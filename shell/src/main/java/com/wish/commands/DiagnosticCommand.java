package com.wish.commands;

import com.wish.service.DiagnosticService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.CommandGroup;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@CommandGroup(name = "diagnostic agent", prefix = "diagnostic")
@Slf4j
public class DiagnosticCommand {

    private final DiagnosticService diagnosticService;

    public DiagnosticCommand(DiagnosticService diagnosticService) {
        this.diagnosticService = diagnosticService;
    }

    @Command(
            name = "request",
            description = "Run multi-stage incident diagnostic (COLLECT → ANALYZE → VALIDATE → TERMINATE)")
    public String request(
            @Option(longName = "prompt", shortName = 'p', description = "Incident or follow-up question", required = true)
                    String prompt,
            @Option(longName = "model", shortName = 'm', description = "Model: sensenova", defaultValue = "sensenova")
                    String model,
            @Option(
                            longName = "conversation-id",
                            shortName = 'c',
                            description = "Reuse investigation session across requests (same shell process)")
                    String conversationId) {
        return diagnosticService.run(prompt, model, conversationId);
    }

    @Command(name = "clear-session", description = "Drop cached diagnostic session for a conversation id")
    public void clearSession(
            @Option(longName = "conversation-id", shortName = 'c', description = "Session id", required = true)
                    String conversationId,
            @Option(longName = "model", shortName = 'm', description = "Model: sensenova", defaultValue = "sensenova")
                    String model) {
        diagnosticService.clearSession(conversationId, model);
    }

    @Command(name = "list-models", description = "List models available for diagnostic")
    public Set<String> listModels() {
        return diagnosticService.allModels();
    }
}
