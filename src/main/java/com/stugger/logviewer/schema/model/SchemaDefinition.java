package com.stugger.logviewer.schema.model;

import java.util.List;

/**
 * In-memory representation of a single schema definition loaded from YAML.
 * <p>
 * Defines schemaId, displayName, summary template, and details {@link SchemaFieldDefinition}s used to render logs.
 *
 * @author Jake
 * @since February 8, 2026
 */
public final class SchemaDefinition {

    public String schemaId;
    public String displayName;
    public String summary;
    public List<SchemaFieldDefinition> details;

}
