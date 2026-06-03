package com.wish.model.incident;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Problem-type taxonomy for the CLASSIFY stage (what is wrong / what the user reports).
 * Values are persisted on {@link com.wish.model.state.State}.
 *
 * <p>Distinct from {@link IncidentRootCause} (why it happened), which is set via classify_root_cause in later stages.
 */
public enum IncidentCategory {
    DATA_ISSUE("Data issue", "Data is wrong, missing, duplicate, stale, or does not match expected schema or volume"),
    JOB_EXECUTION("Job execution", "Pipeline or task failed, stuck, did not run, or output is empty/unexpected"),
    LATENESS_OR_SLA("Lateness / SLA", "Deliverable, dataset, or job finished later than expected or missed a deadline"),
    PERFORMANCE("Performance", "Too slow or timing out, but still partially or fully functional"),
    UNAVAILABLE("Unavailable", "Cannot reach a service, API, table, cluster endpoint, or job control plane"),
    ACCESS_OR_PERMISSION("Access / permission", "Authentication failure, denied access, or missing grants"),
    REPORTING_OR_UI("Reporting / UI", "Dashboard, report, or visualization is wrong, empty, or misleading"),
    ORCHESTRATION("Orchestration", "Scheduling symptom: DAG/run not triggered, blocked dependency, or wrong run window"),
    DATA_FEED_OR_INTEGRATION("Data feed / integration", "Inbound/outbound feed late, missing, or rejected at the boundary"),
    USAGE_OR_QUESTION("Usage / question", "How-to, expected behavior, or operational question — not a production defect"),
    UNKNOWN("Unknown", "Problem shape unclear; say what facts are missing instead of guessing root cause");

    private static final Map<String, IncidentCategory> LEGACY_ALIASES =
            Map.ofEntries(
                    Map.entry("DATA_QUALITY", DATA_ISSUE),
                    Map.entry("PIPELINE_FAILURE", JOB_EXECUTION),
                    Map.entry("SCHEDULING", ORCHESTRATION),
                    Map.entry("INFRASTRUCTURE", UNAVAILABLE),
                    Map.entry("CAPACITY", PERFORMANCE),
                    Map.entry("CONFIGURATION", USAGE_OR_QUESTION),
                    Map.entry("EXTERNAL_DEPENDENCY", DATA_FEED_OR_INTEGRATION));

    private final String label;
    private final String description;

    IncidentCategory(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public static IncidentCategory require(String name) {
        IncidentCategory category = of(name);
        if (category == null) {
            throw new IllegalArgumentException(
                    "Unknown problem type: "
                            + name
                            + ". Allowed: "
                            + Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", ")));
        }
        return category;
    }

    public static IncidentCategory of(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim().replace(' ', '_').replace('-', '_').toUpperCase(Locale.ROOT);
        IncidentCategory legacy = LEGACY_ALIASES.get(normalized);
        if (legacy != null) {
            return legacy;
        }
        for (IncidentCategory category : values()) {
            if (category.name().equals(normalized) || category.label.equalsIgnoreCase(name.trim())) {
                return category;
            }
        }
        return null;
    }

    /** Plain-text catalog for router and CLASSIFY stage prompts. */
    public static String catalogForPrompt() {
        return Arrays.stream(values())
                .map(c -> "- " + c.name() + ": " + c.label + " — " + c.description)
                .collect(Collectors.joining("\n"));
    }
}
