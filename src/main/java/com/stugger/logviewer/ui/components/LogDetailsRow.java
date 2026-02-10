package com.stugger.logviewer.ui.components;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

/**
 *
 * @author Jake
 * @since February 9, 2026
 */
public class LogDetailsRow extends HBox {

    private final String labelString;
    private final String valueString;

    /**
     * Constructor.
     */
    public LogDetailsRow(String labelString, String valueString) {
        this.labelString = labelString;
        this.valueString = valueString;
        setMaxWidth(Double.MAX_VALUE);
        setup();
    }

    private void setup() {
        setSpacing(10);
        setPadding(new Insets(0, 8, 0, 8));
        //label
        Label label = new Label(labelString);
        label.getStyleClass().add("log-detail-label");
        label.setFont(new Font(12));
        label.setMinWidth(66);
        label.setPrefWidth(66);
        label.setMaxWidth(66);
        label.setWrapText(true);
        getChildren().add(label);
        HBox.setMargin(label, new Insets(3, 0, 0, 0));
        HBox.setHgrow(label, Priority.NEVER);
        //value
        if (valueString == null) {
            Label valueLabel = new Label("—");
            valueLabel.setMinHeight(26);
            HBox.setMargin(valueLabel, new Insets(-1, 0, 0, 6));
            getChildren().add(valueLabel);
        } else {
            TextArea textArea = new TextArea(valueString);
            textArea.getStyleClass().add("log-detail-value");
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setMinHeight(0);
            textArea.setMaxHeight(320);
            HBox.setHgrow(textArea, Priority.ALWAYS);
            //mirror Text node for measurement
            Text text = new Text();
            text.setFont(textArea.getFont());
            text.textProperty().bind(textArea.textProperty());
            text.wrappingWidthProperty().bind(textArea.widthProperty().subtract(18));
            textArea.prefHeightProperty().bind(Bindings.createDoubleBinding(() -> text.getLayoutBounds().getHeight() + 12, text.layoutBoundsProperty(), textArea.insetsProperty()));
            getChildren().add(textArea);
        }
    }
}
