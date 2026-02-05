package com.stugger.logviewer.model;

import java.util.Set;

/**
 * Player audit selection used by {@link LogQuery}.
 * <p>
 * Represents either:
 * <ul>
 *   <li>{@link All}: scan all player folders (potentially expensive), or</li>
 *   <li>{@link Some}: limit loading to a specific set of usernames.</li>
 * </ul>
 * This is separate from UI state so log sources can make clear decisions about which folders to touch.
 *
 * @author Jake
 * @since February 1, 2026
 */
sealed public interface PlayerSelection permits PlayerSelection.All, PlayerSelection.Some {

  record All() implements PlayerSelection { }

  record Some(Set<Username> usernames) implements PlayerSelection { }

}