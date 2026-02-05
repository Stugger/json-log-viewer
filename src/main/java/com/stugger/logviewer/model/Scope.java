package com.stugger.logviewer.model;

/**
 * High-level log partition describing where records originate.
 * <p>
 * Each scope maps to a top-level folder name under the configured logs root, and also provides a compact
 * display token for UI use.
 *
 * @author Jake
 * @since January 20, 2026
 */
public enum Scope {
    PLAYER("players", "P"),
    GLOBAL("global", "G"),
    ;

    public final String folderName;
    public final String compactName;

    Scope(String folderName, String compactName) {
        this.folderName = folderName;
        this.compactName = compactName;
    }
}
