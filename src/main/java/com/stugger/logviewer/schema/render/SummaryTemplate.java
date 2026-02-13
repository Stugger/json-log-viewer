package com.stugger.logviewer.schema.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compiles and renders summary templates against JSON log objects like: "[${tile.x}, ${tile.y}, ${tile.p}] ${user}: ${msg}"
 * <p>
 * Supports:
 * <ul>
 *    <li>dot access: tile.x
 *    <li>array index: users[0]
 *    <li>coalesce: ${users[0] || npc}
 *    <li>optional groups: [[ ... ]] (rendered only if any expression inside produces a non-empty value)
 *    <li>optional group else branches: [[ TRY ||| ELSE ]] (if TRY expression produces an empty value then render ELSE expression)
 * </ul>
 * <p>
 * Templates are compiled and cached so we do NOT parse expressions per log line.
 *
 * @author Jake
 * @since February 8, 2026
 */
public final class SummaryTemplate {

    private SummaryTemplate() {}

    private static final ConcurrentHashMap<String, CompiledTemplate> CACHE = new ConcurrentHashMap<>();

    public static String render(String template, JsonObject obj) {
        if (template == null || template.isEmpty() || obj == null) {
            return template;
        }
        CompiledTemplate compiled = CACHE.computeIfAbsent(template, SummaryTemplate::compile);
        return compiled.render(obj);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /* -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
    |
    |------ Compilation
    |
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------*/

    private static CompiledTemplate compile(String template) {
        return compileRange(template, 0, template.length());
    }

    /**
     * Compiles a substring range. Used for both top-level and optional group bodies.
     */
    private static CompiledTemplate compileRange(String template, int startIdx, int endIdx) {
        List<Part> parts = new ArrayList<>();
        int i = startIdx;

        while (i < endIdx) {
            int exprStart = template.indexOf("${", i);
            int optStart = template.indexOf("[[", i);
            if (exprStart >= endIdx) {
                exprStart = -1;
            }
            if (optStart >= endIdx) {
                optStart = -1;
            }
            int next;
            TokenKind kind;

            if (exprStart == -1 && optStart == -1) {
                parts.add(new TextPart(template.substring(i, endIdx)));
                break;
            } else if (exprStart == -1) {
                next = optStart;
                kind = TokenKind.OPTIONAL;
            } else if (optStart == -1) {
                next = exprStart;
                kind = TokenKind.EXPR;
            } else if (exprStart < optStart) {
                next = exprStart;
                kind = TokenKind.EXPR;
            } else {
                next = optStart;
                kind = TokenKind.OPTIONAL;
            }

            //literal text before next token
            if (next > i) {
                parts.add(new TextPart(template.substring(i, next)));
            }

            if (kind == TokenKind.EXPR) {
                int end = template.indexOf('}', next + 2);
                if (end == -1 || end >= endIdx) {
                    // unmatched; treat rest as literal
                    parts.add(new TextPart(template.substring(next, endIdx)));
                    break;
                }

                String expr = template.substring(next + 2, end).trim();
                if (!expr.isEmpty()) {
                    parts.add(new ExprPart(parseCandidates(expr)));
                }

                i = end + 1;
                continue;
            }

            //OPTIONAL group [[ ... ]]
            int close = template.indexOf("]]", next + 2);
            if (close == -1 || close >= endIdx) {
                // unmatched; treat as literal
                parts.add(new TextPart(template.substring(next, endIdx)));
                break;
            }

            //extract inner text
            String inner = template.substring(next + 2, close);

            //split on first "|||"
            int bar = inner.indexOf("|||");
            if (bar == -1) {
                CompiledTemplate thenGroup = compileRange(inner, 0, inner.length());
                parts.add(new OptionalGroupPart(thenGroup, null));
            } else {
                String thenSrc = inner.substring(0, bar);
                String elseSrc = inner.substring(bar + 3);

                CompiledTemplate thenGroup = compileRange(thenSrc, 0, thenSrc.length());
                CompiledTemplate elseGroup = elseSrc.isBlank() ? null : compileRange(elseSrc, 0, elseSrc.length());

                parts.add(new OptionalGroupPart(thenGroup, elseGroup));
            }

            i = close + 2;
        }

        return new CompiledTemplate(template.substring(startIdx, endIdx), parts);
    }

    private enum TokenKind { EXPR, OPTIONAL }

    /* -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
    |
    |------ Rendering
    |
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------*/

    private record CompiledTemplate(String raw, List<Part> parts) {

        public String render(JsonObject obj) {
            StringBuilder out = new StringBuilder(raw.length() + 32);
            RenderCtx ctx = new RenderCtx(out);
            for (Part p : parts) {
                p.append(ctx, obj);
            }
            return out.toString();
        }
    }

    private static final class RenderCtx {
        final StringBuilder out;
        boolean hadValue; //used by optional groups
        RenderCtx(StringBuilder out) {
            this.out = out;
        }
    }

    private interface Part {
        void append(RenderCtx ctx, JsonObject obj);
    }

    private record TextPart(String text) implements Part {
        @Override public void append(RenderCtx ctx, JsonObject obj) { ctx.out.append(text); }
    }

    /**
     * Expression part with coalesce candidates in priority order.
     * Each candidate supports its own optional :format.
     */
    private record ExprPart(List<Candidate> candidates) implements Part {

        @Override
        public void append(RenderCtx ctx, JsonObject obj) {
            for (Candidate c : candidates) {
                JsonElement el = JsonValue.resolve(obj, c.path);
                if (el == null || el.isJsonNull()) {
                    continue;
                }
                String s = ValueFormat.inline(el, c.format);
                if (s != null && !s.isEmpty()) {
                    ctx.out.append(s);
                    ctx.hadValue = true;
                    return;
                }
            }
            //nothing appended
        }
    }

    /**
     * Optional group: only included if at least one expression inside produced a non-empty value.
     */
    private record OptionalGroupPart(CompiledTemplate thenGroup, CompiledTemplate elseGroup) implements Part {
        @Override
        public void append(RenderCtx ctx, JsonObject obj) {
            StringBuilder tmp = new StringBuilder();
            RenderCtx inner = new RenderCtx(tmp);
            for (Part p : thenGroup.parts) {
                p.append(inner, obj);
            }
            if (inner.hadValue) {
                ctx.out.append(tmp);
                ctx.hadValue = true;
                return;
            }
            //else branch (if present)
            if (elseGroup != null) {
                for (Part p : elseGroup.parts) {
                    p.append(ctx, obj);
                }
                //NOTE: do NOT force ctx.hadValue=true here
                //else is allowed to be literal-only and should not "activate" outer optional groups
            }
        }
    }


    /* -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
    |
    |------ Expression Parsing
    |
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------*/

    private record Candidate(String path, String format) { }

    /**
     * Parses:
     * users[0] || npc
     * and supports per-candidate:
     * users[0]:raw || npc
     */
    private static List<Candidate> parseCandidates(String expr) {
        String[] parts = expr.split("\\|\\|");
        List<Candidate> out = new ArrayList<>(parts.length);
        for (String p : parts) {
            String s = p.trim();
            if (s.isEmpty()) {
                continue;
            }
            String path;
            String fmt = null;
            int colon = s.indexOf(':');
            if (colon != -1) {
                path = s.substring(0, colon).trim();
                fmt = s.substring(colon + 1).trim();
                if (fmt.isEmpty()) {
                    fmt = null;
                }
            } else {
                path = s.trim();
            }
            if (!path.isEmpty()) {
                out.add(new Candidate(path, fmt));
            }
        }

        return out;
    }
}
