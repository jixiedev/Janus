package com.wish.support.promptcache;

import com.wish.support.Utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Helpers for stable prompt prefixes that improve LLM prompt-cache reuse. */
public final class PromptCacheSupport {

    private static final int REQUEST_HASH_HEX_CHARS = 16;

    private PromptCacheSupport() {}

    /**
     * Deterministic fingerprint of a user request for plan ids and other stable keys.
     * Optimization: identical {@code -p} text within a session yields the same plan id so
     * planning prompts can share a prefix across runs (instead of a per-run UUID suffix).
     */
    public static String requestFingerprint(String request) {
        String normalized = request == null ? "" : request.trim();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, Math.min(REQUEST_HASH_HEX_CHARS, hex.length()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Content-derived plan id scoped by optional session.
     * Optimization: decouples plan id from ephemeral context UUID so repeated tasks hit cache.
     */
    public static String planIdFromRequest(String sessionId, String request) {
        String fingerprint = requestFingerprint(request);
        String normalizedSession = Utils.trimToNull(sessionId);
        if (normalizedSession == null) {
            return "plan:" + fingerprint;
        }
        return "plan:" + normalizedSession + ":" + fingerprint;
    }
}
