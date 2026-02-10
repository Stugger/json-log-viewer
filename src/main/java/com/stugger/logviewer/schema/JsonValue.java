package com.stugger.logviewer.schema;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Jake
 * @since February 8, 2026
 */
public final class JsonValue {
    private JsonValue() {}

    private static final ConcurrentHashMap<String, PathToken> CACHE = new ConcurrentHashMap<>();

    public static JsonElement resolve(JsonObject root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return null;
        }
        PathToken token = CACHE.computeIfAbsent(path.trim(), PathToken::parse);
        return token.resolve(root);
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
