package com.stugger.logviewer.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Jake
 * @since February 8, 2026
 */
public record PathToken(List<Step> steps) {

    public JsonElement resolve(JsonObject root) {
        JsonElement cur = root;
        for (Step s : steps) {
            if (cur == null || cur.isJsonNull()) {
                return null;
            }
            if (s instanceof KeyStep(String key)) {
                if (!cur.isJsonObject()) {
                    return null;
                }
                JsonObject o = cur.getAsJsonObject();
                cur = o.get(key);
            } else if (s instanceof IndexStep(int idx)) {
                if (!cur.isJsonArray()) {
                    return null;
                }
                JsonArray a = cur.getAsJsonArray();
                if (idx < 0 || idx >= a.size()) {
                    return null;
                }
                cur = a.get(idx);
            } else {
                return null;
            }
        }

        return cur;
    }

    public static PathToken parse(String expr) {
        //parse once, keeping it simple and strict.
        //grammar (rough): segment (('.' segment)*) where segment = key (index*) , index = '[' number ']'
        List<Step> steps = new ArrayList<>();
        int i = 0;

        while (i < expr.length()) {
            //parse key
            int keyStart = i;
            while (i < expr.length()) {
                char c = expr.charAt(i);
                if (c == '.' || c == '[') {
                    break;
                }
                i++;
            }
            String key = expr.substring(keyStart, i).trim();
            if (!key.isEmpty()) {
                steps.add(new KeyStep(key));
            } else {
                //invalid token like ".x" or "[0]" without a key, treated as empty (always resolves null)
                return new PathToken(List.of());
            }
            //parse zero or more [index]
            while (i < expr.length() && expr.charAt(i) == '[') {
                int close = expr.indexOf(']', i + 1);
                if (close == -1) {
                    return new PathToken(List.of());
                }

                String inside = expr.substring(i + 1, close).trim();
                int idx;
                try {
                    idx = Integer.parseInt(inside);
                } catch (NumberFormatException e) {
                    return new PathToken(List.of());
                }
                steps.add(new IndexStep(idx));
                i = close + 1;
            }
            //consume optional dot
            if (i < expr.length() && expr.charAt(i) == '.') {
                i++;
            }
        }

        return new PathToken(steps);
    }

    private sealed interface Step permits KeyStep, IndexStep { }

    private record KeyStep(String key) implements Step { }

    private record IndexStep(int idx) implements Step { }

}
