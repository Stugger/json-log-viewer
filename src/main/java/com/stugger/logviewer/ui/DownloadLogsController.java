package com.stugger.logviewer.ui;

import com.stugger.logviewer.MainApp;
import com.stugger.logviewer.api.LogProviderApiClient;
import com.stugger.logviewer.api.model.RemoteLogExportEstimate;
import com.stugger.logviewer.api.model.RemoteLogExportRequest;
import com.stugger.logviewer.model.DayRange;
import com.stugger.logviewer.model.Scope;
import com.stugger.logviewer.ui.components.LogsDatePickerBehavior;
import com.stugger.logviewer.ui.components.SessionTab;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Controller for the remote log download workflow.
 * <p>
 * Allows users to:
 * <ul>
 *     <li>Browse remote log metadata from the Log Provider API</li>
 *     <li>Select log scopes/types and date ranges</li>
 *     <li>Estimate remote export size and file count</li>
 *     <li>Download ZIP exports of matching logs</li>
 *     <li>Extract downloaded logs into the local viewer workspace</li>
 * </ul>
 * <p>
 * All remote operations are performed asynchronously to avoid blocking the
 * JavaFX application thread.
 *
 * @author Jake
 * @since May 6th, 2026
 */
public class DownloadLogsController {

    private MainController mainController;
    private Stage stage;

    @FXML private DatePicker from_date;
    @FXML private ComboBox<DayRange> include_days;
    @FXML private TreeView<String> types_tree;
    @FXML private ProgressBar download_progress;
    @FXML private Button download_button, cancel_button;

    private CheckBoxTreeItem<String> playerTypesTree;
    private CheckBoxTreeItem<String> globalTypesTree;

    private boolean working;

    /* -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
    |
    |------ Setup & State
    |
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------*/

    public void prepare(MainController mainController, Stage stage, List<String> globalTypes, List<String> playerTypes) {
        this.mainController = mainController;
        this.stage = stage;
        stage.setOnCloseRequest(event -> {
            if (working) {
                event.consume();
                return;
            }
            mainController.setStatus("Remote log download cancelled");
        });
        setupTimeOptions();
        setupTree(globalTypes, playerTypes);
    }

    private void setupTree(List<String> globalTypes, List<String> playerTypes) {
        CheckBoxTreeItem<String> root = new CheckBoxTreeItem<>();
        root.setExpanded(true);
        types_tree.setRoot(root);
        types_tree.setShowRoot(false);
        types_tree.setCellFactory(tv -> new CheckBoxTreeCell<>());
        //add player log types
        playerTypesTree = new CheckBoxTreeItem<>(Scope.PLAYER.folderName);
        playerTypesTree.setExpanded(true);
        for (String type : playerTypes) {
            playerTypesTree.getChildren().add(new CheckBoxTreeItem<>(type));
        }
        root.getChildren().add(playerTypesTree);
        //add global log types
        globalTypesTree = new CheckBoxTreeItem<>(Scope.GLOBAL.folderName);
        globalTypesTree.setExpanded(true);
        for (String type : globalTypes) {
            globalTypesTree.getChildren().add(new CheckBoxTreeItem<>(type));
        }
        root.getChildren().add(globalTypesTree);
    }

    private void setupTimeOptions() {
        // defaults
        include_days.getItems().setAll(DayRange.values());
        include_days.getSelectionModel().select(DayRange.ZERO_DAYS);
        from_date.setValue(LocalDate.now());
        LogsDatePickerBehavior.apply(from_date, null);
    }

    private void setWorking(boolean working) {
        this.working = working;
        from_date.setDisable(working);
        include_days.setDisable(working);
        types_tree.setDisable(working);
        download_button.setDisable(working);
        cancel_button.setDisable(working);
        download_progress.setPrefHeight(working ? 18 : 0);
    }

    /* -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
    |
    |------ Interaction Hooks
    |
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------*/

