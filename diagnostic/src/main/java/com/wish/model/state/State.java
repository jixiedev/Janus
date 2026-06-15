package com.wish.model.state;

import com.wish.model.incident.IncidentCategory;
import com.wish.model.incident.IncidentRootCause;
import com.wish.model.incident.RootCauseConfidence;
import com.wish.model.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.util.LinkedList;
import java.util.Optional;

/*
state is passed to llm to decide stage and tools
 */

public class State {
    @Getter
    @Setter
    private Stage currentStage = Stage.INITIAL;
    @Setter
    @Getter
    private String currentStageThought = "";

    @Getter
    private int stageCnt = 0;

    @Getter
    @Setter
    private IncidentCategory incidentCategory;

    @Getter
    @Setter
    private String incidentCategoryRationale = "";

    /** When the incident last occurred; provisional from CLASSIFY, refined via record_incident_last_occurred. */
    @Getter
    @Setter
    private Instant incidentOccurredAt;

    @Getter
    @Setter
    private IncidentRootCause rootCause;

    @Getter
    @Setter
    private String rootCauseRationale = "";

    @Getter
    @Setter
    private RootCauseConfidence rootCauseConfidence;

    private final LinkedList<Pair<Stage, String>> stageFindings = new LinkedList<>();

    public Optional<IncidentCategory> getIncidentCategoryOptional() {
        return Optional.ofNullable(incidentCategory);
    }

    public void setIncidentClassification(IncidentCategory category, String rationale) {
        setIncidentClassification(category, rationale, incidentOccurredAt);
    }

    public void setIncidentClassification(IncidentCategory category, String rationale, Instant occurredAt) {
        this.incidentCategory = category;
        this.incidentCategoryRationale = rationale == null ? "" : rationale.trim();
        this.incidentOccurredAt = occurredAt;
    }

    public Optional<IncidentRootCause> getRootCauseOptional() {
        return Optional.ofNullable(rootCause);
    }

    public void setRootCauseClassification(
            IncidentRootCause cause, String rationale, RootCauseConfidence confidence) {
        this.rootCause = cause;
        this.rootCauseRationale = rationale == null ? "" : rationale.trim();
        this.rootCauseConfidence = confidence == null ? RootCauseConfidence.PROBABLE : confidence;
    }

    public void addFinding(Stage stage, String findings) {
        stageFindings.add(Pair.of(stage, findings));
    }

    public void enterIntoStage(Stage stage) {
        if (stage == null) {
            throw new IllegalArgumentException("stage must not be null");
        }
        this.currentStage = stage;
        this.stageCnt += 1;
    }

    public LinkedList<Pair<Stage, String>> getStageFindings() {
        return stageFindings;
    }

    /** One-line snapshot for logs. */
    public String summaryLine() {
        return "currentStage=%s stageVisits=%d problemType=%s rootCause=%s[%s] stageFindings=%d"
                .formatted(
                        currentStage.name(),
                        stageCnt,
                        incidentCategory == null ? "—" : incidentCategory.name(),
                        rootCause == null ? "—" : rootCause.name(),
                        rootCauseConfidence == null ? "" : rootCauseConfidence.name(),
                        stageFindings.size());
    }

    public String desc() {
        StringBuilder description = new StringBuilder("Below is the current progress of this incident:");
        if (incidentCategory != null) {
            description.append("\n\n\tProblem type: ")
                    .append(incidentCategory.name())
                    .append(" (")
                    .append(incidentCategory.getLabel())
                    .append(")");
            if (incidentCategoryRationale != null && !incidentCategoryRationale.isBlank()) {
                description.append("\n\tProblem summary: ").append(incidentCategoryRationale.trim());
            }
            if (incidentOccurredAt != null) {
                description.append("\n\tIncident last occurred (classified): ").append(incidentOccurredAt);
            }
        }
        if (rootCause != null) {
            description.append("\n\n\tRoot cause: ")
                    .append(rootCause.name())
                    .append(" (")
                    .append(rootCause.getLabel())
                    .append(")");
            if (rootCauseConfidence != null) {
                description.append(" [").append(rootCauseConfidence.name()).append(']');
            }
            if (rootCauseRationale != null && !rootCauseRationale.isBlank()) {
                description.append("\n\tRoot cause rationale: ").append(rootCauseRationale.trim());
            }
        }
        for(Pair<Stage, String> sf: stageFindings) {
            description.append("\n\n");
            String stageDesc = "\tStage: %s, finding: %s";
            Stage stage = sf.getLeft();
            String finding = sf.getRight();
            description.append(String.format(stageDesc, stage.name(), finding));
        }
        return description.toString();
    }
}
