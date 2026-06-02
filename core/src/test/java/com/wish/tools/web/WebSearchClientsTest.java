package com.wish.tools.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class WebSearchClientsTest {

    @Test
    void create_returnsNullWhenDisabled() {
        assertNull(create(new WebSearchSettings(false, WebSearchSettings.Provider.DUCKDUCKGO, 5, "", "")));
    }

    @Test
    void resolveProvider_usesTavilyWhenConfiguredWithKey() {
        var settings = new WebSearchSettings(true, WebSearchSettings.Provider.TAVILY, 5, "", "tvly-test");
        assertEquals(WebSearchSettings.Provider.TAVILY, WebSearchClients.resolveProvider(settings));
        assertInstanceOf(TavilyWebSearchClient.class, create(settings));
    }

    @Test
    void resolveProvider_fallsBackToDuckDuckGoWhenTavilyKeyMissing() {
        var settings = new WebSearchSettings(true, WebSearchSettings.Provider.TAVILY, 5, "", "");
        assertEquals(WebSearchSettings.Provider.DUCKDUCKGO, WebSearchClients.resolveProvider(settings));
    }

    @Test
    void resolveProvider_usesDuckDuckGoByDefault() {
        var settings = WebSearchSettings.defaults();
        assertEquals(WebSearchSettings.Provider.DUCKDUCKGO, WebSearchClients.resolveProvider(settings));
        assertInstanceOf(DuckDuckGoWebSearchClient.class, create(settings));
    }

    private static WebSearchClient create(WebSearchSettings settings) {
        return WebSearchClients.create(settings);
    }
}
