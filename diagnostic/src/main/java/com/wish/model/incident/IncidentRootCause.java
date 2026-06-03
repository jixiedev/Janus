package com.wish.model.incident;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Root-cause taxonomy updated during investigation (why it happened). Distinct from {@link IncidentCategory}
 * (problem type / what is wrong). Persisted on {@link com.wish.model.state.State}; may be revised as evidence grows.
 */
public enum IncidentRootCause {
    CODE_OR_LOGIC("Code / logic", "Bug or incorrect logic in application, SQL, or transformation code"),
    UPSTREAM_DATA("Upstream data", "Source system delivered bad, incomplete, or late data"),
    MISCONFIGURATION("Misconfiguration", "Wrong config, parameter, connection string, or feature flag"),
    DEPLOYMENT_OR_RELEASE("Deployment / release", "Recent deploy, rollback, migration, or schema change"),
    INFRASTRUCTURE("Infrastructure", "Node, network, storage, or cluster/platform failure"),
    CAPACITY_OR_RESOURCE("Capacity / resource", "OOM, disk full, quota, throttling, or saturation"),
    ORCHESTRATION("Orchestration", "Scheduler, sensor, dependency, or run-window mistake"),
    PERMISSION_OR_CREDENTIAL("Permission / credential", "IAM, secret expiry, or access policy issue"),
    EXTERNAL_VENDOR("External vendor", "Third-party API, SaaS, or partner system outage or change"),
    OPERATOR_ERROR("Operator error", "Manual change, bad rerun, or procedural mistake"),
    NOT_APPLICABLE("Not applicable", "No defect root cause (usage question, false alert, or user error only)"),
    UNKNOWN("Unknown", "Root cause not established; list missing evidence instead of guessing");

    private final String label;
    private final String description;

    IncidentRootCause(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public static IncidentRootCause require(String name) {
        IncidentRootCause cause = of(name);
        if (cause == null) {
            throw new IllegalArgumentException(
                    "Unknown root cause category: "
                            + name
                            + ". Allowed: "
                            + Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", ")));
        }
        return cause;
    }

    public static IncidentRootCause of(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim().replace(' ', '_').replace('-', '_').toUpperCase(Locale.ROOT);
        for (IncidentRootCause cause : values()) {
            if (cause.name().equals(normalized) || cause.label.equalsIgnoreCase(name.trim())) {
                return cause;
            }
        }
        return null;
    }

    public static String catalogForPrompt() {
        return Arrays.stream(values())
                .map(c -> "- " + c.name() + ": " + c.label + " — " + c.description)
                .collect(Collectors.joining("\n"));
    }
}
