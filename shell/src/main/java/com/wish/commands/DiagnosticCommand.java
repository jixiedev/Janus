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
                            longName = "session",
                            shortName = 's',
                            description = "Reuse investigation session across requests (same shell process)")
                    String sessionId,
            @Option(
                            longName = "reports",
                            description =
                                    "Persist incident report for this request (true/false; overrides janus.diagnostic.reports-enabled)")
                    Boolean reports) {
        return diagnosticService.run(prompt, model, sessionId, reports);
    }

    @Command(name = "clear-session", description = "Drop cached diagnostic session")
    public void clearSession(
            @Option(longName = "session", shortName = 's', description = "Session id", required = true)
                    String sessionId,
            @Option(longName = "model", shortName = 'm', description = "Model: sensenova", defaultValue = "sensenova")
                    String model) {
        diagnosticService.clearSession(sessionId, model);
    }

    @Command(name = "list-models", description = "List models available for diagnostic")
    public Set<String> listModels() {
        return diagnosticService.allModels();
    }
}
