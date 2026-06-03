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
            description = "Choose a stage to proceed. Available stages are COLLECT, ANALYZE, VALIDATE")
    public void route(
            @ToolParam(description = "stage name", required = true) String stageName) {
        Stage stage = Stage.of(stageName);
        state.enterIntoStage(stage);
    }
}
