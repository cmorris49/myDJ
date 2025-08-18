package com.mydj.desktop.ui;

import com.mydj.desktop.service.ApiClient;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import java.util.*;
import java.util.function.Consumer;

public class GenreSelector {

    private final ApiClient apiClient;
    private final Button pickBtn = new Button("Choose Genres");
    private final VBox compact = new VBox(pickBtn);
    private final List<String> allGenres = new ArrayList<>();
    private final LinkedHashSet<String> allowed = new LinkedHashSet<>();
    private Consumer<List<String>> onAllowedChanged = list -> {};

    public GenreSelector(ApiClient apiClient) {
        this.apiClient = apiClient;

        compact.setAlignment(Pos.CENTER_LEFT);
        compact.setSpacing(0);

        pickBtn.setOnAction(e -> openPickerDialog());
        pickBtn.getStyleClass().add("toggle-like");    
        pickBtn.setMinHeight(30);
    }

    public Node getCompactControl() {
        return compact;
    }

    /** Keeps allowed genres header in sync. */
    public void setOnAllowedChanged(Consumer<List<String>> cb) {
        this.onAllowedChanged = (cb != null) ? cb : (list -> {});
    }

    /** Called by App after fetching seeds and allowed from backend. */
    public void loadAvailableGenres(List<String> seeds, List<String> allowedFromBackend) {
        allGenres.clear();
        if (seeds != null) allGenres.addAll(seeds);

        allowed.clear();
        if (allowedFromBackend != null) allowed.addAll(allowedFromBackend);

        fireAllowedChanged();
    }

    private void fireAllowedChanged() {
        onAllowedChanged.accept(new ArrayList<>(allowed));
    }

    private void openPickerDialog() {
        Dialog<List<String>> dialog = new Dialog<>();
        dialog.setTitle("Choose Allowed Genres");

        if (compact.getScene() != null) {
            dialog.getDialogPane().getStylesheets().addAll(
                compact.getScene().getStylesheets()
            );
        }
        dialog.getDialogPane().getStyleClass().add("app-dialog");

        ButtonType saveBtnType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        // Search + checkbox list
        TextField search = new TextField();
        search.setPromptText("Filter genres...");
        search.getStyleClass().add("dialog-search");

        ListView<String> listView = new ListView<>();
        listView.getItems().setAll(allGenres);
        listView.getStyleClass().add("dialog-list");

        listView.setCellFactory(lv -> new CheckBoxListCell<>(item -> {
            javafx.beans.property.SimpleBooleanProperty prop =
                new javafx.beans.property.SimpleBooleanProperty(allowed.contains(item));
            prop.addListener((obs, was, is) -> {
                if (is) allowed.add(item);
                else    allowed.remove(item);
            });
            return prop;
        }));

        search.textProperty().addListener((obs, ov, nv) -> {
            String q = nv == null ? "" : nv.trim().toLowerCase(java.util.Locale.ROOT);
            java.util.List<String> filtered = allGenres.stream()
                .filter(g -> g.toLowerCase(java.util.Locale.ROOT).contains(q))
                .collect(java.util.stream.Collectors.toList());
            listView.getItems().setAll(filtered);
        });

        BorderPane content = new BorderPane();
        content.setTop(search);
        content.setCenter(listView);
        BorderPane.setMargin(search, new Insets(0, 0, 8, 0));

        dialog.getDialogPane().setContent(new VBox(10, content));
        dialog.getDialogPane().setPrefWidth(480);
        dialog.setResizable(true);

        Platform.runLater(() -> {
            Button save   = (Button) dialog.getDialogPane().lookupButton(saveBtnType);
            Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
            if (save != null)   save.getStyleClass().add("toggle-like");
            if (cancel != null) cancel.getStyleClass().add("toggle-like");
        });

        dialog.setResultConverter(bt -> bt == saveBtnType ? new java.util.ArrayList<>(allowed) : null);

        java.util.Optional<java.util.List<String>> result = dialog.showAndWait();
        result.ifPresent(selected -> {
            apiClient.sendAllowedGenres(selected,
                () -> Platform.runLater(this::fireAllowedChanged),
                err -> Platform.runLater(() ->
                    new Alert(Alert.AlertType.ERROR, "Failed to save genres:\n" + err.getMessage(), ButtonType.OK).showAndWait()
                )
            );
        });
    }

}
