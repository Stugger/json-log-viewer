package com.stugger.logviewer.ui;

import com.stugger.logviewer.AppPaths;
import com.stugger.logviewer.MainApp;
import com.stugger.logviewer.Settings;
import com.stugger.logviewer.model.DayRange;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Controller for the Settings dialog/window.
 * <p>
 * Allows configuration of logs root directory, schema directory, and other persisted
 * options, and triggers reloads where appropriate.
 *
 * @author Jake
 * @since January 27, 2026
 */
public class SettingsController {

    private Stage stage;

    @FXML private TextField game_name, default_root, schemas_directory, log_file_name_format, log_file_name_extension, log_provider_api_url, log_provider_api_key;
    @FXML private ComboBox<DayRange> default_time;
    @FXML private CheckBox new_session_on_launch;

    private boolean dirty;

    public void prepare(Stage stage) {
        this.stage  = stage;

        Settings settings = MainApp.getSettings();

        game_name.setText(settings.getGameName());
        default_root.setText(settings.getDefaultRootDirectory());
        schemas_directory.setText(settings.getSchemasDirectory());
        log_file_name_format.setText(settings.getLogFileNameFormat());
        log_file_name_extension.setText(settings.getLogFileNameExtension());

        default_time.getItems().setAll(DayRange.values());
        default_time.getSelectionModel().select(settings.getDefaultIncludedDays());

        new_session_on_launch.setDisable(settings.getDefaultRootDirectory() == null || settings.getDefaultRootDirectory().isBlank());
        new_session_on_launch.setSelected(settings.isOpenNewSessionOnLaunch());

        log_provider_api_url.setText(settings.getLogProviderApiUrl());
        log_provider_api_key.setText(settings.getLogProviderApiKey());

        Runnable markDirty = () -> dirty = true;

        game_name.textProperty().addListener((o,a,b) -> markDirty.run());
        log_file_name_format.textProperty().addListener((o,a,b) -> markDirty.run());
        log_file_name_extension.textProperty().addListener((o,a,b) -> markDirty.run());
        default_time.valueProperty().addListener((o,a,b) -> markDirty.run());
        new_session_on_launch.selectedProperty().addListener((o,a,b) -> markDirty.run());
        log_provider_api_url.textProperty().addListener((o,a,b) -> markDirty.run());
        log_provider_api_key.textProperty().addListener((o,a,b) -> markDirty.run());

        stage.setOnCloseRequest(evt -> {
            if (!dirty) {
                return;
            }
            try {
                DateTimeFormatter.ofPattern(log_file_name_format.getText()); //will throw exception if pattern is invalid
            } catch (IllegalArgumentException e) {
                evt.consume();
                AlertManager.show(stage, Alert.AlertType.ERROR,
                        "Invalid date pattern",
                        "The pattern you set for log file names is not valid.",
                        "Look up java date format patterns for more info.");
                return;
            }
            Optional<ButtonType> result = AlertManager.showAndWaitButtons(stage, Alert.AlertType.CONFIRMATION,
                    "Save changes?",
                    "Do you want to save these settings?",
                    "Some changes may require restarting active sessions.",
                    ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
            if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
                evt.consume();
            } else if (result.get() == ButtonType.YES) {
                settings.setGameName(game_name.getText());
                settings.setDefaultRootDirectory(default_root.getText());
                settings.setSchemasDirectory(schemas_directory.getText());
                settings.setLogFileNameFormat(log_file_name_format.getText());
                settings.setLogFileNameExtension(log_file_name_extension.getText());
                settings.setDefaultIncludedDays(default_time.getSelectionModel().getSelectedItem());
                settings.setOpenNewSessionOnLaunch(new_session_on_launch.isSelected());
                settings.setLogProviderApiUrl(log_provider_api_url.getText());
                settings.setLogProviderApiKey(log_provider_api_key.getText());
                settings.save();
            }
        });
    }

    @FXML
    public void on_choose_root_dir() {
        final DirectoryChooser directoryChooser = new DirectoryChooser();
        final File directory = directoryChooser.showDialog(MainApp.getStage());
        if (directory == null) {
            return;
        }
        if (!MainApp.isValidRootDirectory(directory)) {
            AlertManager.show(Alert.AlertType.ERROR,
                    "Unable to set root directory",
                    "The chosen root directory does not contain any valid folders.",
                    "You must set a root directory that contains at least one scope.");
            return;
        }
        default_root.setText(directory.toString());
        new_session_on_launch.setDisable(false);
        dirty = true;
        stage.toFront();
    }

    @FXML
    public void on_reset_root_dir() {
        if (default_root.getText().equals(AppPaths.LOGS_DEFAULT_DIRECTORY)) {
            return;
        }
        default_root.setText(AppPaths.LOGS_DEFAULT_DIRECTORY);
        dirty = true;
    }

    @FXML
    public void on_choose_schemas_dir() {
        final DirectoryChooser directoryChooser = new DirectoryChooser();
        final File directory = directoryChooser.showDialog(MainApp.getStage());
        if (directory == null) {
            return;
        }
        schemas_directory.setText(directory.toString());
        dirty = true;
        stage.toFront();
    }

    @FXML
    public void on_reset_schemas_dir() {
        if (schemas_directory.getText().equals(AppPaths.SCHEMAS_DEFAULT_DIRECTORY)) {
            return;
        }
        schemas_directory.setText(AppPaths.SCHEMAS_DEFAULT_DIRECTORY);
        dirty = true;
    }
}
