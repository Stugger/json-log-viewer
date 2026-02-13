package com.stugger.logviewer.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stugger.logviewer.MainApp;
import com.stugger.logviewer.model.*;
import com.stugger.logviewer.schema.render.SummaryTemplate;
import com.stugger.logviewer.schema.model.SchemaDefinition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * {@link LogSource} implementation for newline-delimited JSON (JSONL) logs on disk.
 * <p>
 * Resolves candidate files based on scope/player/time window, streams each file line-by-line,
 * parses JSON objects, and converts them into {@link LogRecord}s with summaries generated via schema-based formatting.
 * <p>
 * V1 behavior:
 * <ul>
 *   <li>Only lines with a valid {@code timeMs} are considered.</li>
 *   <li>Global logs can be optionally "player filtered" when auditing specific users.</li>
 * </ul>
 * <p>
 * Resource management: the returned record stream arranges for underlying file line streams to be
 * closed when the consumer closes the record stream.
 *
 * @author Jake
 * @since January 24, 2026
 */
public class JsonlLogSource implements LogSource {

    @Override
    public Stream<LogRecord> stream(LogQuery query) {
        Path root = MainApp.getRootDirectory().toPath();

        //build a list of candidate files then stream their lines
        List<Path> files;
        try {
            files = resolveFiles(root, query);
        } catch (IOException e) {
            e.printStackTrace();
            return Stream.empty();
        }

        //keep track of all opened streams so we can close them
        List<Stream<String>> openedStreams = new ArrayList<>();

        //flat stream: file -> lines -> LogRecord
        Stream<LogRecord> result = files.stream().flatMap(file -> {
            try {
                Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8);
                openedStreams.add(lines);
                return lines
                        .map(line -> toRecord(file, line.trim(), query))
                        .filter(r -> r != null && query.withinRange(r.timeMs()));

            } catch (IOException e) {
                return Stream.empty();
            }
        });

        //ensure ALL file streams are closed when the consumer closes this stream
        return result.onClose(() -> {
            for (Stream<String> s : openedStreams) {
                try {
                    s.close();
                } catch (Exception ignored) {}
            }
        });
    }

    private static LogRecord toRecord(Path file, String rawLine, LogQuery query) {
        if (rawLine.isEmpty()) {
            return null;
        }
        try {
            JsonObject obj = JsonParser.parseString(rawLine).getAsJsonObject();
            if (!obj.has("timeMs")) {
                return null;
            }
            long timeMs = obj.get("timeMs").getAsLong();
            if (timeMs <= 0) {
                return null;
            }
            PathMeta meta = PathMeta.from(file);
            //GLOBAL + Some(players) only keep records associated with any audited player
            if (meta.scope() == Scope.GLOBAL && query.players() instanceof PlayerSelection.Some(Set<Username> usernames)) {
                if (!containsPlayer(obj, usernames)) {
                    return null;
                }
            }
            SchemaDefinition schema = MainApp.getSchemaLoader().getSchemaFromJsonObject(obj);
            String summary = schema != null ? SummaryTemplate.render(schema.summary, obj)
                    : rawLine.length() > 140 ? rawLine.substring(0, 140) + "…" : rawLine;
            return new LogRecord(
                    timeMs,
                    meta.scope(),
                    meta.username(),
                    meta.typeId(),
                    summary,
                    MainApp.PRETTY_GSON.toJson(obj)
            );
        } catch (Exception ex) {
            //bad JSON line or partial write; skip
            ex.printStackTrace();
            return null;
        }
    }

    private static boolean containsPlayer(JsonObject obj, Set<Username> usernames) {
        //1. check primary "user"
        if (obj.has("user")) {
            JsonElement userEl = obj.get("user");
            if (userEl != null && userEl.isJsonPrimitive() && userEl.getAsJsonPrimitive().isString()) {
                String user = userEl.getAsString();
                if (user != null && !user.isEmpty()) {
                    for (Username audited : usernames) {
                        if (user.equals(audited.withoutSpaces())) {
                            return true;
                        }
                    }
                }
            }
        }
        //2. check participants "users" array
        if (obj.has("users")) {
            JsonElement usersEl = obj.get("users");
            if (usersEl != null && usersEl.isJsonArray()) {
                for (JsonElement e : usersEl.getAsJsonArray()) {
                    if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
                        continue;
                    }
                    String participant = e.getAsString();
                    if (participant == null || participant.isEmpty()) {
                        continue;
                    }
                    for (Username audited : usernames) {
                        if (participant.equals(audited.withoutSpaces())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static List<Path> resolveFiles(Path logsRoot, LogQuery query) throws IOException {
        List<Path> out = new ArrayList<>();
        //determine days between from/to (inclusive)
        ZoneId zone = ZoneId.systemDefault();
        LocalDate fromDay = LocalDate.ofInstant(query.from(), zone);
        LocalDate toDay = LocalDate.ofInstant(query.to(), zone);

        Set<String> dayNames = new HashSet<>();
        for (LocalDate d = fromDay; !d.isAfter(toDay); d = d.plusDays(1)) {
            dayNames.add(MainApp.getSettings().getLogFileNameFormatter().format(d) + MainApp.getSettings().getLogFileNameExtension());
        }

        //PLAYER scope: players/<username>/.../<day>.jsonl
        if (query.players() != null && query.scopes().contains(Scope.PLAYER)) {
            Path playersRoot = logsRoot.resolve(Scope.PLAYER.folderName);
            if (Files.isDirectory(playersRoot)) {
                if (query.players() instanceof PlayerSelection.Some) {
                    for (Username username : ((PlayerSelection.Some)query.players()).usernames()) {
                        Path p = playersRoot.resolve(username.withoutSpaces());
                        collectDayFilesUnder(p, dayNames, out);
                    }
                } else { //scan all players (can be heavy; ok for v1 if you're careful)
                    try (Stream<Path> children = Files.list(playersRoot)) {
                        children.filter(Files::isDirectory)
                                .forEach(p -> collectDayFilesUnder(p, dayNames, out));
                    }
                }
            }
        }

        //GLOBAL scope: global/.../<day>.jsonl
        if (query.scopes().contains(Scope.GLOBAL)) {
            Path globalRoot = logsRoot.resolve(Scope.GLOBAL.folderName);
            collectDayFilesUnder(globalRoot, dayNames, out);
        }

        return out;
    }

    private static void collectDayFilesUnder(Path baseDir, Set<String> dayNames, List<Path> out) {
        if (baseDir == null || !Files.isDirectory(baseDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(baseDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> dayNames.contains(p.getFileName().toString()))
                    .forEach(out::add);
        } catch (IOException e) {
            System.err.println("Failed walking " + baseDir + ": " + e.getMessage());
        }
    }

}
