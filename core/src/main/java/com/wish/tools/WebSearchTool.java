package com.wish.tools;

import com.wish.tools.web.WebSearchClient;
import com.wish.tools.web.WebSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/** Search the public web for current facts (news, company info, documentation). */
@Slf4j
public class WebSearchTool {

    private final WebSearchClient client;
    private final int maxResults;

    public WebSearchTool(WebSearchClient client) {
        this(client, 5);
    }

    public WebSearchTool(WebSearchClient client, int maxResults) {
        if (client == null) {
            throw new IllegalArgumentException("WebSearchClient is required");
        }
        this.client = client;
        this.maxResults = Math.max(1, Math.min(maxResults, 10));
    }

    @Tool(
            name = "web_search",
            description = "Search the public web for up-to-date information. Use for facts, news, company "
                    + "background, product docs, and other non-local knowledge. Returns titles, URLs, and snippets. "
                    + "Not for searching DataHub/metadata catalogs—use MCP search tools for internal data assets.")
    public String search(
            @ToolParam(description = "Search query in the user's language.", required = true) String query,
            @ToolParam(description = "Maximum number of results (1-10).", required = false) Integer numResults) {
        int limit = numResults == null ? maxResults : Math.max(1, Math.min(numResults, 10));
        try {
            List<WebSearchResult> hits = client.search(query, limit);
            if (hits.isEmpty()) {
                return formatResult("No web results found for: " + query, false);
            }
            return formatResult(formatHits(hits), true);
        } catch (Exception e) {
            log.warn("web_search failed: {}", e.getMessage());
            return formatResult("Web search failed: " + e.getMessage(), false);
        }
    }

    private static String formatHits(List<WebSearchResult> hits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            WebSearchResult hit = hits.get(i);
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append(i + 1).append(". ").append(nullToEmpty(hit.title()));
            if (hit.url() != null && !hit.url().isBlank()) {
                sb.append("\n   URL: ").append(hit.url());
            }
            if (hit.snippet() != null && !hit.snippet().isBlank()) {
                sb.append("\n   ").append(hit.snippet());
            }
        }
        return sb.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String formatResult(String observation, boolean success) {
        return "{observation=%s, success=%s}".formatted(observation, success);
    }
}
