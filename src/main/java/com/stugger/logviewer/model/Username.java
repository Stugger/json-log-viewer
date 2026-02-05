package com.stugger.logviewer.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Normalized wrapper for a RuneScape-style username.
 * <p>
 * Stores a canonical "folder-safe" form using underscores ({@link #withoutSpaces()}) and a display-friendly
 * form using spaces ({@link #withSpaces()}). Input is trimmed and lower-cased; either underscore or space
 * separators are accepted and normalized.
 * <p>
 * Equality is based on the normalized forms so usernames compare the same whether constructed from
 * "name_with_spaces" or "name with spaces".
 *
 * @author Jake
 * @since February 1, 2026
 */

public class Username {

    private final String withSpaces, withoutSpaces;

    public Username(String username) {
        username = username.trim().toLowerCase(Locale.ROOT);
        if (username.contains("_")) {
            withoutSpaces = username;
            withSpaces = username.replace('_', ' ');
        } else if (username.contains(" ")) {
            withoutSpaces = username.replace(' ', '_');
            withSpaces = username;
        } else {
            withoutSpaces = username;
            withSpaces = null;
        }
    }

    public String withSpaces() {
        return withSpaces == null ? withoutSpaces : withSpaces;
    }

    public String withoutSpaces() {
        return withoutSpaces;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Username)) {
            return false;
        }
        if (withoutSpaces.equals(((Username)o).withoutSpaces)) {
            return true;
        }
        return withSpaces != null && withSpaces.equals(((Username)o).withSpaces);
    }

    @Override
    public int hashCode() {
        return Objects.hash(withoutSpaces, withSpaces);
    }

}
