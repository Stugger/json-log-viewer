package com.stugger.logviewer.ui.model;

import com.stugger.logviewer.model.LogRecord;
import com.stugger.logviewer.model.Scope;
import javafx.scene.control.TreeView;

/**
 * Lightweight model object backing each node in the log toggle {@link TreeView}.
 * <p>
 * Nodes represent the hierarchy:
 * root → scope (players/global) → category → leaf type.
 * Leaf nodes carry a {@code typeId} (relative folder path under a scope) used for filtering/matching
 * loaded {@link LogRecord}s.
 * <p>
 * {@link #name} is the display label (and {@link #toString()} returns it for TreeView rendering).
 *
 * @author Jake
 * @since January 26, 2026
 */
public final class ToggleNode {

    public String name;
    public final Kind kind;
    public final Scope scope;     //null for root
    public final String typeId;   //only for LEAF

    public ToggleNode(String name, Kind kind, Scope scope) {
        this(name, kind, scope, null);
    }

    public ToggleNode(String name, Kind kind, Scope scope, String typeId) {
        this.name = name;
        this.kind = kind;
        this.scope = scope;
        this.typeId = typeId;
    }

    @Override
    public String toString() {
        return name;
    }

    // ---- Tree node model ----
    public enum Kind { ROOT, SCOPE, CATEGORY, LEAF }
}
