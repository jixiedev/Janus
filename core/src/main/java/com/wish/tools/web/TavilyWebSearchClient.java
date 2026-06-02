package com.wish.tools.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Tavily Search API (https://tavily.com). */
public class TavilyWebSearchClient implements WebSearchClient {

    private static final URI SEARCH_URI = URI.create("https://api.tavily.com/search");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String apiKey;

    public TavilyWebSearchClient(String apiKey) {
        this(apiKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    TavilyWebSearchClient(String apiKey, HttpClient httpClient) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Tavily API key is required");
        }
        this.apiKey = apiKey.trim();
        this.httpClient = httpClient;
    }

    @Override
    public List<WebSearchResult> search(String query, int maxResults) throws Exception {
        int limit = Math.max(1, Math.min(maxResults, 10));
        String payload = MAPPER.writeValueAsString(
                Map.of("api_key", apiKey, "query", query.trim(), "max_results", limit, "search_depth", "basic"));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(SEARCH_URI)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Tavily search HTTP " + response.statusCode() + ": " + response.body());
        }
        return parseJson(response.body());
    }

    static List<WebSearchResult> parseJson(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        JsonNode results = root.path("results");
        if (!results.isArray()) {
            return List.of();
        }
        List<WebSearchResult> hits = new ArrayList<>();
        for (JsonNode node : results) {
            String title = node.path("title").asText("");
            String url = node.path("url").asText("");
            String snippet = node.path("content").asText("");
            if (!title.isBlank() || !url.isBlank()) {
                hits.add(new WebSearchResult(title, url, snippet));
            }
        }
        return List.copyOf(hits);
    }
}
