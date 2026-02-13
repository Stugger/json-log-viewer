package com.stugger.logviewer;

import java.io.File;

/**
 * Resolves per-user application data locations used by the Log Viewer.
 * <p>
 * Centralizes creation and lookup of the .log_viewer directory structure
 * (settings file, schemas folder, and other persisted app data).
 *
 * @author Jake
 * @since February 11, 2026
 */
public class AppPaths {

    public static final String USER_DATA_DIRECTORY = System.getProperty("user.home") + File.separator + ".log_viewer";

    public static final String SCHEMAS_DEFAULT_DIRECTORY = USER_DATA_DIRECTORY + File.separator + "schemas";

    public static final String SETTINGS_FILE_PATH = USER_DATA_DIRECTORY + File.separator + "settings.json";

    public static void checkDefaults() {
        File file = new File(USER_DATA_DIRECTORY);
        boolean hasRoot = file.exists();
        if (!hasRoot) {
            hasRoot = file.mkdir();
        }
        if (hasRoot) {
            file = new File(SCHEMAS_DEFAULT_DIRECTORY);
            if (!file.exists() && !file.mkdir()) {
                System.err.println("Failed to create default schemas directory at: " + SCHEMAS_DEFAULT_DIRECTORY);
            }
        }
    }

}
