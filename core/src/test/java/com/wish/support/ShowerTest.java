package com.wish.support;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShowerTest {

    @Test
    void progressEndWithConsumerEmitsSingleLine() {
        List<String> lines = new ArrayList<>();
        Shower.setOutputConsumer(lines::add);
        try {
            Shower.progressStart("end-prompt summarizing");
            Shower.progressEnd("done");
            assertEquals(1, lines.size());
            assertEquals("[session] end-prompt summarizing... done", lines.get(0));
        } finally {
            Shower.setOutputConsumer(null);
        }
    }

    @Test
    void llmUsageShowCompactPrefixesLine() {
        List<String> lines = new ArrayList<>();
        Shower.setOutputConsumer(lines::add);
        try {
            Shower.LLM_USAGE.showCompact("Token this call (prompt/completion/total): 1/2/3");
            assertEquals(1, lines.size());
            assertEquals(true, lines.get(0).contains("[usage] Token this call (prompt/completion/total): 1/2/3"));
        } finally {
            Shower.setOutputConsumer(null);
        }
    }
}
