package com.stugger.logviewer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.stugger.logviewer.model.*;
import com.stugger.logviewer.schema.SchemaManager;
import com.stugger.logviewer.ui.AlertManager;
import com.stugger.logviewer.ui.MainController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 *
 * @author Jake
 * @since January 19, 2026
 */
public class MainApp extends Application {

    private static final String TITLE = "Game Log Viewer";

    public static final String USER_DATA_DIRECTORY = System.getProperty("user.home") + File.separator + ".log_viewer";

    private static Stage stage;
    private static MainController mainController;

    private static Settings settings;

    private static SchemaManager schemaManager;

    private static File rootDirectory;

    public static final Map<String, Set<String>> PLAYER_TREE_FOLDER_NAMES = new TreeMap<>();
    public static final Map<String, Set<String>> GLOBAL_TREE_FOLDER_NAMES = new TreeMap<>();

    public static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    @Override
    public void start(Stage stage) throws IOException {
        MainApp.stage = stage;
        settings = new Settings();
        settings.load();
        schemaManager = new SchemaManager();
        schemaManager.loadSchemas();
        FXMLLoader loader = new FXMLLoader(
                MainApp.class.getResource("/ui/main.fxml")
        );
        Scene scene = new Scene(loader.load(), 1600, 1000);
        scene.getStylesheets().add(
                Objects.requireNonNull(MainApp.class.getResource("/ui/theme-dark.css")).toExternalForm()
        );
        mainController = loader.getController();
        stage.setScene(scene);
        stage.show();
        if (settings.getDefaultRootDirectory() != null && isValidRootDirectory(new File(settings.getDefaultRootDirectory()))) {
            setRootDirectory(new File(settings.getDefaultRootDirectory()), settings.isOpenNewSessionOnLaunch());
        } else if (settings.getGameName() != null) {
            stage.setTitle(TITLE.replace("Game", settings.getGameName()));
        } else {
            stage.setTitle(TITLE);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static void setRootDirectory(File rootDirectory) {
        setRootDirectory(rootDirectory, false);
    }

    private static void setRootDirectory(File rootDirectory, boolean openNewSession) {
        String title = TITLE;
        if (settings.getGameName() != null) {
            title = title.replace("Game", settings.getGameName());
        }
        stage.setTitle(title + " - Root: " + rootDirectory);
        MainApp.rootDirectory = rootDirectory;

        mainController.new_tab_menu_item.setDisable(true);
        mainController.choose_root_menu_item.setDisable(true);
        mainController.setStatus("Scanning log folders...");

        var task = new Task<Void>() {
            @Override
            protected Void call() {
                reloadCategories();
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            mainController.new_tab_menu_item.setDisable(false);
            mainController.choose_root_menu_item.setDisable(false);
            mainController.setStatus("Identified " + PLAYER_TREE_FOLDER_NAMES.size() + " player log categories and " + GLOBAL_TREE_FOLDER_NAMES.size() + " global log categories");
            if (openNewSession) {
                try {
                    mainController.openNewSession();
                } catch (IOException ex) {
                    Platform.runLater(() -> AlertManager.notifyException("Failed to open new session", ex));
                }
            }
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            mainController.setStatus("Failed to scan log folders");
            ex.printStackTrace();
        });

        new Thread(task, "log-folder-scan").start();
    }

    public static boolean isValidRootDirectory(File rootDirectory) {
        for (Scope scope : Scope.values()) {
            File scopeFolder = rootDirectory.toPath().resolve(scope.folderName).toFile();
            if (scopeFolder.exists() && scopeFolder.listFiles() != null) {
                return true;
            }
        }
        return false;
    }

    private static void reloadCategories() {
        PLAYER_TREE_FOLDER_NAMES.clear();
        File playersFolder = getRootDirectory().toPath().resolve(Scope.PLAYER.folderName).toFile();
        if (playersFolder.exists()) {
            File[] playerFolders = playersFolder.listFiles();
            if (playerFolders != null) {
                for (File playerFolder : playerFolders) {
                    populateTreeFolderNames(playerFolder.listFiles(), PLAYER_TREE_FOLDER_NAMES);
                }
            }
        }
        GLOBAL_TREE_FOLDER_NAMES.clear();
        File globalFolder = getRootDirectory().toPath().resolve(Scope.GLOBAL.folderName).toFile();
        if (globalFolder.exists()) {
            populateTreeFolderNames(globalFolder.listFiles(), GLOBAL_TREE_FOLDER_NAMES);
        }
    }

    private static void populateTreeFolderNames(File[] files, Map<String, Set<String>> map) {
        if (files == null) {
            return;
        }
        for (File categoryFolder : files) {
            if (!categoryFolder.isDirectory()) { //should only contain category folders
                continue;
            }
            String categoryName = categoryFolder.toPath().getFileName().toString();
            Set<String> types = map.getOrDefault(categoryName, new TreeSet<>());
            for (File typeFolder : Objects.requireNonNull(categoryFolder.listFiles())) {
                if (!typeFolder.isDirectory()) { //contains a file, that means it is a category folder
                    types.clear(); //should already be empty
                    break;
                }
                types.add(typeFolder.toPath().getFileName().toString());
            }
            map.put(categoryName, types);
        }
    }

    public static Settings getSettings() {
        return settings;
    }

    public static SchemaManager getSchemaManager() {
        return schemaManager;
    }

    public static File getRootDirectory() {
        return rootDirectory;
    }

    public static Stage getStage() {
        return stage;
    }
}
