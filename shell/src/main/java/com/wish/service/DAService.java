package com.wish.service;

import com.wish.agent.DataAnalysisAgent;
import com.wish.agent.base.ToolCallAgent;
import com.wish.llm.LLMChatClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.function.Supplier;

@Slf4j
@Service
public class DAService extends ToolCallService {

    private final Path workspaceRoot;

    public DAService(
            ApplicationContext applicationContext,
            @Value("${janus.agent.da.max-steps:10}") int maxSteps,
            @Value("${janus.agent.da.workspace-root:workspace/da}") String workspaceRoot,
            @Value("${janus.agent.default.skills.dir:.agent/default/skills}") String defaultSkillsDir,
            @Value("${janus.agent.da.skills.dir:.agent/da/skills}") String agentSkillsDir,
            @Value("${janus.shell.verbose-thought:true}") boolean verboseThought) {
        super(applicationContext, maxSteps, defaultSkillsDir, agentSkillsDir, verboseThought);
        this.workspaceRoot = WorkspaceSupport.resolveWorkspaceRoot(workspaceRoot);
    }

    @Override
    protected void logStartup() {
        super.logStartup();
        log.info("Data analysis workspace root: {}", workspaceRoot);
    }

    @Override
    protected ToolCallAgent createAgent(Supplier<LLMChatClient> chatClientSupplier) {
        return new DataAnalysisAgent(chatClientSupplier, maxSteps, workspaceRoot, mcpTools, skillTools);
    }

}
