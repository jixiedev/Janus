package com.wish.model.stage;

import lombok.Getter;

import java.util.List;

/**
 * state -> (Choose the next stage) ->
 */
@Getter
public enum Stage {
    INITIAL("Initial", List.of(), 1,false),
    COLLECT("Collect", List.of(), 30,false),
    ANALYZE("Analyze", List.of(), 30, false),
    VALIDATE("Validate", List.of(), 30,false),
    TERMINATE("Terminate", List.of(), 3,true),
    ERROR("Error", List.of(), 3,true);
    final String name;
    final List<Object> availableTools;

    final int maxSteps;

    final boolean isFinal;

    Stage(String name, List<Object> availableTools, int maxSteps, boolean isFinal) {
        this.name = name;
        this.availableTools = availableTools;
        this.maxSteps = maxSteps;
        this.isFinal = isFinal;
    }

    public static Stage of(String name) {
        for(Stage stage: Stage.values()) {
            if(stage.name.equalsIgnoreCase(name)) {
                return stage;
            }
        }
        //todo handle this situation
        return null;
    }

    public boolean shouldContinue() {
        return !isFinal;
    }


}
