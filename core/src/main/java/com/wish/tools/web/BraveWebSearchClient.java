package com.wish.tools.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Brave Search API (https://brave.com/search/api/). */
public class BraveWebSearchClient implements WebSearchClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String apiKey;

    public BraveWebSearchClient(String apiKey) {
        this(apiKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    BraveWebSearchClient(String apiKey, HttpClient httpClient) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Brave API key is required");
        }
        this.apiKey = apiKey.trim();
        this.httpClient = httpClient;
    }

    @Override
    public List<WebSearchResult> search(String query, int maxResults) throws Exception {
        int limit = Math.max(1, Math.min(maxResults, 10));
        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        URI uri = URI.create("https://api.search.brave.com/res/v1/web/search?q=" + encoded + "&count=" + limit);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("X-Subscription-Token", apiKey)
                .GET()
                .build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Brave search HTTP " + response.statusCode() + ": " + response.body());
        }
        return parseJson(response.body());
    }

    static List<WebSearchResult> parseJson(String json) throws Exception {
        JsonNode results = MAPPER.readTree(json).path("web").path("results");
        if (!results.isArray()) {
            return List.of();
        }
        List<WebSearchResult> hits = new ArrayList<>();
        for (JsonNode node : results) {
            String title = node.path("title").asText("");
            String url = node.path("url").asText("");
            String snippet = node.path("description").asText("");
            if (!title.isBlank() || !url.isBlank()) {
                hits.add(new WebSearchResult(title, url, snippet));
            }
        }
        return List.copyOf(hits);
    }
}
