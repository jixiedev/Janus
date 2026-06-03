package com.wish.service;

import com.wish.loop.DiagnosticAgent;
import com.wish.loop.StageToolCallAgent;
import com.wish.model.DiagnosticContext;
import com.wish.models.context.DiagnosticRunContext;
import com.wish.models.report.IncidentReportArchiver;
import com.wish.models.session.DiagnosticSession;
import com.wish.support.AgentRunSupport;
import com.wish.support.Utils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import com.wish.models.report.IncidentReportPaths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DiagnosticService {

    private static final String MCP_TOOL_CALLBACK_PROVIDER = "mcpToolCallbacks";

    private final ApplicationContext applicationContext;
    private final boolean verboseThought;
    private final Map<String, ChatModel> chatModels = new LinkedHashMap<>();
    private final List<Object> mcpTools;
    private final ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
    private final Map<String, DiagnosticAgent> agents = new ConcurrentHashMap<>();
    private final Map<String, DiagnosticSession> sessions = new ConcurrentHashMap<>();
    private final String reportsDir;

    public DiagnosticService(
            ApplicationContext applicationContext,
            @Value("${janus.shell.verbose-thought:true}") boolean verboseThought,
            @Value("${janus.diagnostic.reports-dir:${user.home}/.janus/diagnostic-reports}") String reportsDir) {
        this.applicationContext = applicationContext;
        this.verboseThought = verboseThought;
        this.reportsDir = reportsDir;
        this.mcpTools = resolveMcpTools(applicationContext);
        StageToolCallAgent.setThoughtLoggingEnabled(verboseThought);
    }

    @PostConstruct
    private void initializeAgents() {
        System.setProperty(IncidentReportPaths.PROPERTY, reportsDir);
        log.info("Diagnostic incident reports directory: {}", IncidentReportPaths.resolveRoot());
        chatModels.putAll(resolveChatModels(applicationContext));
        for (Map.Entry<String, ChatModel> entry : chatModels.entrySet()) {
            agents.put(entry.getKey(), new DiagnosticAgent(entry.getValue(), mcpTools, toolCallingManager));
        }
        log.info("Diagnostic agent registered models: {}", chatModels.keySet());
        log.info("Diagnostic MCP catalog: {} tool(s); per-stage tools defined in Stage enum", mcpTools.size());
        if (mcpTools.isEmpty()) {
            log.warn("No MCP tools registered; stages only use builtins from Stage enum (e.g. query_warehouse)");
        }
        chatModels.forEach((alias, model) -> {
            if (model instanceof OpenAiChatModel openAi) {
                log.info("Diagnostic model alias '{}' -> '{}'", alias, openAi.getOptions().getModel());
            }
        });
    }

    public String run(String prompt, String model) {
        return run(prompt, model, null);
    }

    /**
     * @param sessionId optional conversation id ({@code -c}); merges investigation summaries across requests
     */
    public String run(String prompt, String model, String sessionId) {
        String modelKey = Utils.normalizeModelAlias(model);
        DiagnosticAgent agent = agents.get(modelKey);
        if (agent == null) {
            String available = chatModels.isEmpty() ? "(none)" : String.join(", ", chatModels.keySet());
            throw new IllegalArgumentException(
                    "Unknown model '%s'. Available: %s".formatted(model, available));
        }

        String normalizedSessionId = Utils.trimToNull(sessionId);
        if (normalizedSessionId == null) {
            return runEphemeral(agent, prompt);
        }
        return runPersistent(agent, prompt, modelKey, normalizedSessionId);
    }

    private String runEphemeral(DiagnosticAgent agent, String prompt) {
        DiagnosticContext context = DiagnosticContext.createEphemeral(prompt);
        DiagnosticRunContext runContext =
                new DiagnosticRunContext("diagnostic-ephemeral:" + context.getContextId(), null, chatMemory);
        runContext.setDiagnosticContext(context);
        IncidentReportArchiver archiver = new IncidentReportArchiver(resolveArchiveChatModel());
        try {
            String result = AgentRunSupport.runWithTokenLogging(runContext, () -> agent.process(context));
            context.setRunResult(result);
            archiver.archiveRun(context, result);
            return result;
        } finally {
            context.cleanupRun();
        }
    }

    private String runPersistent(
            DiagnosticAgent agent, String prompt, String modelKey, String sessionId) {
        String cacheKey = sessionKey(modelKey, sessionId);
        ChatModel chatModel = chatModels.get(modelKey);
        DiagnosticSession session =
                sessions.computeIfAbsent(
                        cacheKey,
                        key -> {
                            log.info("New diagnostic session '{}' (model={})", sessionId, modelKey);
                            return new DiagnosticSession(sessionId, chatMemory, chatModel);
                        });
        DiagnosticRunContext runContext = session.beginPrompt(prompt);
        try {
            String result =
                    AgentRunSupport.runWithTokenLogging(
                            runContext, () -> agent.process(runContext.getDiagnosticContext()));
            runContext.setRunResult(result);
            session.endPrompt(result);
            return "conversation-id: %s%n%n%s".formatted(sessionId, result);
        } catch (RuntimeException e) {
            session.abortPrompt();
            throw e;
        }
    }

    public void clearSession(String sessionId, String model) {
        String modelKey = Utils.normalizeModelAlias(model);
        String normalized = Utils.trimToNull(sessionId);
        if (normalized == null) {
            throw new IllegalArgumentException("conversation-id is required");
        }
        String cacheKey = sessionKey(modelKey, normalized);
        DiagnosticSession removed = sessions.remove(cacheKey);
        if (removed != null) {
            removed.archiveSessionClosed();
            removed.clear();
            log.info("Cleared diagnostic session '{}' (model={})", normalized, modelKey);
        } else {
            chatMemory.clear(DiagnosticSession.SESSION_KEY_PREFIX + normalized);
            log.info("No cached diagnostic session for '{}' (model={})", normalized, modelKey);
        }
    }

    public Set<String> allModels() {
        return Collections.unmodifiableSet(chatModels.keySet());
    }

    private static String sessionKey(String modelKey, String sessionId) {
        return modelKey + ":" + sessionId;
    }

    private ChatModel resolveArchiveChatModel() {
        return chatModels.isEmpty() ? null : chatModels.values().iterator().next();
    }

    private static Map<String, ChatModel> resolveChatModels(ApplicationContext context) {
        Map<String, ChatModel> models = new LinkedHashMap<>();
        context.getBeansOfType(ChatModel.class).forEach((beanName, chatModel) -> {
            String alias = aliasForBeanName(beanName);
            if (alias != null) {
                models.putIfAbsent(alias, chatModel);
            }
        });
        return Map.copyOf(models);
    }

    private static String aliasForBeanName(String beanName) {
        String lower = beanName.toLowerCase(Locale.ROOT);
        if (lower.contains("sensenova") || lower.contains("openai")) {
            return "sensenova";
        }
        return null;
    }

    private static List<Object> resolveMcpTools(ApplicationContext context) {
        if (!context.containsBean(MCP_TOOL_CALLBACK_PROVIDER)) {
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
}
