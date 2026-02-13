package com.stugger.logviewer.data;

import com.stugger.logviewer.model.Scope;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight metadata derived from a log file path.
 * <p>
 * Extracts scope (PLAYER/GLOBAL), username (if applicable), and type id based on
 * the canonical folder layout under the logs root.
 *
 * @author Jake
 * @since January 24, 2026
 */
public record PathMeta(Scope scope, String username, String typeId) {

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
