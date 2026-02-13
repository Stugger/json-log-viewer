package com.stugger.logviewer.ui.components;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;

/**
 * UI component representing a single labeled field in the Details panel.
 * <p>
 * Displays a label and a selectable value area, with automatic sizing behavior to
 * keep dense details readable without wasting vertical space.
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
        label.setMinWidth(66);
        label.setPrefWidth(66);
        label.setMaxWidth(66);
        label.setWrapText(true);
        getChildren().add(label);
        HBox.setMargin(label, new Insets(3, 0, 0, 0));
        HBox.setHgrow(label, Priority.NEVER);
        //value
        if (valueString == null) {
            label.setOpacity(0.7);
            Label valueLabel = new Label("—");
            valueLabel.setMinHeight(26);
            valueLabel.setOpacity(0.7);
            HBox.setMargin(valueLabel, new Insets(-1, 0, 0, 8));
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
