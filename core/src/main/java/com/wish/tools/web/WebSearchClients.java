package com.wish.tools.web;

import com.wish.support.Utils;

public final class WebSearchClients {

    private WebSearchClients() {}

    public static WebSearchClient create(WebSearchSettings settings) {
        if (settings == null || !settings.enabled()) {
            return null;
        }
        return switch (resolveProvider(settings)) {
            case TAVILY -> new TavilyWebSearchClient(settings.tavilyApiKey());
            case BRAVE -> new BraveWebSearchClient(settings.braveApiKey());
            case DUCKDUCKGO -> new DuckDuckGoWebSearchClient();
        };
    }

    static WebSearchSettings.Provider resolveProvider(WebSearchSettings settings) {
        return switch (settings.provider()) {
            case TAVILY -> hasKey(settings.tavilyApiKey())
                    ? WebSearchSettings.Provider.TAVILY
                    : WebSearchSettings.Provider.DUCKDUCKGO;
            case BRAVE -> hasKey(settings.braveApiKey())
                    ? WebSearchSettings.Provider.BRAVE
                    : WebSearchSettings.Provider.DUCKDUCKGO;
            case DUCKDUCKGO -> WebSearchSettings.Provider.DUCKDUCKGO;
        };
    }

    private static boolean hasKey(String key) {
        return Utils.trimToNull(key) != null;
    }
}
