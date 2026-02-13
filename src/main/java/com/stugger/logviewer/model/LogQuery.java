package com.stugger.logviewer.model;

import java.time.Instant;
import java.util.Set;

/**
 * Immutable query describing which logs to load.
 * <p>
 * Contains:
 * <ul>
 *   <li>Enabled {@link Scope}s</li>
 *   <li>Optional player selection (all players vs a specific audited set)</li>
 *   <li>Inclusive time window</li>
 * </ul>
 * This object is intended to be stable across UI layers and log sources.
 *
 * @author Jake
 * @since January 24, 2026
 */
public record LogQuery(
        Set<Scope> scopes,
        PlayerSelection players,
        Instant from,
        Instant to) {

    public boolean withinRange(long timeMs) {
        Instant t = Instant.ofEpochMilli(timeMs);
        return !t.isBefore(from) && !t.isAfter(to);
    }
}
