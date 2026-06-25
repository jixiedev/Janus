package com.wish.service;

import com.wish.agent.base.ToolCallAgent;
import com.wish.models.context.ToolCallUserContext;
import com.wish.models.session.ToolCallSession;
import com.wish.llm.LLMChatClient;
import com.wish.support.AgentRunSupport;
import com.wish.support.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
@Service
public class ToolCallService {

    private static final String MCP_TOOL_CALLBACK_PROVIDER = "mcpToolCallbacks";

    protected final Map<String, ChatModel> chatModels;
    protected final List<Object> mcpTools;
    protected final List<Object> skillTools;
    protected final int maxSteps;
    protected final ChatMemory chatMemory;
    protected final Map<String, LLMChatClient> chatClients = new ConcurrentHashMap<>();
    protected final Map<String, ToolCallAgent> agents = new ConcurrentHashMap<>();
    private final Map<String, ToolCallSession> sessions = new ConcurrentHashMap<>();
    protected final ApplicationContext applicationContext;
    protected final boolean verboseThought;

    public ToolCallService(
            ApplicationContext applicationContext,
            @Value("${janus.agent.tool-call.max-steps:10}") int maxSteps,
            @Value("${janus.agent.default.skills.dir:.agent/default/skills}") String defaultSkillsDir,
            @Value("${janus.agent.tool-call.skills.dir:.agent/tool-call/skills}") String agentSkillsDir,
            @Value("${janus.shell.verbose-thought:true}") boolean verboseThought) {
        this.applicationContext = applicationContext;
        this.chatModels = resolveChatModels(applicationContext);
        this.mcpTools = resolveMcpTools(applicationContext);
        this.maxSteps = maxSteps;
        this.verboseThought = verboseThought;
        this.chatMemory = MessageWindowChatMemory.builder().build();
        this.skillTools = resolveSkillTools(List.of(defaultSkillsDir, agentSkillsDir));
        ToolCallAgent.setThoughtLoggingEnabled(verboseThought);
    }

    protected List<Object> resolveSkillTools(List<String> skillDirs) {
        List<Object> tools = new ArrayList<>();
        List<String> effectiveSkillDirs = (skillDirs == null) ? List.of() : skillDirs.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(dir -> !dir.isEmpty())
                .toList();
        boolean hasAnySkill = effectiveSkillDirs.stream().anyMatch(ToolCallService::containsSkillFile);
        if (!hasAnySkill) {
            log.warn("No SKILL.md found under {}; skill tools disabled", effectiveSkillDirs);
            return List.of();
        }
        SkillsTool.Builder builder = SkillsTool.builder();
        for (String dir : effectiveSkillDirs) {
            builder.addSkillsDirectory(dir);
        }
        tools.add(builder.build());
        tools.add(FileSystemTools.builder().build());
        tools.add(ShellTools.builder().build());
        return List.copyOf(tools);
    }

    @PostConstruct
    private void initializeAgents() {
        for (Map.Entry<String, ChatModel> entry : chatModels.entrySet()) {
            registerModel(entry.getKey(), entry.getValue());
        }
        logStartup();
    }

    protected void registerModel(String modelKey, ChatModel chatModel) {
        Supplier<LLMChatClient> clientSupplier = chatClientSupplier(chatModel);
        ToolCallAgent agent = createAgent(clientSupplier);
        chatClients.put(modelKey, agent.getChatClient());
        agents.put(modelKey, agent);
    }

    protected Supplier<LLMChatClient> chatClientSupplier(ChatModel chatModel) {
        return () -> createChatClient(chatModel);
    }

    protected LLMChatClient createChatClient(ChatModel chatModel) {
        return new LLMChatClient(chatModel, Collections.emptyList());
    }

    protected ToolCallAgent createAgent(Supplier<LLMChatClient> chatClientSupplier) {
        return new ToolCallAgent(chatClientSupplier, maxSteps, mcpTools, skillTools);
    }

    protected ToolCallSession createSession(String sessionId, ChatModel chatModel, ToolCallAgent agent) {
        return new ToolCallSession(sessionId, chatMemory, chatModel, agent.sessionSummarySystemPrompt(), agent.directSessionResultTool());
    }

    protected void logStartup() {
        log.info("Registered chat models: {}", chatModels.keySet());
        log.info("Thought logging enabled: {}", verboseThought);
        chatModels.forEach((alias, model) -> {
            if (model instanceof OpenAiChatModel openAi) {
                log.info("OpenAI chat model alias '{}' -> api model '{}'", alias, openAi.getOptions().getModel());
            }
        });
        if (applicationContext.containsBean(MCP_TOOL_CALLBACK_PROVIDER)) {
            if (!mcpTools.isEmpty()) {
                log.info("Loaded {} MCP tool(s)", mcpTools.size());
            } else {
                log.warn(
                        "MCP client is on classpath but no tools were registered; "
                                + "check child processes (uv/uvx) and secrets in application-local.properties");
            }
        } else {
            log.info("MCP tools disabled (no {} bean)", MCP_TOOL_CALLBACK_PROVIDER);
        }
    }

    public String run(String prompt, String model) {
        return run(prompt, model, null);
    }

