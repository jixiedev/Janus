package com.wish.tools.web;

import java.util.List;

public interface WebSearchClient {

    List<WebSearchResult> search(String query, int maxResults) throws Exception;
}
