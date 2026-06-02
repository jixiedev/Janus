package com.wish.tools.web;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HTML-based DuckDuckGo search (no API key). */
public class DuckDuckGoWebSearchClient implements WebSearchClient {

    private static final URI SEARCH_URI = URI.create("https://html.duckduckgo.com/html/");
    private static final Pattern RESULT_LINK =
            Pattern.compile("class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern RESULT_SNIPPET =
            Pattern.compile("class=\"result__snippet\"[^>]*>(.*?)</(?:a|td|div)>", Pattern.DOTALL);

    private final HttpClient httpClient;

    public DuckDuckGoWebSearchClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    DuckDuckGoWebSearchClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public List<WebSearchResult> search(String query, int maxResults) throws Exception {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int limit = Math.max(1, Math.min(maxResults, 10));
        String body = "q=" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8) + "&b=";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(SEARCH_URI)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "Janus/1.0 (+https://github.com/wish/janus)")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("DuckDuckGo search HTTP " + response.statusCode());
        }
        return parseHtml(response.body(), limit);
    }

    static List<WebSearchResult> parseHtml(String html, int maxResults) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        Matcher linkMatcher = RESULT_LINK.matcher(html);
        Matcher snippetMatcher = RESULT_SNIPPET.matcher(html);
        List<String> titles = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        while (linkMatcher.find() && titles.size() < maxResults) {
            urls.add(resolveUrl(linkMatcher.group(1)));
            titles.add(cleanText(linkMatcher.group(2)));
        }
        List<String> snippets = new ArrayList<>();
        while (snippetMatcher.find() && snippets.size() < maxResults) {
            snippets.add(cleanText(snippetMatcher.group(1)));
        }
        List<WebSearchResult> results = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            String snippet = i < snippets.size() ? snippets.get(i) : "";
            results.add(new WebSearchResult(titles.get(i), urls.get(i), snippet));
        }
        return List.copyOf(results);
    }

    private static String resolveUrl(String href) {
        if (href == null) {
            return "";
        }
        String decoded = href.replace("&amp;", "&");
        if (decoded.startsWith("//")) {
            decoded = "https:" + decoded;
        }
        try {
            URI uri = URI.create(decoded);
            String query = uri.getRawQuery();
            if (query != null && query.contains("uddg=")) {
                for (String param : query.split("&")) {
                    if (param.startsWith("uddg=")) {
                        return URLDecoder.decode(param.substring("uddg=".length()), StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return decoded;
    }

    private static String cleanText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("<[^>]+>", "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
