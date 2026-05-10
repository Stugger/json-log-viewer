package com.stugger.logviewer.ui;

import com.stugger.logviewer.MainApp;
import com.stugger.logviewer.api.LogProviderApiClient;
import com.stugger.logviewer.ui.components.SessionTab;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Application-level controller for the main window.
 * <p>
 * Manages the session tab container, status bar messaging, and top-level actions
 * such as choosing the logs root directory, opening new audit sessions, and showing settings.
 * <p>
 * This class intentionally keeps global app wiring (stage ownership, stylesheet setup,
 * root directory validation) centralized so session controllers can stay focused on per-tab behavior.
 *
 * @author Jake
 * @since January 19, 2026
 */
public class MainController {

    @FXML public MenuItem new_tab_menu_item, choose_root_menu_item;

    @FXML private TabPane session_tabs;

    @FXML private Label statusLabel;

    @FXML
    private void initialize() {
        setStatus("Ready");
    }

    @FXML
    private void on_click_choose_logs_root() {
        if (choose_root_menu_item.isDisable()) { //still scanning folders
            return;
        }
        if (!session_tabs.getTabs().isEmpty()) {
            AlertManager.show(Alert.AlertType.WARNING,
                    "Unable to set root directory",
                    "There are " + session_tabs.getTabs().size() + " session(s) still active.",
                    "You must close all tabs before you can set the root directory.");
            return;
        }
        final DirectoryChooser directoryChooser = new DirectoryChooser();
        final File directory = directoryChooser.showDialog(MainApp.getStage());
        if (directory != null) {
            if (!MainApp.isValidRootDirectory(directory)) {
                AlertManager.show(Alert.AlertType.ERROR, "Unable to set root directory",
                        "The chosen root directory does not contain any valid folders.",
                        "You must set a root directory that contains at least one scope.");
                return;
            }
            MainApp.setRootDirectory(directory);
        }
    }

    @FXML
    private void on_click_new_tab() throws IOException {
        if (new_tab_menu_item.isDisable()) { //still scanning folders
            return;
        }
        if (MainApp.getRootDirectory() == null) {
            AlertManager.show(Alert.AlertType.ERROR,
                    "No root directory selected",
                    "There is no selected directory to load log files from.",
                    "You can select a root directory from File -> Choose Logs Root");
            return;
        }
        openNewSession();
    }

    @FXML
    private void on_click_settings() throws IOException {
        Parent root;
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/ui/settings.fxml"));
        root = loader.load();
        Scene scene = new Scene(root, 600, 390);
        scene.getStylesheets().add(
                Objects.requireNonNull(MainApp.class.getResource("/ui/theme-dark.css")).toExternalForm()
        );
        Stage stage = new Stage();
        stage.getIcons().addAll(MainApp.getStage().getIcons());
        stage.setTitle("Log Viewer Settings");
        stage.setScene(scene);
        stage.setResizable(false);
        SettingsController settings = loader.getController();
        settings.prepare(stage);
        stage.initOwner(MainApp.getStage());
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.show();
    }

    @FXML
    private void on_click_reload_schemas() {
        MainApp.getSchemaLoader().load();
        setStatus("Reloaded schemas");
    }

    @FXML
    private void on_click_download_logs() {
        String apiUrl = MainApp.getSettings().getLogProviderApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            AlertManager.show(Alert.AlertType.ERROR,
                    "No Log Provider API URL",
                    "There is no configured Log Provider API URL.",
                    "Set it in File -> Settings first.");
            return;
        }

        setStatus("Loading remote log metadata...");
        final MainController mainController = this;
        var task = new Task<Void>() {

            private List<String> globalTypes;
            private List<String> playerTypes;

            @Override
            protected Void call() throws Exception {
                var client = new LogProviderApiClient();
                globalTypes = client.getGlobalLogTypes();
                playerTypes = client.getPlayerLogTypes();
                return null;
            }

            @Override
            protected void succeeded() {
                try {
                    setStatus("Loaded remote log metadata");
                    Parent root;
                    FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/ui/download_logs.fxml"));
                    root = loader.load();
                    Scene scene = new Scene(root, 500, 550);
                    scene.getStylesheets().add(
                            Objects.requireNonNull(MainApp.class.getResource("/ui/theme-dark.css")).toExternalForm()
                    );
                    Stage stage = new Stage();
                    stage.getIcons().addAll(MainApp.getStage().getIcons());
                    stage.setTitle("Remote Download Logs");
                    stage.setScene(scene);
                    stage.setMinWidth(scene.getWidth());
                    stage.setMaxWidth(scene.getWidth());
                    DownloadLogsController controller = loader.getController();
                    controller.prepare(mainController, stage, globalTypes, playerTypes);
                    stage.initOwner(MainApp.getStage());
                    stage.initModality(Modality.APPLICATION_MODAL);
                    stage.show();
                } catch (Exception e) {
                    AlertManager.notifyException(e);
                }
            }

            @Override
            protected void failed() {
                setStatus("Failed to load remote log metadata");
                AlertManager.notifyException(getException());
            }
        };

        Thread thread = new Thread(task, "remote-log-metadata-loader");
        thread.setDaemon(true);
        thread.start();
    }

    public void openNewSession() throws IOException {
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/ui/session.fxml"));
        Node root = loader.load();
        SessionController controller = loader.getController();
        controller.open(this);
        SessionTab tab = new SessionTab(controller);
        controller.setTab(tab);
        tab.setText("Active Session");
        tab.setContent(root);
        session_tabs.getTabs().add(tab);
        session_tabs.getSelectionModel().select(tab);
    }

    public ObservableList<Tab> getTabs() {
        return session_tabs.getTabs();
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }

}
