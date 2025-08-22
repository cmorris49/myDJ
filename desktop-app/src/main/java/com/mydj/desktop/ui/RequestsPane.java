package com.mydj.desktop.ui;

import com.mydj.desktop.model.RequestRecord;
import com.mydj.desktop.service.ApiClient;
import com.mydj.desktop.ui.util.FxUi;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.*;
import java.util.function.BiConsumer;

public class RequestsPane {
    private final ApiClient apiClient;

    private final VBox root = new VBox(12);

    private final ListView<String> validListView = new ListView<>();
    private final ListView<String> invalidListView = new ListView<>();

    private final ToggleButton autoAddToggle = new ToggleButton("Auto-Add Valid");
    private final ToggleButton explicitToggle = new ToggleButton("Allow Explicit");
    private final HBox toggles = new HBox(10, autoAddToggle, explicitToggle);

    private final Label validTitle   = new Label("Valid Requests");
    private final Label invalidTitle = new Label("Invalid Requests");
    private final Label allowedHeading = new Label("Allowed Genres: all");

    private final VBox validBox = new VBox(8);
    private final VBox invalidBox = new VBox(8);

    private final Map<String, String> displayToUri = new HashMap<>();

    private BiConsumer<String, String> onAddToPlaylist = (d, u) -> {};

    public RequestsPane(ApiClient apiClient) {
        this.apiClient = apiClient;

        toggles.setAlignment(Pos.CENTER_LEFT);
        FxUi.setupEvenRow(toggles, 10, 8); 

        centerTitle(validTitle);
        centerTitle(invalidTitle);
        centerSubTitle(allowedHeading);

        validBox.getChildren().setAll(validTitle, allowedHeading, toggles, validListView);
        validBox.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(validListView, Priority.ALWAYS);

        invalidBox.getChildren().setAll(invalidTitle, invalidListView);
        invalidBox.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(invalidListView, Priority.ALWAYS);

        root.getChildren().addAll(validBox, invalidBox);

        // init explicit toggle from backend
        apiClient.getAllowExplicit(
            v -> Platform.runLater(() -> explicitToggle.setSelected(v)),
            err -> System.err.println("getAllowExplicit failed: " + err)
        );

        // double-click add from lists
        validListView.setOnMouseClicked(evt -> {
            if (evt.getButton() == MouseButton.PRIMARY && evt.getClickCount() == 2) {
                String display = validListView.getSelectionModel().getSelectedItem();
                if (display != null) {
                    String uri = displayToUri.get(display);
                    if (uri != null && !uri.isBlank()) onAddToPlaylist.accept(display, uri);
                }
            }
        });
        invalidListView.setOnMouseClicked(evt -> {
            if (evt.getButton() == MouseButton.PRIMARY && evt.getClickCount() == 2) {
                String display = invalidListView.getSelectionModel().getSelectedItem();
                if (display != null) {
                    String uri = displayToUri.get(display);
                    if (uri != null && !uri.isBlank()) onAddToPlaylist.accept(display, uri);
                }
            }
        });

        autoAddToggle.setOnAction(e -> loadRequests());
        explicitToggle.setOnAction(e -> apiClient.setAllowExplicit(
            explicitToggle.isSelected(),
            () -> Platform.runLater(this::loadRequests), 
            err -> System.err.println("setAllowExplicit failed: " + err)
        ));
    }

    private void centerTitle(Label lbl) {
        lbl.getStyleClass().add("section-title");
        lbl.setAlignment(Pos.CENTER);
        lbl.setMaxWidth(Double.MAX_VALUE);
    }

    private void centerSubTitle(Label lbl) {
        lbl.getStyleClass().add("section-subtitle");
        lbl.setAlignment(Pos.CENTER);
        lbl.setMaxWidth(Double.MAX_VALUE);
    }

    public Node getNode() { return root; }
    public Node getValidBox() { return validBox; }
    public Node getInvalidBox() { return invalidBox; }

    public void loadRequests() {
        apiClient.getRequests(map -> {
            List<RequestRecord> valid = map.getOrDefault("valid", Collections.emptyList());
            List<RequestRecord> invalid = map.getOrDefault("invalid", Collections.emptyList());

            boolean explicitAllowed = explicitToggle.isSelected();

            List<String> validDisplays = new ArrayList<>();
            List<String> invalidDisplays = new ArrayList<>();
            displayToUri.clear();

            for (RequestRecord r : valid) {
                String display = r.display();
                displayToUri.put(display, r.getUri());
                if (explicitAllowed || !r.isExplicit()) validDisplays.add(display);
                else invalidDisplays.add(display);
            }
            for (RequestRecord r : invalid) {
                String display = r.display();
                displayToUri.put(display, r.getUri());
                invalidDisplays.add(display);
            }

            Platform.runLater(() -> {
                validListView.getItems().setAll(validDisplays);
                invalidListView.getItems().setAll(invalidDisplays);

                if (autoAddToggle.isSelected()) {
                    for (String display : validDisplays) {
                        String uri = displayToUri.get(display);
                        onAddToPlaylist.accept(display, uri);
                    }
                }
            });
        }, ex -> System.err.println("Failed to load requests: " + ex.getMessage()));
    }

    public void setOnAddToPlaylist(BiConsumer<String, String> handler) {
        this.onAddToPlaylist = handler != null ? handler : (d, u) -> {};
    }

    public void setAllowedHeading(String text) {
        String t = (text == null || text.isBlank() || "(none)".equalsIgnoreCase(text.trim()))
                ? "all" : text;
        allowedHeading.setText("Allowed Genres: " + t);
    }

    public void addRightOfExplicit(Node node) {
        int idx = toggles.getChildren().indexOf(explicitToggle);
        if (idx < 0) idx = toggles.getChildren().size() - 1;
        toggles.getChildren().add(idx + 1, node);
    }

    public void removeDisplay(String display) {
        validListView.getItems().remove(display);
        invalidListView.getItems().remove(display);
        displayToUri.remove(display);
    }
}