    /**
     * @param sessionId optional; when set, reuses in-process {@link ToolCallSession} for the same id and model.
     *                  When null/blank, uses a one-off context (not merged into session memory).
     */
    public String run(String prompt, String model, String sessionId) {
        String modelKey = Utils.normalizeModelAlias(model);
        ToolCallAgent agent = agents.get(modelKey);
        if (agent == null) {
            String available = chatModels.isEmpty()
                    ? "(none — check API keys and spring-ai model starters)"
                    : String.join(", ", chatModels.keySet());
            throw new IllegalArgumentException(
                    "Unknown model '%s'. Available: %s".formatted(model, available));
        }

        String normalizedSessionId = Utils.trimToNull(sessionId);
        if (normalizedSessionId == null) {
            return runEphemeral(agent, prompt);
        }
        return runPersistent(agent, prompt, modelKey, normalizedSessionId);
    }

    private String runPersistent(ToolCallAgent agent, String prompt, String modelKey, String sessionId) {
        String cacheKey = sessionKey(modelKey, sessionId);
        ToolCallSession session = sessions.computeIfAbsent(
                cacheKey,
                key -> {
                    log.info("New session '{}' (model={})", sessionId, modelKey);
                    return createSession(sessionId, agent.getChatClient().getChatModel(), agent);
                });
        ToolCallUserContext context = session.beginPrompt(prompt);
        try {
            AgentRunSupport.runWithTokenLogging(context, () -> agent.run(context, prompt));
            session.endPrompt(context.getRunResult());
            return "conversation-id: %s".formatted(sessionId);
        } catch (RuntimeException e) {
            session.abortPrompt();
            throw e;
        }
    }

    private String runEphemeral(ToolCallAgent agent, String prompt) {
        String contextId = "ephemeral:" + UUID.randomUUID();
        ToolCallUserContext context = new ToolCallUserContext(contextId, chatMemory);
        try {
            AgentRunSupport.runWithTokenLogging(context, () -> agent.run(context, prompt));
            return "";
        } finally {
            // Optimization: no -c means one-off request; skip session extraction and purge state eagerly.
            chatMemory.clear(contextId);
            clearConversationScopedTools(agent, contextId);
        }
    }

    public void clearSession(String sessionId, String model) {
        String modelKey = Utils.normalizeModelAlias(model);
        String normalized = Utils.trimToNull(sessionId);
        if (normalized == null) {
            throw new IllegalArgumentException("conversation-id is required");
        }
        String key = sessionKey(modelKey, normalized);
        ToolCallSession removed = sessions.remove(key);
        ToolCallAgent agent = agents.get(modelKey);
        clearConversationScopedTools(agent, normalized);
        if (removed == null) {
            chatMemory.clear(ToolCallSession.SESSION_KEY_PREFIX + normalized);
            log.info("No session to clear for conversation '{}' (model={})", normalized, modelKey);
        } else {
            removed.clear();
            log.info("Cleared session '{}' (model={})", normalized, modelKey);
        }
    }

    protected static String sessionKey(String modelKey, String sessionId) {
        return modelKey + ":" + sessionId;
    }

    protected static Map<String, ChatModel> resolveChatModels(ApplicationContext context) {
        Map<String, ChatModel> models = new LinkedHashMap<>();
        context.getBeansOfType(ChatModel.class).forEach((beanName, chatModel) -> {
            String alias = aliasForBeanName(beanName);
            if (alias != null) {
                models.putIfAbsent(alias, chatModel);
            }
        });
        return Map.copyOf(models);
    }

    public Set<String> allModels() {
        return chatModels.keySet();
    }

    protected static String aliasForBeanName(String beanName) {
        String lower = beanName.toLowerCase(Locale.ROOT);
        if (lower.contains("sensenova") || lower.contains("openai")) {
            return "sensenova";
        }
        return null;
    }

    protected static List<Object> resolveMcpTools(ApplicationContext context) {
        if (!context.containsBean(MCP_TOOL_CALLBACK_PROVIDER)) {
            log.debug("Bean '{}' not found; MCP tools disabled", MCP_TOOL_CALLBACK_PROVIDER);
            return List.of();
        }
        ToolCallbackProvider provider = context.getBean(MCP_TOOL_CALLBACK_PROVIDER, ToolCallbackProvider.class);
        ToolCallback[] callbacks = provider.getToolCallbacks();
        if (callbacks == null || callbacks.length == 0) {
            return List.of();
        }
        List<Object> tools = new ArrayList<>(callbacks.length);
        Collections.addAll(tools, callbacks);
        return List.copyOf(tools);
    }

    private static boolean containsSkillFile(String root) {
        if (root == null || root.isBlank()) {
            return false;
        }
        java.nio.file.Path path = java.nio.file.Path.of(root).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            return false;
        }
        try (Stream<java.nio.file.Path> stream = Files.walk(path, 4)) {
            return stream.anyMatch(skillPath ->
                    Files.isRegularFile(skillPath) && "SKILL.md".equals(skillPath.getFileName().toString()));
        } catch (IOException e) {
            log.warn("Failed to scan skills directory '{}': {}", path, e.getMessage());
            return false;
        }
    }

    protected static void clearConversationScopedTools(ToolCallAgent agent, String scopeId) {
        if (agent == null) {
            return;
        }
        agent.clearConversationScopedTools(scopeId);
    }
}


