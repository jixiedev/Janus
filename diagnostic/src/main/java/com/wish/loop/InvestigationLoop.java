package com.wish.loop;

import com.wish.stage.Stage;
import com.wish.state.State;

import java.util.List;

public class InvestigationLoop {
    private static final State currentState = new State();
    private static final List<Stage> availableStage = List.of();

    private Stage currentStage = null;


    public void loop() {
        while (true) {
            /*
            step 1. ask the llm to choose a stage based on the current state
         */
            this.currentStage = routeStage();

        /*
            proceed in this stage, and update state as well
         */
            handleStage();

            if(this.currentStage.isFinal()) {
                break;
            }
        }


    }

    private Stage routeStage() {
        return null;
    }

    private void handleStage() {

    }
}
