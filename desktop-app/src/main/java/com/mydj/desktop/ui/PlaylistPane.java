package com.mydj.desktop.ui;

import com.mydj.desktop.model.PlaylistInfo;
import com.mydj.desktop.model.PlaylistTrack;
import com.mydj.desktop.service.ApiClient;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.*;
import java.util.function.Consumer;

public class PlaylistPane {
    private final ApiClient apiClient;

    private final VBox root = new VBox(8);

    private final Label playlistsTitle = new Label("Playlists");
    private final ComboBox<String> playlistCombo = new ComboBox<>();
    private final Button refreshButton = new Button("Refresh");
    
    private final CheckBox autoQueueCheckBox = new CheckBox("Auto-queue new tracks");

    private final Label currentTitle = new Label("Current Playlist");
    private final ListView<String> trackListView = new ListView<>();

    private String selectedPlaylistId = null;
    private Consumer<PlaylistInfo> onPlaylistChanged = p -> {};

    private final Map<String, String> displayToUri = new HashMap<>();
    private Set<String> seenDisplays = new HashSet<>();

    public PlaylistPane(ApiClient apiClient) {
        this.apiClient = apiClient;

        centerTitle(playlistsTitle);
        centerSubtitle(currentTitle);

        HBox controls = new HBox(10, playlistCombo, refreshButton, autoQueueCheckBox);
        controls.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(playlistCombo, Priority.SOMETIMES);
        refreshButton.getStyleClass().add("toggle-like");

        root.getChildren().setAll(
            playlistsTitle,
            controls,
            currentTitle,
            trackListView
        );
        VBox.setVgrow(trackListView, Priority.ALWAYS);

        trackListView.setOnMouseClicked(evt -> {
            if (evt.getButton() == MouseButton.PRIMARY && evt.getClickCount() == 2) {
                String disp = trackListView.getSelectionModel().getSelectedItem();
                String uri = displayToUri.get(disp);
                if (uri != null) {
                    new Thread(() -> {
                        apiClient.queue(uri,
                            () -> apiClient.next(() -> {}, ex -> {}),
                            ex -> {}
                        );
                    }, "QueueAndSkip").start();
                }
            }
        });

        refreshButton.setOnAction(e -> refreshTracks());

        playlistCombo.setOnAction(e -> {
            String sel = playlistCombo.getValue();
            if (sel == null) return;

            apiClient.getPlaylists(list -> {
                for (PlaylistInfo p : list) {
                    if (p.getName().equals(sel)) {
                        selectedPlaylistId = p.getId();
                        onPlaylistChanged.accept(p);
                        seenDisplays.clear();
                        refreshTracks();
                        break;
                    }
                }
            }, ex -> {});
        });
    }

    private void centerTitle(Label lbl) {
        lbl.getStyleClass().add("section-title"); 
        lbl.setMaxWidth(Double.MAX_VALUE);
        lbl.setAlignment(Pos.CENTER);
    }

    private void centerSubtitle(Label lbl) {
        lbl.getStyleClass().add("section-subtitle"); 
        lbl.setMaxWidth(Double.MAX_VALUE);
        lbl.setAlignment(Pos.CENTER);
    }

    public Node getNode() {
        return root;
    }

    public void loadPlaylists() {
        apiClient.getPlaylists(list -> {
            playlistCombo.getItems().clear();
            for (PlaylistInfo p : list) {
                playlistCombo.getItems().add(p.getName());
            }
            if (!list.isEmpty()) {
                playlistCombo.setValue(list.get(0).getName());
                // trigger initial load
                Platform.runLater(() -> {
                    if (playlistCombo.getOnAction() != null) {
                        playlistCombo.getOnAction().handle(null);
                    }
                });
            }
        }, ex -> System.err.println("Failed to load playlists: " + ex.getMessage()));
    }

    public void refreshTracks() {
        if (selectedPlaylistId == null) return;

        apiClient.getPlaylistTracks(selectedPlaylistId, tracks -> {
            List<String> displays = new ArrayList<>();
            displayToUri.clear();

            for (PlaylistTrack t : tracks) {
                String disp = t.display();
                displays.add(disp);
                displayToUri.put(disp, t.getUri());
            }

            if (autoQueueCheckBox.isSelected()) {
                for (String disp : displays) {
                    if (!seenDisplays.contains(disp)) {
                        String uri = displayToUri.get(disp);
                        new Thread(() -> apiClient.queue(uri, () -> {}, ex -> {}), "AutoQueue").start();
                    }
                }
            }

            seenDisplays = new HashSet<>(displays);
            trackListView.getItems().setAll(displays);
        }, ex -> System.err.println("Failed to fetch tracks: " + ex.getMessage()));
    }

    public String getSelectedPlaylistId() {
        return selectedPlaylistId;
    }

    public String getSelectedPlaylistName() {
        return playlistCombo.getValue();
    }

    public void setOnPlaylistChanged(Consumer<PlaylistInfo> cb) {
        this.onPlaylistChanged = cb != null ? cb : (p -> {});
    }
}
