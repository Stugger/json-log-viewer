package com.stugger.logviewer.ui.components;

import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Shared DatePicker behavior used throughout the application.
 * <p>
 * Adds:
 * <ul>
 *     <li>Future date restriction</li>
 *     <li>Automatic typed-date commit behavior</li>
 *     <li>Optional value-change callbacks</li>
 * </ul>
 *
 * @author Jake
 * @since May 6th, 2026
 */
public class LogsDatePickerBehavior {

    /**
     * Applies custom log-viewer DatePicker behavior to the supplied control.
     *
     * @param datePicker target DatePicker
     * @param onValueChanged optional callback invoked when the selected value changes
     */
    public static void apply(DatePicker datePicker, Runnable onValueChanged) {
        //gray out future dates in the popup calendar
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    return;
                }
                boolean future = date.isAfter(LocalDate.now());
                setDisable(future);
                setOpacity(future ? 0.45 : 1.0);
            }
        });

        //reload on change, and if user types a future date, or it gets set programmatically, snap back
        datePicker.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                return;
            }
            LocalDate today = LocalDate.now();
            if (newV.isAfter(today)) {
                datePicker.setValue(today);
            } else if (!Objects.equals(oldV, newV) && onValueChanged != null) {
                onValueChanged.run();
            }
        });

        //when date is typed, auto-commit and then clamp
        datePicker.getEditor().focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                try {
                    datePicker.setValue(datePicker.getConverter().fromString(datePicker.getEditor().getText()));
                } catch (Exception ignored) {
                    //if parse fails, let DatePicker handle it
                }
            }
        });
    }

}
