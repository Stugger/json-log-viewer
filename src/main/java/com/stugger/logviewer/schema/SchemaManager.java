package com.stugger.logviewer.schema;

import com.google.gson.JsonObject;
import com.stugger.logviewer.MainApp;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

/**
 *
 * @author Jake
 * @since February 6, 2026
 */
public final class SchemaManager {

    private final Map<String, Schema.Definition> byId = new HashMap<>();

    public SchemaManager() {}

    public void loadSchemas() {
        SummaryTemplate.clearCache();
        JsonValue.clearCache();
        List<Schema.Definition> schemas = loadFromDirectory(Path.of(MainApp.getSettings().getSchemasDirectory()));
        validate(schemas);
        for (Schema.Definition sd : schemas) {
            byId.put(sd.schemaId, sd);
        }
    }

    public Schema.Definition getSchemaFromJsonObject(JsonObject obj) {
        if (obj != null && obj.has("schemaId")) {
            try {
                String sid = obj.get("schemaId").getAsString();
                Schema.Definition d = byId.get(sid);
                if (d != null) {
                    return d;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static List<Schema.Definition> loadFromDirectory(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }

        List<Schema.Definition> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.{yml,yaml}")) {
            for (Path p : stream) {
                out.addAll(loadYamlFile(p));
            }
        } catch (Exception ignored) {
            //swallow for now
        }
        return out;
    }

    private static List<Schema.Definition> loadYamlFile(Path p) throws Exception {
        try (InputStream in = Files.newInputStream(p)) {
            return loadYamlStream(in);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Schema.Definition> loadYamlStream(InputStream in) {
        //SnakeYAML engine loads to Maps by default - we'll map manually into POJOs (simple and robust)
        LoadSettings settings = LoadSettings.builder().build();
        Load load = new Load(settings);

        Object root = load.loadFromInputStream(in);
        if (!(root instanceof Map<?, ?> m)) {
            return List.of();
        }

        Object schemasObj = m.get("schemas");
        if (!(schemasObj instanceof List<?> list)) {
            return List.of();
        }

        List<Schema.Definition> defs = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> sm)) continue;
            defs.add(mapToSchemaDef((Map<String, Object>) sm));
        }
        return defs;
    }

    private static Schema.Definition mapToSchemaDef(Map<String, Object> m) {
        Schema.Definition d = new Schema.Definition();
        d.schemaId = asString(m.get("schemaId"));
        d.displayName = asString(m.get("displayName"));
        d.summary = asString(m.get("summary"));

        Object detailsObj = m.get("details");
        if (detailsObj instanceof List<?> detailsList) {
            d.details = new ArrayList<>();
            for (Object item : detailsList) {
                if (!(item instanceof Map<?, ?> fm)) {
                    continue;
                }
                Schema.FieldDefinition f = new Schema.FieldDefinition();
                f.label = asString(fm.get("label"));
                f.path = asString(fm.get("path"));
                f.format = asString(fm.get("format"));
                //f.render = asString(fm.get("render"));
                Object opt = fm.get("optional");
                f.optional = opt instanceof Boolean b ? b : null;
                d.details.add(f);
            }
        }

        return d;
    }

    private static void validate(List<Schema.Definition> defs) {
        Set<String> ids = new HashSet<>();
        for (Schema.Definition d : defs) {
            if (d.schemaId == null || d.schemaId.isBlank()) {
                throw new IllegalStateException("Schema missing schemaId");
            }
            if (!ids.add(d.schemaId)) {
                throw new IllegalStateException("Duplicate schemaId: " + d.schemaId);
            }
            if (d.displayName == null || d.displayName.isBlank()) {
                throw new IllegalStateException("Schema " + d.schemaId + " missing displayName");
            }
            if (d.summary == null || d.summary.isBlank()) {
                throw new IllegalStateException("Schema " + d.schemaId + " missing summary");
            }
        }
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static List<String> asStringList(Object o) {
        if (o == null) {
            return List.of();
        }
        if (o instanceof List<?> l) {
            List<String> out = new ArrayList<>(l.size());
            for (Object x : l) {
                if (x == null) {
                    continue;
                }
                out.add(String.valueOf(x));
            }
            return out;
        }
        return List.of(String.valueOf(o));
    }
}
