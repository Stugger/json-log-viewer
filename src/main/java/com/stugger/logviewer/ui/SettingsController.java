package com.stugger.logviewer.ui;

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

public class SettingsController {

    private Stage stage;

    @FXML private TextField game_name, default_root, log_file_name_format, log_file_name_extension;
    @FXML private ComboBox<DayRange> default_time;
    @FXML private CheckBox new_session_on_launch;

    private boolean dirty;

    public void prepare(Stage stage) {
        this.stage  = stage;

        Settings settings = MainApp.getSettings();

        game_name.setText(settings.getGameName());
        default_root.setText(settings.getDefaultRootDirectory());
        log_file_name_format.setText(settings.getLogFileNameFormat());
        log_file_name_extension.setText(settings.getLogFileNameExtension());

        default_time.getItems().setAll(DayRange.values());
        default_time.getSelectionModel().select(settings.getDefaultIncludedDays());

        new_session_on_launch.setDisable(settings.getDefaultRootDirectory() == null);
        new_session_on_launch.setSelected(settings.isOpenNewSessionOnLaunch());

        Runnable markDirty = () -> dirty = true;

        game_name.textProperty().addListener((o,a,b) -> markDirty.run());
        log_file_name_format.textProperty().addListener((o,a,b) -> markDirty.run());
        log_file_name_extension.textProperty().addListener((o,a,b) -> markDirty.run());
        default_time.valueProperty().addListener((o,a,b) -> markDirty.run());
        new_session_on_launch.selectedProperty().addListener((o,a,b) -> markDirty.run());

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
                settings.setLogFileNameFormat(log_file_name_format.getText());
                settings.setLogFileNameExtension(log_file_name_extension.getText());
                settings.setDefaultIncludedDays(default_time.getSelectionModel().getSelectedItem());
                settings.setOpenNewSessionOnLaunch(new_session_on_launch.isSelected());
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
    public void on_clear_root_dir() {
        if (default_root.getText().isEmpty()) {
            return;
        }
        default_root.setText("");
        new_session_on_launch.setDisable(true);
        dirty = true;
    }
}
