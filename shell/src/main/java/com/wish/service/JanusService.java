package com.wish.service;

import com.wish.agent.JanusAgent;
import com.wish.agent.base.ToolCallAgent;
import com.wish.llm.LLMChatClient;
import com.wish.tools.web.WebSearchSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Supplier;

@Slf4j
@Service
public class JanusService extends ToolCallService {

    private final Path workspaceRoot;
    private final WebSearchSettings webSearchSettings;

    public JanusService(
            ApplicationContext applicationContext,
            @Value("${janus.agent.janus.max-steps:10}") int maxSteps,
            @Value("${janus.agent.janus.workspace-root:workspace/janus}") String workspaceRoot,
            @Value("${janus.agent.default.skills.dir:.agent/default/skills}") String defaultSkillsDir,
            @Value("${janus.agent.janus.skills.dir:.agent/janus/skills}") String agentSkillsDir,
            @Value("${janus.web-search.enabled:true}") boolean webSearchEnabled,
            @Value("${janus.web-search.provider:duckduckgo}") String webSearchProvider,
            @Value("${janus.web-search.max-results:5}") int webSearchMaxResults,
            @Value("${janus.web-search.brave-api-key:}") String braveApiKey,
            @Value("${janus.web-search.tavily-api-key:}") String tavilyApiKey) {
        super(applicationContext, maxSteps, defaultSkillsDir, agentSkillsDir);
        this.workspaceRoot = WorkspaceSupport.resolveWorkspaceRoot(workspaceRoot);
        this.webSearchSettings = new WebSearchSettings(
                webSearchEnabled,
                parseProvider(webSearchProvider),
                webSearchMaxResults,
                braveApiKey,
                tavilyApiKey);
    }

    @Override
    protected void logStartup() {
        super.logStartup();
        log.info("Janus workspace root: {}", workspaceRoot);
        log.info(
                "Janus agent ready (web search enabled={}, provider={}, maxResults={})",
                webSearchSettings.enabled(),
                webSearchSettings.provider(),
                webSearchSettings.maxResults());
    }

    @Override
    protected ToolCallAgent createAgent(Supplier<LLMChatClient> chatClientSupplier) {
        return new JanusAgent(chatClientSupplier, maxSteps, workspaceRoot, mcpTools, skillTools, webSearchSettings);
    }

    private static WebSearchSettings.Provider parseProvider(String value) {
        if (value == null || value.isBlank()) {
            return WebSearchSettings.Provider.DUCKDUCKGO;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "brave" -> WebSearchSettings.Provider.BRAVE;
            case "tavily" -> WebSearchSettings.Provider.TAVILY;
            default -> WebSearchSettings.Provider.DUCKDUCKGO;
        };
    }

}
