package com.wish.tools;

import com.wish.tools.web.WebSearchClient;
import com.wish.tools.web.WebSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSearchToolTest {

    @Test
    void search_formatsHitsForAgent() {
        WebSearchClient client = (query, max) ->
                List.of(new WebSearchResult("Wish", "https://example.com/wish", "Founded in 2010"));
        var tool = new WebSearchTool(client, 3);

        String result = tool.search("Wish company history", 3);

        assertTrue(result.contains("success=true"));
        assertTrue(result.contains("Wish"));
        assertTrue(result.contains("https://example.com/wish"));
        assertTrue(result.contains("Founded in 2010"));
    }
}
