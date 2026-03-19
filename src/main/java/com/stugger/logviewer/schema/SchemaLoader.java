package com.stugger.logviewer.schema;

import com.google.gson.JsonObject;
import com.stugger.logviewer.MainApp;
import com.stugger.logviewer.schema.model.RenderMode;
import com.stugger.logviewer.schema.model.SchemaDefinition;
import com.stugger.logviewer.schema.model.SchemaFieldDefinition;
import com.stugger.logviewer.schema.render.JsonValue;
import com.stugger.logviewer.schema.render.SummaryTemplate;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

/**
 * Loads YAML schema files and builds a registry used during rendering.
 * <p>
 * Supports loading from the configured schema directory.
 *
 * @author Jake
 * @since February 6, 2026
 */
public final class SchemaLoader {

    private final Map<String, SchemaDefinition> byId = new HashMap<>();

    public SchemaLoader() {}

    public void load() {
        SummaryTemplate.clearCache();
        JsonValue.clearCache();
        List<SchemaDefinition> schemas = loadFromDirectory(Path.of(MainApp.getSettings().getSchemasDirectory()));
        validate(schemas);
        for (SchemaDefinition sd : schemas) {
            byId.put(sd.schemaId, sd);
        }
    }

    public SchemaDefinition getSchemaFromJsonObject(JsonObject obj) {
        if (obj != null && obj.has("schemaId")) {
            try {
                String sid = obj.get("schemaId").getAsString();
                SchemaDefinition d = byId.get(sid);
                if (d != null) {
                    return d;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static List<SchemaDefinition> loadFromDirectory(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }

        List<SchemaDefinition> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.{yml,yaml}")) {
            for (Path p : stream) {
                out.addAll(loadYamlFile(p));
            }
        } catch (Exception ignored) {
            //swallow for now
        }
        return out;
    }

    private static List<SchemaDefinition> loadYamlFile(Path p) throws Exception {
        try (InputStream in = Files.newInputStream(p)) {
            return loadYamlStream(in);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<SchemaDefinition> loadYamlStream(InputStream in) {
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

        List<SchemaDefinition> schemas = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> sm)) continue;
            schemas.add(mapToSchemaDefinition((Map<String, Object>) sm));
        }
        return schemas;
    }

    private static SchemaDefinition mapToSchemaDefinition(Map<String, Object> m) {
        SchemaDefinition d = new SchemaDefinition();
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
                SchemaFieldDefinition f = new SchemaFieldDefinition();
                f.label = asString(fm.get("label"));
                f.path = asString(fm.get("path"));
                f.prefix = asString(fm.get("prefix"));
                f.append = asString(fm.get("append"));
                f.format = asString(fm.get("format"));
                f.render = RenderMode.from(asString(fm.get("render")));
                Object opt = fm.get("optional");
                f.optional = opt instanceof Boolean b ? b : null;
                d.details.add(f);
            }
        }

        return d;
    }

    private static void validate(List<SchemaDefinition> schemas) {
        Set<String> ids = new HashSet<>();
        for (SchemaDefinition d : schemas) {
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
