package com.stugger.logviewer.ui;

import com.stugger.logviewer.MainApp;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

/**
 * Convenience helpers for user-facing alerts and exception dialogs.
 * <p>
 * Centralizes consistent alert styling/ownership and supports expandable stack trace
 * displays for debugging failures during IO and parsing.
 */
public class AlertManager {

    public static void show(Alert.AlertType type, String title, String header, String content) {
        show(MainApp.getStage(), type, title, header, content);
    }

    public static void show(Stage stage, Alert.AlertType type, String title, String header, String content) {
        final Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        Stage window = (Stage) alert.getDialogPane().getScene().getWindow();
        window.getIcons().addAll(stage.getIcons());
        alert.initOwner(stage);
        alert.show();
    }

    public static Optional<ButtonType> showAndWait(Alert.AlertType type, String title, String header, String content) {
        return showAndWait(MainApp.getStage(), type, title, header, content);
    }

    public static Optional<ButtonType> showAndWait(Stage stage, Alert.AlertType type, String title, String header, String content) {
        final Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        Stage window = (Stage) alert.getDialogPane().getScene().getWindow();
        window.getIcons().addAll(MainApp.getStage().getIcons());
        alert.initOwner(stage);
        return alert.showAndWait();
    }

    public static Optional<ButtonType> showAndWaitButtons(Alert.AlertType type, String title, String header, String content, ButtonType... buttonTypes) {
        return showAndWaitButtons(MainApp.getStage(), type, title, header, content, buttonTypes);
    }

    public static Optional<ButtonType> showAndWaitButtons(Stage stage, Alert.AlertType type, String title, String header, String content, ButtonType... buttonTypes) {
        Alert alert = new Alert(type, content, buttonTypes);
        alert.setTitle(title);
        alert.setHeaderText(header);
        Stage window = (Stage) alert.getDialogPane().getScene().getWindow();
        window.getIcons().addAll(stage.getIcons());
        alert.initOwner(stage);
        return alert.showAndWait();
    }

    public static void notifyException(Throwable ex) {
        notifyException("Exception", ex);
    }

    public static void notifyException(String title, Throwable ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText("An exception has occurred.");
        alert.setContentText("View the text below for more information.");
        //create expandable Exception
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        String exceptionText = sw.toString();

        Label label = new Label("The exception stacktrace was:");

        TextArea textArea = new TextArea(exceptionText);
        textArea.setEditable(false);
        textArea.setWrapText(true);

        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane expContent = new GridPane();
        expContent.setMaxWidth(Double.MAX_VALUE);
        expContent.add(label, 0, 0);
        expContent.add(textArea, 0, 1);

        //set expandable Exception into the dialog pane
        alert.getDialogPane().setExpandableContent(expContent);
        alert.initOwner(MainApp.getStage());
        alert.showAndWait();
    }
}
