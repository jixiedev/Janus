package com.wish.tools.web;

/** Configuration for {@link com.wish.tools.WebSearchTool}. */
public record WebSearchSettings(
        boolean enabled,
        Provider provider,
        int maxResults,
        String braveApiKey,
        String tavilyApiKey) {

    public enum Provider {
        DUCKDUCKGO,
        BRAVE,
        TAVILY
    }

    public static WebSearchSettings defaults() {
        return new WebSearchSettings(true, Provider.DUCKDUCKGO, 5, "", "");
    }
}
