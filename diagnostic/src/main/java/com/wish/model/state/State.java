package com.wish.model.state;

import com.wish.model.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;

import java.util.LinkedList;

/*
state is passed to llm to decide stage and tools
 */

public class State {
    public Stage currentStage = Stage.INITIAL;
    @Setter
    @Getter
    public String currentStageThought = "";

    @Getter
    private int stageCnt = 0;

    public LinkedList<Pair<Stage, String>> stageFindings = new LinkedList<>();

    public void addFinding(Stage stage, String findings) {
        stageFindings.add(Pair.of(stage, findings));
    }

    public void enterIntoStage(Stage stage) {
        this.currentStage = stage;
        this.stageCnt += 1;
    }

    public String desc() {
        StringBuilder description = new StringBuilder("Below is the current progress of this incident:");
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