    @FXML
    public void on_click_download() {
        RemoteLogExportRequest request = buildRequest();
        if (request == null) {
            return;
        }

        setWorking(true);
        mainController.setStatus("Estimating remote log download...");

        Task<RemoteLogExportEstimate> task = new Task<>() {
            @Override
            protected RemoteLogExportEstimate call() throws Exception {
                LogProviderApiClient client = new LogProviderApiClient();
                return client.estimateExport(request);
            }
        };

        task.setOnSucceeded(event -> {
            setWorking(false);

            RemoteLogExportEstimate estimate = task.getValue();

            if (estimate.fileCount() <= 0) {
                AlertManager.show(stage, Alert.AlertType.INFORMATION,
                        "No Logs Found",
                        "No matching log files were found.",
                        "Try changing the date range or selected log types.");
                mainController.setStatus("No remote logs found");
                return;
            }

            ButtonType downloadButton = new ButtonType("Download", ButtonBar.ButtonData.OK_DONE);
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Estimated size: " + formatBytes(estimate.totalBytes()), downloadButton, ButtonType.CANCEL);
            confirm.initOwner(stage);
            confirm.setTitle("Confirm Log Download");
            confirm.setHeaderText("Download " + estimate.fileCount() + " log " + (estimate.fileCount() == 1 ? "file" : "files") + "?");
            Stage window = (Stage) confirm.getDialogPane().getScene().getWindow();
            window.getIcons().addAll(MainApp.getStage().getIcons());

            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != downloadButton) {
                mainController.setStatus("Remote log download cancelled");
                return;
            }

            startDownloadTask(request);
        });

        task.setOnFailed(event -> {
            setWorking(false);
            mainController.setStatus("Failed to estimate remote log download");
            AlertManager.notifyException(task.getException());
        });

        Thread thread = new Thread(task, "remote-log-estimate");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void on_click_cancel() {
        mainController.setStatus("Remote log download cancelled");
        stage.close();
    }

    /* -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
    |
    |------ API Integration
    |
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------*/

    private RemoteLogExportRequest buildRequest() {
        List<String> playerTypes = new ArrayList<>();
        List<String> globalTypes = new ArrayList<>();

        collectSelectedLeafValues(playerTypesTree, playerTypes);
        collectSelectedLeafValues(globalTypesTree, globalTypes);

        if (playerTypes.isEmpty() && globalTypes.isEmpty()) {
            AlertManager.show(stage, Alert.AlertType.ERROR,
                    "No Log Types Selected",
                    "No log types were selected.",
                    "Select at least one log type to download.");
            return null;
        }

        if (from_date.getValue() == null || include_days.getSelectionModel().getSelectedItem() == null) {
            AlertManager.show(stage, Alert.AlertType.ERROR,
                    "Invalid Date Range",
                    "Missing date range.",
                    "Select a date and included day range.");
            return null;
        }

        LocalDate to = from_date.getValue();
        LocalDate from = to.minusDays(include_days.getSelectionModel().getSelectedItem().days);

        return new RemoteLogExportRequest(
                from.toString(),
                to.toString(),
                globalTypes,
                playerTypes,
                MainApp.getSettings().getLogFileNameFormat(),
                MainApp.getSettings().getLogFileNameExtension()
        );
    }

    private void collectSelectedLeafValues(CheckBoxTreeItem<String> item, List<String> selected) {
        if (item == null) {
            return;
        }
        if (item.getChildren().isEmpty()) {
            if (item.isSelected()) {
                selected.add(item.getValue());
            }
            return;
        }
        for (TreeItem<String> child : item.getChildren()) {
            collectSelectedLeafValues((CheckBoxTreeItem<String>) child, selected);
        }
    }

    private void startDownloadTask(RemoteLogExportRequest request) {
        setWorking(true);
        mainController.setStatus("Downloading remote logs...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                LogProviderApiClient client = new LogProviderApiClient();
                Path zipPath = Files.createTempFile("remote-logs-", ".zip");
                try {
                    client.downloadExport(request, zipPath);
                    updateMessage("Extracting remote logs...");
                    unzipToDirectory(zipPath, MainApp.getRootDirectory().toPath());
                } finally {
                    Files.deleteIfExists(zipPath);
                }
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            setWorking(false);
            mainController.setStatus("Remote logs downloaded");
            MainApp.reloadCategories();
            if (mainController.getTabs() != null) {
                for (Tab tab : mainController.getTabs()) {
                    if (tab instanceof SessionTab sessionTab) {
                        sessionTab.getController().reloadTree();
                    }
                }
            }
            stage.close();
        });

        task.setOnFailed(event -> {
            setWorking(false);
            mainController.setStatus("Failed to download remote logs");
            AlertManager.notifyException(task.getException());
        });

        Thread thread = new Thread(task, "remote-log-download");
        thread.setDaemon(true);
        thread.start();
    }

    private static void unzipToDirectory(Path zipPath, Path targetDir) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path outputPath = targetDir.resolve(entry.getName()).normalize();
                if (!outputPath.startsWith(targetDir.normalize())) {
                    throw new IOException("Blocked unsafe zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(zip, outputPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.US, "%.1f MB", mb);
        }
        double gb = mb / 1024.0;
        return String.format(Locale.US, "%.2f GB", gb);
    }

}
