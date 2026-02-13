package com.stugger.logviewer.schema.model;

/**
 * Definition of a single details field inside a schema.
 * <p>
 * Controls label, JSON path resolution, optional suppression rules, and rendering
 * mode/formatting used by the Details panel.
 *
 * @author Jake
 * @since February 8, 2026
 */
public final class SchemaFieldDefinition {

    public String label;
    public String path;
    public Boolean optional; // true=emdash if missing, false=exclude if missing. Special case for numbers: if true and value is 0, then exclude
    public String format;    // "raw", "commas"
    public RenderMode render;

}
