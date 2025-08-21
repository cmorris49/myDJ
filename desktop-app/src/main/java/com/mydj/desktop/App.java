package com.mydj.desktop;

import com.mydj.desktop.service.ApiClient;
import com.mydj.desktop.ui.DeviceSelector;
import com.mydj.desktop.ui.GenreSelector;
import com.mydj.desktop.ui.PlaybackBar;
import com.mydj.desktop.ui.PlaylistPane;
import com.mydj.desktop.ui.RequestsPane;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.controlsfx.control.NotificationPane;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class App extends Application {

    private static final String BASE_URL = "https://mydj-1.onrender.com";

    private ApiClient apiClient;
    private GenreSelector genreSelector;
    private PlaylistPane playlistPane;
    private RequestsPane requestsPane;
    private DeviceSelector deviceSelector;
    private Label status;
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor();
    private Consumer<String> notifier;

    private static long jitterMs(int baseMs) {
        return baseMs + ThreadLocalRandom.current().nextInt(0, 400);
    }

    @Override
    public void start(Stage stage) {
        ToggleButton themeToggle = new ToggleButton("Dark Mode");
        themeToggle.setId("theme-toggle");
        themeToggle.setSelected(true);
        apiClient = new ApiClient(BASE_URL);
        status = new Label("Status: Ready");

        genreSelector = new GenreSelector(apiClient);
        playlistPane = new PlaylistPane(apiClient);
        requestsPane = new RequestsPane(apiClient);
        deviceSelector = new DeviceSelector(apiClient);
        PlaybackBar playbackBar = new PlaybackBar(apiClient, status);

        requestsPane.addRightOfExplicit(genreSelector.getCompactControl());
        genreSelector.setOnAllowedChanged(allowed -> {
            String text = allowed.isEmpty() ? "(none)" : String.join(", ", allowed);
            requestsPane.setAllowedHeading(text);
        });

        // adding to playlist from requests
        requestsPane.setOnAddToPlaylist((display, uri) -> {
            String playlistId = playlistPane.getSelectedPlaylistId();
            if (playlistId == null) {
                status.setText("No playlist selected to add to.");
                if (notifier != null) notifier.accept("No playlist selected");
                return;
            }
            apiClient.addToPlaylistByUri(
                uri, playlistId, resp -> {
                    status.setText("Added: " + resp);
                    if (notifier != null) notifier.accept("Added to playlist");
                    playlistPane.refreshTracks();
                    requestsPane.removeDisplay(display);
                }
            );
        });

        // When playlist selection changes, refresh the tracks
        playlistPane.setOnPlaylistChanged(p -> playlistPane.refreshTracks());

        // Top bar
        Button signIn = new Button("Sign in");
        signIn.setOnAction(e -> getHostServices().showDocument(BASE_URL + "/login"));
        Button showQr = new Button("Show QR");
        showQr.setDisable(true);
        showQr.setOnAction(e -> getHostServices().showDocument(BASE_URL + "/qr-default"));
        HBox leftControls = new HBox(8, signIn, showQr);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topSelectors = new HBox(10,
            leftControls,               
            playlistPane.getNode(),     
            spacer,                    
            deviceSelector.getNode(),
            themeToggle
        );

        // Middle content
        SplitPane lists = new SplitPane(
            playlistPane.getNode(),
            requestsPane.getValidBox(),
            requestsPane.getInvalidBox()
        );
        lists.setDividerPositions(0.33, 0.66);
        VBox.setVgrow(lists, Priority.ALWAYS);

        // Main content
        VBox mainContent = new VBox(15, topSelectors, lists, status);
        mainContent.setPadding(new Insets(10));
        VBox.setVgrow(lists, Priority.ALWAYS);

        // Compose with playback bar
        VBox contentWrapper = new VBox();
        contentWrapper.getChildren().addAll(mainContent, playbackBar.getNode());
        VBox.setVgrow(mainContent, Priority.ALWAYS);

        // Notification wrapper
        NotificationPane notificationPane = new NotificationPane(contentWrapper);
        notificationPane.setShowFromTop(true);
        notifier = msg -> {
            notificationPane.setText(msg);
            notificationPane.show();
            PauseTransition pt = new PauseTransition(Duration.seconds(2));
            pt.setOnFinished(e -> notificationPane.hide());
            pt.play();
        };

        // Scene
        Scene scene = new Scene(notificationPane, 1000, 700);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        applyTheme(scene, themeToggle.isSelected());
        themeToggle.selectedProperty().addListener((obs, oldV, newV) -> applyTheme(scene, newV));

        stage.setScene(scene);
        stage.setTitle("myDJ Desktop App");
        stage.show();

        apiClient.checkLogin(isAuthed -> showQr.setDisable(!isAuthed));
        poller.scheduleAtFixedRate(
            () -> apiClient.checkLogin(isAuthed -> showQr.setDisable(!isAuthed)),
            jitterMs(2000), 10_000, java.util.concurrent.TimeUnit.MILLISECONDS
        );

        Platform.runLater(() -> {
            playbackBar.refreshPlayback();
            playlistPane.refreshTracks();
            requestsPane.loadRequests();
            deviceSelector.refreshDevices();
        });

        // Polling
        poller.scheduleAtFixedRate(() -> Platform.runLater(playbackBar::refreshPlayback), 0, 1, TimeUnit.SECONDS);
        poller.scheduleAtFixedRate(() -> Platform.runLater(playlistPane::refreshTracks), jitterMs(1500), 15_000, TimeUnit.MILLISECONDS);
        poller.scheduleAtFixedRate(() -> Platform.runLater(requestsPane::loadRequests), jitterMs(3000), 10_000, TimeUnit.MILLISECONDS);
        poller.scheduleAtFixedRate(() -> Platform.runLater(deviceSelector::refreshDevices), jitterMs(4500), 30_000, TimeUnit.MILLISECONDS);

        // Initial loads
        playlistPane.loadPlaylists();
        requestsPane.loadRequests();

        // Load genres
        apiClient.getGenreSeeds(
            allGenres -> apiClient.getAllowedGenres(
                allowedGenres -> {
                    genreSelector.loadAvailableGenres(allGenres, allowedGenres);
                    requestsPane.setAllowedHeading(allowedGenres.isEmpty() ? "(none)" : String.join(", ", allowedGenres));
                },
                errAllowed -> {
                    genreSelector.loadAvailableGenres(allGenres, List.of());
                    requestsPane.setAllowedHeading("(none)");
                }
            ),
            errSeeds -> {
                List<String> fallback = List.of(
                    "pop", "rock", "hip-hop", "classical", "alternative", "jazz", "blues", "country",
                    "dance", "electronic", "folk", "heavy-metal", "reggae", "r-n-b", "punk",
                    "soul", "indie", "edm"
                );
                apiClient.getAllowedGenres(
                    allowedGenres -> {
                        genreSelector.loadAvailableGenres(fallback, allowedGenres);
                        requestsPane.setAllowedHeading(allowedGenres.isEmpty() ? "(none)" : String.join(", ", allowedGenres));
                    },
                    errAllowed -> {
                        genreSelector.loadAvailableGenres(fallback, List.of());
                        requestsPane.setAllowedHeading("(none)");
                    }
                );
            }
        );

        deviceSelector.loadDevices();
    }

    @Override
    public void stop() {
        try { poller.shutdownNow(); } catch (Exception ignored) {}
    }

    private void applyTheme(Scene scene, boolean darkMode) {
        String dark  = getClass().getResource("/dark-theme.css").toExternalForm();
        String light = getClass().getResource("/light-theme.css").toExternalForm();
        scene.getStylesheets().removeAll(dark, light);
        scene.getStylesheets().add(darkMode ? dark : light);
    }

    public static void main(String[] args) {
        launch();
    }
}
