package com.stugger.logviewer.ui;

import com.google.gson.*;
import com.stugger.logviewer.MainApp;
import com.stugger.logviewer.data.JsonlLogSource;
import com.stugger.logviewer.model.*;
import com.stugger.logviewer.schema.render.DetailsValueFormat;
import com.stugger.logviewer.schema.render.JsonValue;
import com.stugger.logviewer.schema.render.ValueFormat;
import com.stugger.logviewer.schema.model.SchemaDefinition;
import com.stugger.logviewer.schema.model.SchemaFieldDefinition;
import com.stugger.logviewer.ui.components.LogDetailsRow;
import com.stugger.logviewer.ui.components.PlayerSelectionRow;
import com.stugger.logviewer.ui.components.SessionTab;
import com.stugger.logviewer.ui.model.ToggleNode;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * Controller for a single audit session tab.
 * <p>
 * Owns the end-to-end UI state:
 * player selection, scope/type toggle tree, time window selection, and free-text searching.
 * Loads log records from disk via {@link JsonlLogSource} on scope/player/date changes, then
 * applies lightweight in-memory filtering (tree + search) and table sorting.
 * <p>
 * Design notes:
 * <ul>
 *   <li>Disk reloads are intentionally limited to changes that affect the underlying file set (scope/player/date).</li>
 *   <li>Type toggles and search primarily operate on already-loaded events for responsiveness.</li>
 *   <li>Tree selection handling is debounced to avoid repeated reloads when users click rapidly.</li>
 * </ul>
 *
 * @author Jake
 * @since January 21, 2026
 */
public class SessionController {

    private MainController main;

    public SessionTab tab;

    // Toolbar fields
    @FXML private DatePicker audit_from;
    @FXML private ComboBox<DayRange> audit_include_days;
    @FXML private TextField search_field;

    // Left
    @FXML private VBox player_selection_box;
    @FXML private TreeView<ToggleNode> toggle_tree;

    // Center table
    @FXML private TableView<LogRecord> logs_table;
    @FXML private TableColumn<LogRecord, Long> time_column;
    @FXML private TableColumn<LogRecord, String> scope_column;
    @FXML private TableColumn<LogRecord, String> type_column;
    @FXML private TableColumn<LogRecord, String> summary_column;

    // Right
    @FXML private VBox details_box;
    @FXML private TextArea raw_json;

    private final ObservableList<LogRecord> allEvents = FXCollections.observableArrayList();
    private FilteredList<LogRecord> filteredEvents;

    private EnumSet<Scope> lastLoadedScopes = EnumSet.noneOf(Scope.class);
    private boolean suppressTreeEvents;
    private PauseTransition treeDebounce;

    private boolean allPlayers;
    private final Set<PlayerSelectionRow> selectedPlayers = new HashSet<>();
    private final BooleanProperty playersSelectionValid = new SimpleBooleanProperty(false);

    private static final DateTimeFormatter TIME_TABLE_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd '-' HH:mm:ss").withLocale(Locale.US);
    private static final DateTimeFormatter TIME_DETAIL_FMT = DateTimeFormatter.ofPattern("h:mm:ss a 'on' MMMM d',' yyyy").withLocale(Locale.US);

    public void setTab(SessionTab tab) {
        this.tab = tab;
    }

    @FXML
    private void initialize() {
        setupPlayerSelection();
        setupToggleTree();
        setupTable();
        setupTableSelection();
        setupTimeOptions();
        setupSearchFiltering();
    }

    public void open(MainController main) {
        this.main = main;
        reloadFromDisk();
    }

    /* -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
    |
    |------ Player Selection / Auditing
    |
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------*/

