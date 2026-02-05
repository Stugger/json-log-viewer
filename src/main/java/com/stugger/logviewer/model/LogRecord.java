package com.stugger.logviewer.model;

/**
 * Normalized, UI-ready representation of a single log entry.
 * <p>
 * Stores the canonical timestamp ({@code timeMs}), inferred scope/player/type metadata from the file path,
 * a human-readable summary (V1: best-effort heuristics), and the raw JSON payload for inspection/searching.
 * <p>
 * Records are treated as immutable data; filtering and sorting are done externally.
 *
 * @author Jake
 * @since January 20, 2026
 */
public record LogRecord(
        long timeMs,
        Scope scope,
        String player,
        String typeId,
        String summary,
        String rawJson
) {}
