package com.stugger.logviewer.schema.render;

import com.google.gson.*;
import com.stugger.logviewer.MainApp;
import com.stugger.logviewer.schema.model.RenderMode;

import java.util.Map;

/**
 * Rendering utilities for complex (non-primitive) JSON values in schema details.
 * <p>
 * Supports multiple render modes:
 * <ul>
 *   <li>{@link RenderMode#JSON} – pretty-printed JSON</li>
 *   <li>{@link RenderMode#INLINE_JSON} – compact single-line JSON</li>
 *   <li>{@link RenderMode#COMPACT} – YAML-style block formatting</li>
 *   <li>{@link RenderMode#INLINE_COMPACT} – YAML-style inline formatting</li>
 * </ul>
 * <p>
 * Intended for use by the Details panel renderer when a schema field resolves
 * to a JSON object or array. Primitives are formatted consistently with
 * {@link ValueFormat} to maintain predictable number/string output.
 * <p>
 * The compact modes aim to reduce vertical space usage compared to pretty JSON,
 * making dense log data (e.g., item arrays) more readable in constrained UI areas.
 *
 * @author Jake
 * @since February 11, 2026
 */
public final class DetailsValueFormat {

    private DetailsValueFormat() {}

    public static String complex(JsonElement el, RenderMode mode) {
        if (el == null || el.isJsonNull()) {
            return "";
        }
        if (mode == null) {
            mode = RenderMode.COMPACT;
        }
        return switch (mode) {
            case JSON -> MainApp.PRETTY_GSON.toJson(el);
            case INLINE_JSON -> el.toString();
            case COMPACT -> compact(el, false);
            case INLINE_COMPACT -> compact(el, true);
        };
    }

    //compact (yaml-ish)
    private static String compact(JsonElement el, boolean inlineObjects) {
        if (el.isJsonObject()) {
            return inlineObjects ? objectInline(el.getAsJsonObject()) : objectBlock(el.getAsJsonObject());
        }
        if (el.isJsonArray()) {
            return arrayCompact(el.getAsJsonArray(), inlineObjects);
        }
        if (el.isJsonPrimitive()) {
            return primitive(el.getAsJsonPrimitive());
        }
        return el.toString();
    }

    private static String arrayCompact(JsonArray arr, boolean inlineObjects) {
        if (arr.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.size(); i++) {
            if (i > 0) sb.append(inlineObjects ? ",\n" : "\n\n");

            JsonElement e = arr.get(i);
            if (e == null || e.isJsonNull()) {
                sb.append("null");
            } else if (e.isJsonObject()) {
                sb.append(inlineObjects ? "[ " + objectInline(e.getAsJsonObject()) + " ]" : objectBlock(e.getAsJsonObject()));
            } else if (e.isJsonPrimitive()) {
                sb.append(primitive(e.getAsJsonPrimitive()));
            } else {
                sb.append(e);
            }
        }
        return sb.toString();
    }

    private static String objectBlock(JsonObject obj) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            if (!first) sb.append('\n');
            first = false;
            sb.append(e.getKey()).append(": ").append(valueInline(e.getValue()));
        }
        return sb.toString();
    }

    private static String objectInline(JsonObject obj) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(e.getKey()).append(": ").append(valueInline(e.getValue()));
        }
        return sb.toString();
    }

    private static String valueInline(JsonElement v) {
        if (v == null || v.isJsonNull()) return "null";
        if (v.isJsonPrimitive()) return primitive(v.getAsJsonPrimitive());
        // keep nested stuff compact on one line
        return v.toString();
    }

    private static String primitive(JsonPrimitive p) {
        if (p.isString()) return "\"" + p.getAsString() + "\"";
        if (p.isBoolean()) return String.valueOf(p.getAsBoolean());
        if (p.isNumber()) return ValueFormat.number(p.getAsNumber(), "raw");
        return p.getAsString();
    }
}
