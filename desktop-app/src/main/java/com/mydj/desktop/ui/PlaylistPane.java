package com.mydj.desktop.ui;

import com.mydj.desktop.model.PlaylistInfo;
import com.mydj.desktop.model.PlaylistTrack;
import com.mydj.desktop.service.ApiClient;
import com.mydj.desktop.ui.util.FxUi;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.css.PseudoClass;
import javafx.scene.input.MouseEvent;

import java.util.*;
import java.util.function.Consumer;

public class PlaylistPane {

    private final ApiClient apiClient;
    private final VBox root = new VBox(8);
    private final Label playlistsTitle = new Label("Playlists");
    private final Label currentTitle = new Label("Manage your current playlist");
    private final ComboBox<String> playlistCombo = new ComboBox<>();
    private final Button refreshButton = new Button("Refresh");
    private final ToggleButton autoQueueToggle = new ToggleButton("Auto-Queue");
    private final ListView<String> trackListView = new ListView<>();
    private String selectedPlaylistId = null;
    private Consumer<PlaylistInfo> onPlaylistChanged = p -> {};
    private final Map<String, String> displayToUri = new HashMap<>();
    private Set<String> seenDisplays = new HashSet<>();
    private static final PseudoClass PC_PLAYING = PseudoClass.getPseudoClass("playing");
    private volatile String playingUri;

    public void setCurrentlyPlayingUri(String uri) {
        this.playingUri = uri;
        if (trackListView != null) { 
            trackListView.refresh();
        }
    }

    public PlaylistPane(ApiClient apiClient) {
        this.apiClient = apiClient;

        centerTitle(playlistsTitle);
        centerSubtitle(currentTitle);

        HBox controls = new HBox(10, playlistCombo, refreshButton, autoQueueToggle);
        controls.setAlignment(Pos.CENTER_LEFT);
        FxUi.setupEvenRow(controls, 10, 8); 

        // style
        refreshButton.getStyleClass().add("toggle-like");
        trackListView.setId("playlist-list"); 

        root.getChildren().setAll(
            playlistsTitle,
            currentTitle,
            controls,
            trackListView
        );
        VBox.setVgrow(trackListView, Priority.ALWAYS);

        trackListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String display, boolean empty) {
                super.updateItem(display, empty);
                if (empty || display == null) {
                    setText(null);
                    pseudoClassStateChanged(PC_PLAYING, false);
                    return;
                }
                setText(display);

                String uri = displayToUri.get(display); 
                boolean isPlaying = playingUri != null && playingUri.equals(uri);
                pseudoClassStateChanged(PC_PLAYING, isPlaying);
            }
        });

        // Double-click to queue & skip
        trackListView.setOnMouseClicked((MouseEvent e) -> {
            if (e.getClickCount() == 2) {
                String display = trackListView.getSelectionModel().getSelectedItem();
                if (display == null) return;

                String trackUri = displayToUri.get(display);
                String playlistId = getSelectedPlaylistId(); 

                if (playlistId == null || trackUri == null) return;

                apiClient.playFromPlaylist(playlistId, trackUri, () -> {}, err -> {});
                setCurrentlyPlayingUri(trackUri);
                trackListView.getSelectionModel().clearSelection();
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
            }, ex -> System.err.println("Failed to load playlists: " + ex.getMessage()));
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

            if (autoQueueToggle.isSelected()) {
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
