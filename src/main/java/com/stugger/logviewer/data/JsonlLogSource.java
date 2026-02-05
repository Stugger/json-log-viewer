package com.stugger.logviewer.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stugger.logviewer.MainApp;
import com.stugger.logviewer.model.*;

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
 * parses JSON objects, and converts them into {@link LogRecord}s.
 * <p>
 * V1 behavior:
 * <ul>
 *   <li>Only lines with a valid {@code timeMs} are considered.</li>
 *   <li>Global logs can be optionally "player filtered" when auditing specific users.</li>
 *   <li>Summaries are generated via {@code fallbackSummary} until schema-based formatting is introduced.</li>
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
            //check if any record has a field with a value of an audited players username
            if (query.players() instanceof PlayerSelection.Some && file.toString().contains(Scope.GLOBAL.folderName)) {
                boolean matched = false;
                usernames: for (Username username : ((PlayerSelection.Some)query.players()).usernames()) {
                    if (rawLine.contains("\"" + username.withoutSpaces() + "\"")) {
                        for (String key : obj.keySet()) {
                            JsonElement el = obj.get(key);
                            if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                                if (el.getAsString().equals(username.withoutSpaces())) {
                                    matched = true;
                                    break usernames;
                                }
                            }
                        }
                    }
                }
                if (!matched) {
                    return null;
                }
            }
            PathMeta meta = PathMeta.from(file);
            String summary = fallbackSummary(obj, meta, rawLine);
            return new LogRecord(
                    timeMs,
                    meta.scope,
                    meta.username,
                    meta.typeId,
                    summary,
                    rawLine
            );
        } catch (Exception ignored) {
            //bad JSON line or partial write; skip
            return null;
        }
    }

    //TODO will be replaced with schema architecture - currently reflects my own use case
    private static String fallbackSummary(JsonObject obj, PathMeta meta, String rawLine) {
        // Staff action style
        if (obj.has("staffMember") && obj.has("info")) {
            return obj.get("staffMember").getAsString() + ": " + obj.get("info").getAsString();
        }

        // Login
        if (meta.typeId().contains("login")) {
            return obj.get("username").getAsString() + ": ip=" + obj.get("ip").getAsString() + ", mac=" + obj.get("mac").getAsString() + ", pcuid=" + obj.get("pcuid").getAsString();
        }

        // Command style
        if (obj.has("command")) {
            return ";;" + obj.get("command").getAsString() + (obj.has("additionalInfo") ? " " + obj.get("additionalInfo").getAsString() : "");
        }

        // Chat style
        if (obj.has("sentBy") && obj.has("message")) {
            StringBuilder sb = new StringBuilder();
            if (obj.has("chunkX")) { //public
                sb.append("[").append(obj.get("chunkX").getAsInt()).append(", ").append(obj.get("chunkY").getAsInt()).append(", ").append(obj.get("plane").getAsInt()).append("] ");
            } else if (obj.has("channel")) { //friends
                sb.append("[").append(obj.get("channel").getAsString()).append("] ");
            } else if (obj.has("activityName")) { //party
                sb.append("[").append(obj.get("activityName").getAsString()).append("] ");
            } else if (obj.has("groupName")) { //irongroup
                sb.append("[").append(obj.get("groupName").getAsString()).append("] ");
            } else if (obj.has("sentTo")) { //direct
                sb.append("[to ").append(obj.get("sentTo").getAsString()).append("] ");
            }
            sb.append(obj.get("sentBy").getAsString()).append(": ").append(obj.get("message").getAsString());
            return sb.toString();
        }

        // Items style
        if (meta.typeId().contains("items/") && obj.has("item")) {
            StringBuilder sb = new StringBuilder(meta.typeId().replace("items/", "") + " " + obj.get("item"));
            if (obj.has("tile")) {
                sb.append(" at tile(").append(obj.get("tile")).append(")");
            }
            if (obj.has("ownerUsername")) {
                sb.append(", owner=").append(obj.get("ownerUsername").getAsString());
            }
            return sb.toString();
        }

        // Trade style (player)
        if (obj.has("withPlayer") && obj.has("receivedValue")) {
            return "traded with " + obj.get("withPlayer").getAsString() + ": gave " + obj.get("gaveValue").getAsLong() + "gp worth of items in exchange for " + obj.get("receivedValue").getAsLong() + "gp worth of items";
        }

        // Trade style (global)
        if (obj.has("playerOne") && obj.has("playerOneValue")) {
            String playerOne = obj.get("playerOne").getAsString();
            String playerTwo = obj.get("playerTwo").getAsString();
            return "[" + playerOne + " & " + playerTwo + "] " + playerOne + " value: " + obj.get("playerOneValue").getAsLong() + "gp, " + playerTwo + " value: " + obj.get("playerTwoValue").getAsLong() + "gp\n"
                    + playerOne + " items: " + obj.get("playerOneItems") + "\n" +  playerTwo + " items: " + obj.get("playerTwoItems");
        }

        // Death-ish
        if (meta.typeId().contains("deaths/") && obj.has("killer")) {
            return "killed by " + obj.get("killer").getAsString();
        }

        // Generic fallback: typeId + clipped JSON
        String clipped = rawLine.length() > 140 ? rawLine.substring(0, 140) + "…" : rawLine;
        return meta.typeId + " • " + clipped;
    }

    private static List<Path> resolveFiles(Path logsRoot, LogQuery query) throws IOException {
        List<Path> out = new ArrayList<>();
        //determine days between from/to (inclusive)
        ZoneId zone = ZoneId.systemDefault();
        LocalDate fromDay = LocalDate.ofInstant(query.from(), zone);
        LocalDate toDay = LocalDate.ofInstant(query.to(), zone);

        List<String> dayNames = new ArrayList<>();
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
                        children.filter(Files::isDirectory).forEach(p -> collectDayFilesUnder(p, dayNames, out));
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

    private static void collectDayFilesUnder(Path baseDir, List<String> dayNames, List<Path> out) {
        if (baseDir == null || !Files.isDirectory(baseDir)) return;

        try (Stream<Path> walk = Files.walk(baseDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return dayNames.contains(name);
                    })
                    .forEach(out::add);
        } catch (IOException ignored) {}
    }

    /**
     * @param username null for global
     * @param typeId   relative folder under scope/user
     */
    private record PathMeta(Scope scope, String username, String typeId) {

        static PathMeta from(Path file) {
                List<String> parts = new ArrayList<>();
                for (Path p : file) {
                    parts.add(p.toString());
                }

                //find "players" or "global" segment
                int playersIdx = parts.indexOf(Scope.PLAYER.folderName);
                int globalIdx = parts.indexOf(Scope.GLOBAL.folderName);

                if (playersIdx >= 0 && parts.size() > playersIdx + 2) {
                    String user = parts.get(playersIdx + 1);
                    //typeId is everything after user and before filename
                    String typeId = String.join("/", parts.subList(playersIdx + 2, parts.size() - 1));
                    return new PathMeta(Scope.PLAYER, user, typeId);
                }
                if (globalIdx >= 0 && parts.size() > globalIdx + 1) {
                    String typeId = String.join("/", parts.subList(globalIdx + 1, parts.size() - 1));
                    return new PathMeta(Scope.GLOBAL, null, typeId);
                }

                return new PathMeta(Scope.GLOBAL, null, "unknown");
            }
        }
}
