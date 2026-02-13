package com.stugger.logviewer.schema.model;

/**
 * Rendering strategy for complex JSON values in schema details.
 * <p>
 * Controls how objects and arrays are displayed in the Details panel:
 * <ul>
 *   <li>{@code COMPACT} – YAML-style multi-line formatting (default)</li>
 *   <li>{@code INLINE_COMPACT} – YAML-style single-line entries</li>
 *   <li>{@code JSON} – pretty-printed JSON</li>
 *   <li>{@code INLINE_JSON} – raw compact JSON</li>
 * </ul>
 * <p>
 * Used when resolving schema field definitions that specify a render mode.
 * Defaults to {@code COMPACT} when unspecified or invalid.
 *
 * @author Jake
 * @since February 11, 2026
 */
public enum RenderMode {

    COMPACT,
    INLINE_COMPACT,
    JSON,
    INLINE_JSON;

    public static RenderMode from(String s) {
        if (s == null || s.isBlank()) {
            return COMPACT;
        }
        return switch (s.trim().toLowerCase()) {
            case "json" -> JSON;
            case "inline_json", "inline" -> INLINE_JSON;
            case "inline_compact" -> INLINE_COMPACT;
            default -> COMPACT; //"compact"
        };
    }
}