    private void setupPlayerSelection() {
        player_selection_box.getChildren().clear();
        //"all players"
        player_selection_box.getChildren().add(new PlayerSelectionRow(row -> {
            allPlayers = row.isSelected();
            for (Node node : player_selection_box.getChildren()) {
                if (node instanceof PlayerSelectionRow psr) {
                    psr.setIgnored(allPlayers);
                }
            }
            updatePlayersSelectionValid();
            reloadFromDisk();
        }));

        //players separator
        Separator sep = new Separator(Orientation.HORIZONTAL);
        sep.setMinHeight(8);
        sep.setValignment(VPos.BOTTOM);
        sep.setOpacity(0.5);
        player_selection_box.getChildren().add(sep);

        //-> player rows will be inserted here <-

        //footer separator
        sep = new Separator(Orientation.HORIZONTAL);
        sep.setMinHeight(8);
        sep.setValignment(VPos.BOTTOM);
        sep.setOpacity(0.5);
        player_selection_box.getChildren().add(sep);

        //add player button (full width)
        Button addPlayer = new Button("+ Add Player");
        addPlayer.setMaxWidth(Double.MAX_VALUE);
        addPlayer.setPrefHeight(24);
        VBox.setMargin(addPlayer, new Insets(4, 6, 6, 6));
        addPlayer.setOnAction(e -> handleAddPlayerClicked());

        player_selection_box.getChildren().add(addPlayer);
    }

    public void handleAddPlayerClicked() {
        Path playersDir = MainApp.getRootDirectory().toPath().resolve("players");

        Dialog<Username> dialog = new Dialog<>();
        dialog.setTitle("Add Player");
        dialog.setHeaderText("Enter a username to add to the audit list.");
        dialog.initModality(Modality.WINDOW_MODAL);
        Stage window = (Stage) dialog.getDialogPane().getScene().getWindow();
        window.getIcons().addAll(MainApp.getStage().getIcons());

        ButtonType addType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addType, ButtonType.CANCEL);

        TextField usernameField = new TextField();
        usernameField.setMinWidth(196);
        usernameField.setMaxWidth(196);
        usernameField.setPromptText("Enter a player's username...");

        Label statusText = new Label("");
        statusText.getStyleClass().add("note");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Username:"), usernameField);
        grid.add(statusText, 1, 1);

        dialog.getDialogPane().setContent(grid);

        Node addButton = dialog.getDialogPane().lookupButton(addType);
        addButton.setDisable(true);

        //change listener to normalize & validate
        usernameField.textProperty().addListener((obs, o, n) -> {
            //clear UI
            usernameField.getStyleClass().removeAll("valid", "invalid");
            if (usernameField.getText().isBlank()) {
                statusText.setText("");
                addButton.setDisable(true);
                return;
            }
            //update UI
            Username username = new Username(usernameField.getText());
            if (!username.withSpaces().matches("[a-z0-9_ ]+")) {
                usernameField.getStyleClass().add("invalid");
                statusText.setText("Invalid username format.");
                addButton.setDisable(true);
                return;
            }
            if (Files.exists(playersDir.resolve(username.withoutSpaces()))) {
                if (selectedPlayers.stream().anyMatch(r -> username.equals(r.getUsername()))) {
                    usernameField.getStyleClass().add("invalid");
                    statusText.setText("That player is already in the list.");
                    addButton.setDisable(true);
                } else {
                    usernameField.getStyleClass().add("valid");
                    statusText.setText("Player found.");
                    addButton.setDisable(false);
                }
            } else {
                usernameField.getStyleClass().add("invalid");
                statusText.setText("No folder found for that username.");
                addButton.setDisable(true);
            }
        });

        //focus field automatically
        Platform.runLater(usernameField::requestFocus);

        dialog.setResultConverter(btn -> {
            if (btn == addType) {
                return new Username(usernameField.getText());
            }
            return null;
        });

