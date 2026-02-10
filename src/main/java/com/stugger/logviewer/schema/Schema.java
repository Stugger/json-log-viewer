package com.stugger.logviewer.schema;

import java.util.List;

/**
 *
 * @author Jake
 * @since February 8, 2026
 */
public final class Schema {

    private Schema() {}

    public static final class Definition {
        public String schemaId;
        public String displayName;
        public String summary;
        public List<FieldDefinition> details;
    }

    public static final class FieldDefinition {
        public String label;
        public String path;
        public String format;    // "raw"
        //public String render;    // "json", "list_inline", "list_lines"
        public Boolean optional; // true=emdash if missing, false=exclude if missing
    }
}
