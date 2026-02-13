package com.stugger.logviewer;

import com.google.gson.*;
import com.stugger.logviewer.model.DayRange;
import com.stugger.logviewer.ui.AlertManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;

/**
 * Persistent user configuration for the Log Viewer.
 * <p>
 * Stores UI + filesystem preferences such as logs root directory, schema directory,
 * and any formatting options used during loading and display.
 *
 * @author Jake
 * @since January 27, 2026
 */
public class Settings {

    private static final String DEFAULT_FILE_NAME_FORMAT = "yyyy_MM_dd";
    private static final String DEFAULT_FILE_NAME_EXTENSION = ".jsonl";
    private static final DayRange DEFAULT_TIME_RANGE = DayRange.ZERO_DAYS;

    private String gameName;
    private String defaultRootDirectory;

    private String schemasDirectory = AppPaths.SCHEMAS_DEFAULT_DIRECTORY;
    private String logFileNameFormat = DEFAULT_FILE_NAME_FORMAT;
    private String logFileNameExtension = DEFAULT_FILE_NAME_EXTENSION;
    private DayRange defaultIncludedDays = DEFAULT_TIME_RANGE;
    private boolean openNewSessionOnLaunch;

    private DateTimeFormatter logFileNameFormatter;

    public Settings() {
        /* empty */
    }

    public void load() {
        System.out.println("Loading settings...");
        Path path = Paths.get(AppPaths.SETTINGS_FILE_PATH);
        if (!Files.exists(path)) {
            save();
            logFileNameFormatter = DateTimeFormatter.ofPattern(logFileNameFormat);
            return;
        }
        try (FileReader reader = new FileReader(path.toFile())) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || root.isJsonNull()) {
                save();
                logFileNameFormatter = DateTimeFormatter.ofPattern(logFileNameFormat);
                return;
            }
            JsonObject j = root.getAsJsonObject();
            //read with defaults (so missing keys don't null out fields)
            gameName = getString(j, "gameName", gameName);
            defaultRootDirectory = getString(j, "defaultRootDirectory", defaultRootDirectory);
            schemasDirectory = getString(j, "schemasDirectory", schemasDirectory);
            logFileNameFormat = getString(j, "logFileNameFormat", logFileNameFormat);
            logFileNameExtension = getString(j, "logFileNameExtension", logFileNameExtension);
            defaultIncludedDays = DayRange.valueOf(getString(j, "defaultIncludedDays", defaultIncludedDays.name()));
            openNewSessionOnLaunch = getBoolean(j, "openNewSessionOnLaunch", openNewSessionOnLaunch);
        } catch (Exception e) {
            AlertManager.notifyException(e);
            save(); //if settings are corrupted, fall back to defaults and rewrite
        }
        logFileNameFormatter = DateTimeFormatter.ofPattern(logFileNameFormat);
    }

    private String getString(JsonObject j, String key, String def) {
        JsonElement el = j.get(key);
        return (el == null || el.isJsonNull()) ? def : el.getAsString();
    }

    private boolean getBoolean(JsonObject j, String key, boolean def) {
        JsonElement el = j.get(key);
        return (el == null || el.isJsonNull()) ? def : el.getAsBoolean();
    }

    public void save() {
        JsonObject obj = new JsonObject();
        obj.addProperty("gameName", gameName);
        obj.addProperty("defaultRootDirectory", defaultRootDirectory);
        obj.addProperty("schemasDirectory", schemasDirectory);
        obj.addProperty("logFileNameFormat", logFileNameFormat);
        obj.addProperty("logFileNameExtension", logFileNameExtension);
        obj.addProperty("defaultIncludedDays", defaultIncludedDays.name());
        obj.addProperty("openNewSessionOnLaunch", openNewSessionOnLaunch);
        File file = Paths.get(AppPaths.SETTINGS_FILE_PATH).toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(MainApp.PRETTY_GSON.toJson(obj));
            logFileNameFormatter = DateTimeFormatter.ofPattern(logFileNameFormat);
        } catch (Exception e) {
            AlertManager.notifyException(e);
        }
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    public void setDefaultRootDirectory(String defaultRootDirectory) {
        this.defaultRootDirectory = defaultRootDirectory;
    }

    public void setSchemasDirectory(String schemasDirectory) {
        this.schemasDirectory = schemasDirectory;
    }

    public void setLogFileNameFormat(String logFileNameFormat) {
        this.logFileNameFormat = logFileNameFormat;
    }

    public void setLogFileNameExtension(String logFileNameExtension) {
        this.logFileNameExtension = logFileNameExtension;
    }

    public void setDefaultIncludedDays(DayRange defaultIncludedDays) {
        this.defaultIncludedDays = defaultIncludedDays;
    }

    public void setOpenNewSessionOnLaunch(boolean openNewSessionOnLaunch) {
        this.openNewSessionOnLaunch = openNewSessionOnLaunch;
    }

    public String getGameName() {
        return gameName;
    }

    public String getDefaultRootDirectory() {
        return defaultRootDirectory;
    }

    public String getSchemasDirectory() {
        return schemasDirectory;
    }

    public String getLogFileNameFormat() {
        return logFileNameFormat;
    }

    public String getLogFileNameExtension() {
        return logFileNameExtension;
    }

    public DayRange getDefaultIncludedDays() {
        return defaultIncludedDays;
    }

    public boolean isOpenNewSessionOnLaunch() {
        return openNewSessionOnLaunch;
    }

    public DateTimeFormatter getLogFileNameFormatter() {
        return logFileNameFormatter;
    }
}
