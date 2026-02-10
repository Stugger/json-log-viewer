package com.stugger.logviewer.schema;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.text.DecimalFormat;

/**
 *
 * @author Jake
 * @since February 8, 2026
 */
public final class ValueFormat {
    private ValueFormat() {}

    private static final ThreadLocal<DecimalFormat> COMMA = ThreadLocal.withInitial(() -> new DecimalFormat("#,###"));

    public static String inline(JsonElement el, String format) {
        if (el == null || el.isJsonNull()) {
            return "";
        }
        if (el.isJsonPrimitive()) {
            JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isString()) {
                return p.getAsString();
            }
            if (p.isBoolean()) {
                return String.valueOf(p.getAsBoolean());
            }
            if (p.isNumber()) {
                return number(p.getAsNumber(), format);
            }
            return p.getAsString();
        }
        return el.toString(); //compact JSON for summary
    }

    public static String number(Number n, String format) {
        if (n == null) {
            return "";
        }
        //format override
        if ("raw".equalsIgnoreCase(format)) {
            return n.toString();
        }
        //use string form to detect decimals / exponents safely (don't comma-format decimals)
        String s = n.toString();
        if (s.indexOf('.') != -1 || s.indexOf('e') != -1 || s.indexOf('E') != -1) {
            return s;
        }
        long v;
        try { v = Long.parseLong(s); }
        catch (NumberFormatException e) { return s; }
        //only comma-format >= 1000 unless explicitly set
        if (Math.abs(v) < 1000 && !"commas".equalsIgnoreCase(format)) {
            return s;
        }
        return COMMA.get().format(v);
    }
}