        dialog.initOwner(MainApp.getStage());
        dialog.showAndWait().ifPresent(username -> {
            if (username.withoutSpaces().isEmpty()) {
                return;
            }
            //sanity check existence again
            if (!Files.exists(playersDir.resolve(username.withoutSpaces()))) {
                AlertManager.showAndWait(Alert.AlertType.ERROR,
                        "Player Not Found",
                        "No log folder exists for: " + username.withoutSpaces(),
                        "Double-check spelling, or confirm the player has logged in at least once.");
                return;
            }
            //add the row to the list
            PlayerSelectionRow selectionRow = new PlayerSelectionRow(username, true, row -> {
                updatePlayersSelectionValid();
                if (!allPlayers) {
                    reloadFromDisk();
                }
            }, row -> {
                selectedPlayers.remove(row);
                player_selection_box.getChildren().remove(row);
                updatePlayersSelectionValid();
                if (!allPlayers) {
                    reloadFromDisk();
                }
            });
            if (allPlayers) {
                selectionRow.setIgnored(true);
            }
            selectedPlayers.add(selectionRow);
            player_selection_box.getChildren().add(2, selectionRow); //add to top
            updatePlayersSelectionValid();
            if (!allPlayers) {
                reloadFromDisk();
            }
        });
    }

    private void updatePlayersSelectionValid() {
        playersSelectionValid.set(allPlayers || selectedPlayers.stream().anyMatch(PlayerSelectionRow::isSelected));
    }

    /* -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
    |
    |------ Log Toggle Tree
    |
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------*/

    private void setupToggleTree() {
        suppressTreeEvents = true;
        CheckBoxTreeItem<ToggleNode> root = new CheckBoxTreeItem<>(new ToggleNode("root", ToggleNode.Kind.ROOT, null));
        root.setExpanded(true);
        root.getChildren().add(scopeTree(Scope.PLAYER, MainApp.PLAYER_TREE_FOLDER_NAMES));
        root.getChildren().add(scopeTree(Scope.GLOBAL, MainApp.GLOBAL_TREE_FOLDER_NAMES));
        toggle_tree.setRoot(root);
        toggle_tree.setShowRoot(false);
        toggle_tree.setCellFactory(tv -> {
            CheckBoxTreeCell<ToggleNode> cell = new CheckBoxTreeCell<>();
            playersSelectionValid.addListener((obs, o, n) -> cell.updateItem(cell.getItem(), cell.isEmpty()));
            cell.emptyProperty().addListener((obs, o, n) -> cell.updateItem(cell.getItem(), n));
            cell.itemProperty().addListener((obs, o, n) -> {
                cell.updateItem(n, cell.isEmpty());
                cell.getStyleClass().removeAll("kind-scope","kind-category","kind-leaf");
                if (n != null) {
                    cell.getStyleClass().add("kind-" + n.kind.name().toLowerCase(Locale.ROOT));
                }
            });
            //hook disable property on player scope so the whole player tree is disabled when invalid
            cell.disableProperty().bind(Bindings.createBooleanBinding(() -> {
                ToggleNode tn = cell.getItem();
                return tn != null && tn.scope == Scope.PLAYER && !playersSelectionValid.get();
            }, cell.itemProperty(), playersSelectionValid));
            //prevent enabling entire subtrees when selecting a scope/category (they will only be used to deselect all)
            cell.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                if (cell.isEmpty()) {
                    return;
                }
                TreeItem<ToggleNode> ti = cell.getTreeItem();
                if (!(ti instanceof CheckBoxTreeItem<ToggleNode> parent)) {
                    return;
                }
                ToggleNode n = parent.getValue();
                if (n == null) {
                    return;
                }
                //only for scope/category (not leaves)
                if (n.kind != ToggleNode.Kind.SCOPE && n.kind != ToggleNode.Kind.CATEGORY) {
                    return;
                }
                //only register on checkbox click
                Node pickedNode = e.getPickResult().getIntersectedNode();
                if (!pickedNode.getStyleClass().contains("mark") && !pickedNode.getStyleClass().contains("box")) {
                    return;
                }
                //always clear all descendants
                suppressTreeEvents = true;
                try {
                    clearDescendants(parent);
                    //make sure the parent isn't left indeterminate visually
                    parent.setIndeterminate(false);
                    parent.setSelected(false);
                } finally {
                    suppressTreeEvents = false;
                }
                //debounce -> reload/refilter logic and stop default toggle behaviour by consuming
                scheduleTreeChanged();
                e.consume();
            });
            return cell;
        });
        //debounced change handling
        treeDebounce = new PauseTransition(Duration.millis(150));
        treeDebounce.setOnFinished(e -> onTreeSelectionChanged());
        suppressTreeEvents = false;
    }

    private CheckBoxTreeItem<ToggleNode> scopeTree(Scope scope, Map<String, Set<String>> names) {
        CheckBoxTreeItem<ToggleNode> scopeTree = new CheckBoxTreeItem<>(new ToggleNode(scope.folderName, ToggleNode.Kind.SCOPE, scope));
        scopeTree.setExpanded(true);

        for (Map.Entry<String, Set<String>> entry : names.entrySet()) {
            String categoryName = entry.getKey();
            Set<String> types = entry.getValue();
            CheckBoxTreeItem<ToggleNode> cat;
            if (types.isEmpty()) { //having no sub-folders means it's a type
                cat = new CheckBoxTreeItem<>(new ToggleNode(categoryName, ToggleNode.Kind.LEAF, scope, categoryName));
            } else {
                cat = new CheckBoxTreeItem<>(new ToggleNode(categoryName, ToggleNode.Kind.CATEGORY, scope));
                cat.setExpanded(true);
                for (String type : types) {
                    CheckBoxTreeItem<ToggleNode> child = new CheckBoxTreeItem<>(new ToggleNode(type, ToggleNode.Kind.LEAF, scope, categoryName + "/" + type));
                    cat.getChildren().add(child);
                }
            }
            scopeTree.getChildren().add(cat);
        }
        wireTreeListeners(scopeTree);
        return scopeTree;
    }

    private void wireTreeListeners(CheckBoxTreeItem<ToggleNode> item) {
        //hard rule for scope nodes:
        ToggleNode node = item.getValue();
        if (node.kind == ToggleNode.Kind.SCOPE) {
            item.selectedProperty().addListener((obs, oldV, newV) -> {
                if (suppressTreeEvents) {
                    return;
                }
                if (!newV) {
                    suppressTreeEvents = true;
                    try {
                        //force scope itself to be fully off
                        item.setIndeterminate(false);
                        //force everything under it off too
                        clearDescendants(item);
                    } finally {
                        suppressTreeEvents = false;
                    }
                    //scope changed -> reload (or clear)
                    scheduleTreeChanged();
                } else {
                    //turning a scope on: let JavaFX cascade naturally
                    scheduleTreeChanged();
                }
            });
        } else { //normal nodes just debounce changes
            item.selectedProperty().addListener((obs, oldV, newV) -> scheduleTreeChanged());
            item.indeterminateProperty().addListener((obs, oldV, newV) -> scheduleTreeChanged());
        }
        for (TreeItem<ToggleNode> child : item.getChildren()) {
            wireTreeListeners((CheckBoxTreeItem<ToggleNode>) child);
        }
    }

    private void clearDescendants(CheckBoxTreeItem<?> parent) {
        for (TreeItem<?> child : parent.getChildren()) {
            CheckBoxTreeItem<?> cbi = (CheckBoxTreeItem<?>) child;
            //clear child state
            cbi.setIndeterminate(false);
            cbi.setSelected(false);
            //recurse
            clearDescendants(cbi);
        }
    }

    private void scheduleTreeChanged() {
        if (!suppressTreeEvents) {
            treeDebounce.playFromStart();
        }
    }

    private void onTreeSelectionChanged() {
        //if scope enablement changed, we must reload from disk
        EnumSet<Scope> now = getEnabledScopesFromTree();
        boolean scopesChanged = !now.equals(lastLoadedScopes);
        if (scopesChanged) {
            reloadFromDisk();
            return;
        }
        //otherwise type toggles can just filter what's already loaded
        refilter();
    }

    private EnumSet<Scope> getEnabledScopesFromTree() {
        EnumSet<Scope> scopes = EnumSet.noneOf(Scope.class);
        for (TreeItem<ToggleNode> top : toggle_tree.getRoot().getChildren()) {
            CheckBoxTreeItem<ToggleNode> cbi = (CheckBoxTreeItem<ToggleNode>) top;
            ToggleNode n = cbi.getValue();
            if (n.kind == ToggleNode.Kind.SCOPE && n.scope != null) {
                if (cbi.isSelected() || cbi.isIndeterminate()) {
                    scopes.add(n.scope);
                }
            }
        }
        return scopes;
    }

    /* -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
    |
    |------ Logs Table
    |
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------*/

    private void setupTable() {
        //time
        time_column.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().timeMs()));
        time_column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Long timeMs, boolean empty) {
                super.updateItem(timeMs, empty);
                if (empty || timeMs == null) {
                    setText(null);
                } else {
                    setText(TIME_TABLE_FMT.format(Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault()).toLocalDateTime()));
                }
                setAlignment(Pos.CENTER_LEFT);
            }
        });
        time_column.setSortType(TableColumn.SortType.DESCENDING);

        //scope
        scope_column.setCellValueFactory(cell -> {
            LogRecord source = cell.getValue();
            if (source.scope() == Scope.PLAYER) {
                return new ReadOnlyObjectWrapper<>(source.player());
            }
            return new ReadOnlyObjectWrapper<>(source.scope().folderName);
        });
        scope_column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty) {
                    if (getTableView().getItems().get(getIndex()).scope() == Scope.GLOBAL) {
                        setOpacity(0.5);
                    } else {
                        setOpacity(1);
                    }
                }
                setAlignment(Pos.CENTER_LEFT);
            }
        });

        //type
        type_column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().typeId()));
        type_column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item == null ? "" : item);
                }
                setAlignment(Pos.CENTER_LEFT);
            }
        });

        //summary
        summary_column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().summary()));
        summary_column.setCellFactory(tc -> {
            TableCell<LogRecord, String> cell = new TableCell<>();
            Text text = new Text();
            text.fillProperty().bind(cell.textFillProperty());
            text.setFontSmoothingType(FontSmoothingType.LCD);
            cell.setGraphic(text);
            cell.setPrefHeight(Control.USE_COMPUTED_SIZE);
            text.wrappingWidthProperty().bind(summary_column.widthProperty().subtract(16));
            StringBinding binding = Bindings.when(cell.itemProperty().isNull()).then("").otherwise(cell.itemProperty().asString());
            text.textProperty().bind(binding);
            return cell;
        });
        logs_table.setFixedCellSize(-1); //allow variable height rows
        logs_table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        filteredEvents = new FilteredList<>(allEvents, e -> true);
        SortedList<LogRecord> sortedEvents = new SortedList<>(filteredEvents);
        sortedEvents.comparatorProperty().bind(logs_table.comparatorProperty());

        logs_table.setItems(sortedEvents);
        logs_table.getSortOrder().add(time_column);
        logs_table.sort();
    }

    private void setupTableSelection() {
        raw_json.setText("Select a record...");
        details_box.getChildren().setAll(new Label("Select a record..."));
        logs_table.getSelectionModel().selectedItemProperty().addListener((obs, oldV, ev) -> {
            if (ev == null) {
                raw_json.setText("Select a record...");
                details_box.getChildren().setAll(new Label("Select a record..."));
                return;
            }
            raw_json.setText(ev.rawJson());
            buildLogDetailsBox(ev);
        });
    }

    private void buildLogDetailsBox(LogRecord record) {
        details_box.getChildren().clear();
        JsonObject obj = JsonParser.parseString(record.rawJson()).getAsJsonObject();
        SchemaDefinition schema = MainApp.getSchemaLoader().getSchemaFromJsonObject(obj);
        if (schema == null || schema.details == null || schema.details.isEmpty()) {
            details_box.getChildren().add(new Label("No schema details configured."));
            return;
        }
        Label schemaNameLabel = new Label(schema.displayName);
        schemaNameLabel.getStyleClass().add("log-schema-name");
        details_box.getChildren().add(schemaNameLabel);
        details_box.getChildren().add(new Separator());
        int rowsAdded = buildDetailRows(schema, obj);
        if (rowsAdded == 0) {
            details_box.getChildren().add(new Label("No details available."));
        }
        details_box.getChildren().add(new Separator());
        details_box.getChildren().add(new LogDetailsRow("Time", TIME_DETAIL_FMT.format(Instant.ofEpochMilli(record.timeMs()).atZone(ZoneId.systemDefault()).toLocalDateTime())));
        details_box.getChildren().add(new LogDetailsRow("Path", record.scope().folderName + "/" + (record.scope() == Scope.PLAYER ? record.player() + "/" : "") + record.typeId()));
        details_box.getChildren().add(new LogDetailsRow("Schema", schema.schemaId));
    }

    private int buildDetailRows(SchemaDefinition schema, JsonObject obj) {
        int rowsAdded = 0;
        for (SchemaFieldDefinition f : schema.details) {
            if (f == null || f.label == null || f.label.isBlank()) {
                continue;
            }
            JsonElement el = JsonValue.resolve(obj, f.path);
            boolean missing = false;
            boolean optional = Boolean.TRUE.equals(f.optional);
            if (el == null || el.isJsonNull()) {
                missing = true;
            } else if (el.isJsonArray() && el.getAsJsonArray().isEmpty()) {
                missing = true;
            } else if (el.isJsonPrimitive()) {
                JsonPrimitive p = el.getAsJsonPrimitive();
                String s = el.getAsString();
                if (p.isString()) {
                    missing = s == null || s.isBlank();
                } else if (p.isNumber() && optional) {
                    if (s.indexOf('.') == -1 && s.indexOf('e') == -1 && s.indexOf('E') == -1) {
                        try {
                            missing = Long.parseLong(s) == 0;
                        } catch (NumberFormatException ignored) {
                            //treat as present
                        }
                    }
                }
            }
            if (missing && optional) {
                continue;
            }
            if (missing) {
                details_box.getChildren().add(new LogDetailsRow(f.label, null));
            } else {
                String value;
                if (el.isJsonPrimitive()) {
                    value = ValueFormat.inline(el, f.format);
                } else { //object / array (raw json)
                    value = DetailsValueFormat.complex(el, f.render);
                }
                if (value != null && !value.isBlank()) {
                    if (f.prefix != null) {
                        value = f.prefix + value;
                    }
                    if (f.append != null) {
                        value = value + f.append;
                    }
                }
                details_box.getChildren().add(new LogDetailsRow(f.label, value));
            }
            rowsAdded++;
        }
        return rowsAdded;
    }

    /* -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
    |
    |------ Toolbar (Date of logs / Searching)
    |
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------*/

    private void setupTimeOptions() {
        // defaults
        audit_include_days.getItems().setAll(DayRange.values());
        audit_include_days.getSelectionModel().select(MainApp.getSettings().getDefaultIncludedDays());
        audit_from.setValue(LocalDate.now());
        //grey out future dates in the popup calendar
        audit_from.setDayCellFactory(picker -> new DateCell() {
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
        audit_from.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                return;
            }
            LocalDate today = LocalDate.now();
            if (newV.isAfter(today)) {
                audit_from.setValue(today);
            } else if (!Objects.equals(oldV, newV)) {
                reloadFromDisk();
            }
        });
        //when date is typed, auto-commit and then clamp
        audit_from.getEditor().focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                try {
                    audit_from.setValue(audit_from.getConverter().fromString(audit_from.getEditor().getText()));
                } catch (Exception ignored) {
                    //if parse fails, let DatePicker handle it
                }
            }
        });
        audit_include_days.valueProperty().addListener((obs, oldV, range) -> {
            if (!Objects.equals(oldV, range)) {
                reloadFromDisk();
            }
        });
    }

    private void setupSearchFiltering() {
        search_field.textProperty().addListener((obs, oldV, newV) -> refilter());
    }

    /* -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
    |
    |------ Log Loading / Refiltering
    |
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------*/

    private void refilter() {
        if (filteredEvents == null) return;

        final String search = normalize(search_field.getText());
        filteredEvents.setPredicate(ev -> {
            //tree filters (type + scope)
            if (!isEventAllowedByTree(ev)) return false;
            //search filter: contains
            if (!search.isBlank()) {
                return normalize(ev.summary()).contains(search) || normalize(ev.rawJson()).contains(search);
            }
            return true;
        });

        main.setStatus("Showing " + filteredEvents.size() + " events");
    }

    private boolean isEventAllowedByTree(LogRecord ev) {
        //if tree isn't ready, allow everything
        if (toggle_tree == null || toggle_tree.getRoot() == null) {
            return true;
        }
        return isScopeSelected(ev.scope()) && isTypeSelected(ev.scope(), ev.typeId());
    }

    private boolean isScopeSelected(Scope scope) {
        for (TreeItem<ToggleNode> top : toggle_tree.getRoot().getChildren()) {
            if (top instanceof CheckBoxTreeItem<ToggleNode> cbi) {
                ToggleNode n = cbi.getValue();
                if (n.kind == ToggleNode.Kind.SCOPE && n.scope == scope) {
                    return cbi.isSelected() || cbi.isIndeterminate();
                }
            }
        }
        return true;
    }

    private boolean isTypeSelected(Scope scope, String typeId) {
        for (TreeItem<ToggleNode> top : toggle_tree.getRoot().getChildren()) {
            if (!(top instanceof CheckBoxTreeItem<ToggleNode> cbi)) continue;
            ToggleNode n = cbi.getValue();
            if (n.kind == ToggleNode.Kind.SCOPE && n.scope == scope) {
                return isAnyLeafSelectedByName(cbi, typeId);
            }
        }
        return true;
    }

    private boolean isAnyLeafSelectedByName(CheckBoxTreeItem<ToggleNode> item, String leafName) {
        for (TreeItem<ToggleNode> child : item.getChildren()) {
            CheckBoxTreeItem<ToggleNode> c = (CheckBoxTreeItem<ToggleNode>) child;
            ToggleNode n = c.getValue();
            if (n.kind == ToggleNode.Kind.LEAF && n.typeId.equalsIgnoreCase(leafName)) {
                return c.isSelected() || c.isIndeterminate();
            }
            if (!c.getChildren().isEmpty()) {
                if (isAnyLeafSelectedByName(c, leafName)) return true;
            }
        }
        return false;
    }

    private void reloadFromDisk() {
        main.setStatus("Loading logs...");

        var task = new Task<List<LogRecord>>() {
            @Override
            protected List<LogRecord> call() {
                JsonlLogSource src = new JsonlLogSource();
                EnumSet<Scope> scopes = getEnabledScopesFromTree();
                if (scopes.isEmpty()) {
                    return new ArrayList<>();
                }
                PlayerSelection playerSelection = null;
                if (!allPlayers) {
                    Set<Username> players = new HashSet<>();
                    for (PlayerSelectionRow selectionRow : selectedPlayers) {
                        if (selectionRow.isSelected()) {
                            players.add(selectionRow.getUsername());
                        }
                    }
                    if (!players.isEmpty()) {
                        playerSelection = new PlayerSelection.Some(players);
                    }
                } else {
                    playerSelection = new PlayerSelection.All();
                }
                //build query from UI fields (v1)
                var q = new LogQuery(
                        scopes,
                        playerSelection,
                        audit_from.getValue().atStartOfDay(ZoneId.systemDefault()).minusDays(audit_include_days.getSelectionModel().getSelectedItem().days).toInstant(),
                        audit_from.getValue().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant() //inclusive end-day
                );
                //stream -> list sorted by time
                try (Stream<LogRecord> s = src.stream(q)) {
                    return s.toList();
                }
            }
        };

        task.setOnSucceeded(e -> {
            allEvents.setAll(task.getValue());
            lastLoadedScopes = getEnabledScopesFromTree(); // capture what was used
            refilter();
            if (allEvents.size() == filteredEvents.size()) {
                main.setStatus("Loaded " + allEvents.size() + " events");
            } else {
                main.setStatus("Showing " + filteredEvents.size() + " events");
            }
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            main.setStatus("Failed to load logs");
            ex.printStackTrace();
        });

        new Thread(task, "log-load").start();
    }

    /* -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
    |
    |------ Utility
    |
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------*/

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

}
