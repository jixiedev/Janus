package com.wish.state;

import com.wish.stage.Stage;
import org.apache.commons.lang3.tuple.Pair;

import java.util.LinkedList;

public class State {
    public static final String rawRequest = "";
    public String currentHypothesis = "";

    public LinkedList<Pair<Stage, String>> stageHypothesis = new LinkedList<>();

    public void addFinding(Stage stage, String hypothesis) {
        stageHypothesis.add(Pair.of(stage, hypothesis));
    }
}
