package com.stugger.logviewer.ui.components;

import com.stugger.logviewer.model.Username;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;

import java.util.function.Consumer;

/**
 * UI row representing a selectable audit target in the left-hand "players" panel.
 * <p>
 * V1 supports two row modes:
 * <ul>
 *   <li>"ALL PLAYERS" (radio button) which switches auditing into an all-players scan mode.</li>
 *   <li>A specific username (checkbox) with a delete button to remove the row from the audit list.</li>
 * </ul>
 * The row reports user actions via callbacks rather than directly mutating controller state.
 * {@link #setIgnored(boolean)} is used when "ALL PLAYERS" is enabled to disable individual player checkboxes
 * without removing them.
 *
 * @author Jake
 * @since February 1, 2026
 */
public class PlayerSelectionRow extends HBox {

    private final Username username;

    private boolean selected;
    private ButtonBase selectedToggle;

    private final Consumer<PlayerSelectionRow> onToggled;
    private final Consumer<PlayerSelectionRow> onDeleted;

    /**
     * Constructor for the "ALL PLAYERS" default row.
     */
    public PlayerSelectionRow(Consumer<PlayerSelectionRow> onToggled) {
        this(null, false, onToggled, null);
    }

    /**
     * Constructor for an added players row.
     */
    public PlayerSelectionRow(Username username, boolean selected, Consumer<PlayerSelectionRow> onToggled, Consumer<PlayerSelectionRow> onDeleted) {
        this.username = username;
        this.selected = selected;
        this.onToggled = onToggled;
        this.onDeleted = onDeleted;
        setMaxWidth(Double.MAX_VALUE);
        setup();
    }

    private void setup() {
        boolean allPlayers = username == null;
        //toggle + name
        if (allPlayers) {
            selectedToggle = new RadioButton("ALL PLAYERS");
            ((RadioButton)selectedToggle).setSelected(selected);
        } else {
           selectedToggle = new CheckBox(username.withSpaces());
           ((CheckBox)selectedToggle).setSelected(selected);
        }
        selectedToggle.setFont(new Font(12));
        selectedToggle.getStyleClass().add("player-toggle");
        getChildren().add(selectedToggle);
        HBox.setMargin(selectedToggle, new Insets(allPlayers ? 5 : 2, 0, 0, 6));
        selectedToggle.setOnAction(e -> {
            selected = allPlayers ? ((RadioButton)selectedToggle).isSelected() : ((CheckBox)selectedToggle).isSelected();
            onToggled.accept(this);
        });
        //delete button
        if (!allPlayers) {
            //line between name and button
            Separator leader = new Separator(Orientation.HORIZONTAL);
            leader.setMinHeight(18);
            leader.setPadding(new Insets(5, 0, 0, 0));
            leader.setValignment(VPos.CENTER);
            leader.getStyleClass().add("leader-line");
            leader.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(leader, Priority.ALWAYS);
            getChildren().add(leader);
            //button
            Button deleteButton = new Button("X");
            deleteButton.getStyleClass().add("clear-button");
            deleteButton.setFont(Font.font(11));
            deleteButton.setPadding(new Insets(0, 6, 0, 6)); // top/right/bottom/left
            deleteButton.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            deleteButton.setPrefHeight(16); // pick what matches your row
            deleteButton.setFocusTraversable(false);
            getChildren().add(deleteButton);
            HBox.setMargin(deleteButton, new Insets(4, 6, 0, 0));
            deleteButton.setOnAction(e -> {
                onDeleted.accept(this);
            });
        } else {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            getChildren().add(spacer);
        }
    }

    public void setIgnored(boolean ignored) {
        if (selectedToggle instanceof CheckBox) { //all players toggle cannot be disabled (not a checkbox)
            selectedToggle.setDisable(ignored);
        }
    }

    public boolean isSelected() {
        return selected;
    }

    public Username getUsername() {
        return username;
    }
}
