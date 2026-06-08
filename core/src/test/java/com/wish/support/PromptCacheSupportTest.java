package com.wish.support;

import com.wish.support.promptcache.PromptCacheSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PromptCacheSupportTest {

    @Test
    void requestFingerprint_isStableAndNormalized() {
        String first = PromptCacheSupport.requestFingerprint("  plan task  ");
        String second = PromptCacheSupport.requestFingerprint("plan task");
        assertEquals(first, second);
        assertEquals(16, first.length());
    }

    @Test
    void planIdFromRequest_scopesBySession() {
        String ephemeral = PromptCacheSupport.planIdFromRequest(null, "build api");
        String sessionA = PromptCacheSupport.planIdFromRequest("demo", "build api");
        String sessionB = PromptCacheSupport.planIdFromRequest("other", "build api");

        assertEquals("plan:" + PromptCacheSupport.requestFingerprint("build api"), ephemeral);
        assertEquals("plan:demo:" + PromptCacheSupport.requestFingerprint("build api"), sessionA);
        assertNotEquals(sessionA, sessionB);
    }
}
