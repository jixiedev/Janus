package com.wish.tools.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuckDuckGoWebSearchClientTest {

    @Test
    void parseHtml_extractsTitleUrlAndSnippet() {
        String html =
                """
                <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com&amp;rut=1">Example Title</a>
                <a class="result__snippet">Short description here.</a>
                """;

        var results = DuckDuckGoWebSearchClient.parseHtml(html, 5);

        assertEquals(1, results.size());
        assertEquals("Example Title", results.get(0).title());
        assertEquals("https://example.com", results.get(0).url());
        assertTrue(results.get(0).snippet().contains("description"));
    }
}
