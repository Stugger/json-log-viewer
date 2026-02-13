package com.stugger.logviewer.schema.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached JSON path resolver for schema rendering.
 * <p>
 * Converts path strings into parsed tokens and resolves them against a {@link JsonObject}
 * with minimal overhead across large record sets.
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
