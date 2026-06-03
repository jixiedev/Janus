package com.wish.tools;

import com.wish.model.stage.Stage;
import com.wish.model.state.State;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/*
this tool class can't be used globally
 */
public class StageRouter {
    private final State state;

    public StageRouter(State state) {
        this.state = state;
    }

    @Tool(
            name = "router",
            description =
                    "Choose the next investigation stage. "
                            + "Values: CLASSIFY, COLLECT, ANALYZE, VALIDATE, TERMINATE, ERROR. "
                            + "Call exactly once per routing turn.")
    public String route(@ToolParam(description = "Stage name (e.g. COLLECT, TERMINATE)", required = true) String stageName) {
        Stage stage = Stage.require(stageName);
        state.enterIntoStage(stage);
        return "Entered stage: " + stage.getName();
    }
}
