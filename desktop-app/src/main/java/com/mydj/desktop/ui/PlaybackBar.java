package com.mydj.desktop.ui;

import java.util.HashMap;

import com.mydj.desktop.model.PlaybackState;
import com.mydj.desktop.service.ApiClient;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;
import java.util.Map;
import javafx.scene.CacheHint;

public class PlaybackBar {
    private final BorderPane root = new BorderPane();

    private final Label trackInfo = new Label();
    private final Button prevBtn = new Button("⏮");
    private final Button playPauseBtn = new Button("▶");
    private final Button nextBtn = new Button("⏭"); 
    private Button shuffleBtn = new Button("⇄");   
    private Button repeatBtn  = new Button();  
    private final Slider progressSlider = new Slider(0, 1, 0);
    private final Label timeLabel = new Label("0:00 / 0:00");
    private final Slider volumeSlider = new Slider(0, 100, 50);
    private final Label volumeStatus = new Label();
    private final ImageView albumCover = new ImageView();
    private String currentAlbumUrl = null;
    private final Map<String, Image> albumImageCache = new HashMap<>();
    private final ApiClient apiClient;
    private final Label statusLabel;
    private PlaybackState lastState;
    private long lastStateFetchTimestamp;
    private boolean isSeeking = false;
    private Timeline smoothUpdater;
    private volatile String currentTrackUri; 

    public String getCurrentTrackUri() {
        return currentTrackUri;
    }

    public PlaybackBar(ApiClient apiClient, Label statusLabel) {
        this.apiClient = apiClient;
        this.statusLabel = statusLabel;
        root.getStyleClass().add("playback-bar");
        root.setPadding(new Insets(10));

        // album cover and title 
        albumCover.setFitWidth(75);
        albumCover.setFitHeight(75);
        albumCover.setPreserveRatio(true);
        albumCover.setSmooth(true);
        albumCover.setCache(true);
        albumCover.setCacheHint(CacheHint.SPEED);

        trackInfo.setTextOverrun(OverrunStyle.ELLIPSIS);
        trackInfo.setMaxWidth(240);

        HBox leftBox = new HBox(10, albumCover, trackInfo);
        trackInfo.setId("song-title");
        leftBox.setAlignment(Pos.CENTER_LEFT);
        leftBox.setPadding(new Insets(0, 10, 0, 0));
        leftBox.setPickOnBounds(false); 

        // CENTER: transport progress 
        shuffleBtn.getStyleClass().add("control-icon-btn");
        prevBtn.getStyleClass().add("transport-btn");
        playPauseBtn.getStyleClass().addAll("transport-btn", "transport-btn-primary", "lg");
        nextBtn.getStyleClass().add("transport-btn");
        repeatBtn.getStyleClass().add("control-icon-btn");

        Label repeatGlyph = new Label("⟲");
        repeatGlyph.getStyleClass().add("icon-glyph");
        Label oneBadge = new Label("1");
        oneBadge.getStyleClass().add("icon-badge");
        StackPane repeatIcon = new StackPane(repeatGlyph, oneBadge);
        repeatBtn.setGraphic(repeatIcon);
        repeatBtn.setText(null);
        Label shuffleGlyph = new Label("⇄");          
        shuffleGlyph.getStyleClass().addAll("icon-glyph", "shuffle-glyph");
        StackPane shuffleIcon = new StackPane(shuffleGlyph);
        shuffleBtn.setGraphic(shuffleIcon);
        shuffleBtn.setText(null);  

        HBox controlsBox = new HBox(12, shuffleBtn, prevBtn, playPauseBtn, nextBtn, repeatBtn);
        controlsBox.setAlignment(Pos.CENTER);
        shuffleBtn.getStyleClass().removeAll("is-on");           
        repeatBtn.getStyleClass().removeAll("is-on", "is-one"); 

        progressSlider.setPrefWidth(360);
        HBox progressBox = new HBox(8, progressSlider, timeLabel);
        progressBox.setAlignment(Pos.CENTER);
        HBox.setHgrow(progressSlider, Priority.NEVER);

        VBox centerBox = new VBox(6, controlsBox, progressBox);
        centerBox.setAlignment(Pos.CENTER);
        progressBox.getStyleClass().add("progress-row");   
        progressSlider.getStyleClass().add("time-slider"); 
        centerBox.getStyleClass().add("playback-bar"); 

        // Seek only on release
        progressSlider.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (smoothUpdater != null) smoothUpdater.pause();
        });
        progressSlider.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (lastState != null) {
                int target = (int) (progressSlider.getValue() * lastState.getDurationMs());
                isSeeking = true;
                new Thread(() ->
                    apiClient.seek(target,
                        () -> onSeekComplete(target),
                        ex -> onSeekFail(ex.getMessage()))
                ).start();
            }
        });

        // volume 
        VBox rightBox = new VBox(2, volumeSlider, volumeStatus);
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        rightBox.setPadding(new Insets(8, 0, 0, 10));
        rightBox.setMaxWidth(180);
        rightBox.setPickOnBounds(false);

        StackPane layeredCenter = new StackPane(centerBox);
        StackPane.setAlignment(leftBox, Pos.CENTER_LEFT);
        StackPane.setAlignment(rightBox, Pos.CENTER_RIGHT);
        layeredCenter.getChildren().addAll(leftBox, rightBox);

        root.setCenter(layeredCenter);

        // Actions
        prevBtn.setOnAction(e -> runCmd(() ->
            apiClient.previous(
                () -> updateStatus("Skipped to previous track"),
                ex -> updateStatus("Prev failed: " + ex.getMessage())
            )
        ));
        playPauseBtn.setOnAction(e -> {
            if (lastState != null && lastState.isPlaying()) {
                runCmd(() ->
                    apiClient.pause(
                        () -> updateStatus("Paused"),
                        ex -> updateStatus("Pause failed: " + ex.getMessage())
                    )
                );
            } else {
                runCmd(() ->
                    apiClient.play(
                        () -> updateStatus("Playing"),
                        ex -> updateStatus("Play failed: " + ex.getMessage())
                    )
                );
            }
        });
        nextBtn.setOnAction(e -> runCmd(() ->
            apiClient.next(
                () -> updateStatus("Skipped to next track"),
                ex -> updateStatus("Next failed: " + ex.getMessage())
            )
        ));
        shuffleBtn.setOnAction(e -> {
            boolean goingOn = !shuffleBtn.getStyleClass().contains("is-on");
            apiClient.setShuffle(goingOn, () -> {
                if (goingOn) {
                    if (!shuffleBtn.getStyleClass().contains("is-on")) shuffleBtn.getStyleClass().add("is-on");
                } else {
                    shuffleBtn.getStyleClass().remove("is-on");
                }
            }, err -> {});
        });
        final StringProperty repeatMode = new SimpleStringProperty("off");
        repeatBtn.setOnAction(e -> {
            String next = switch (repeatMode.get()) {
                case "off"     -> "context";
                case "context" -> "track";
                default        -> "off";
            };
            apiClient.setRepeat(next, () -> {
                repeatMode.set(next);
                repeatBtn.getStyleClass().removeAll("is-on", "is-one");
                if ("context".equals(next)) {
                    repeatBtn.getStyleClass().add("is-on");  
                } else if ("track".equals(next)) {
                    repeatBtn.getStyleClass().add("is-one"); 
                }
            }, err -> {});
        });

        // Volume slider on release
        volumeSlider.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            int vol = (int) volumeSlider.getValue();
            volumeStatus.setText(vol + "%");
            runCmd(() ->
                apiClient.setVolume(vol,
                    () -> updateStatus("Volume: " + vol + "%"),
                    ex -> updateStatus("Volume failed: " + ex.getMessage())
                )
            );
        });

        // Smooth progress updater ~60 FPS
        smoothUpdater = new Timeline(new KeyFrame(Duration.millis(16), evt -> {
            if (lastState != null && lastState.isPlaying() && !progressSlider.isValueChanging() && !isSeeking) {
                long elapsed = System.currentTimeMillis() - lastStateFetchTimestamp;
                int prog = Math.min(lastState.getDurationMs(), lastState.getProgressMs() + (int) elapsed);
                double frac = (double) prog / Math.max(1, lastState.getDurationMs());
                progressSlider.setValue(frac);
                timeLabel.setText(formatTime(prog) + " / " + formatTime(lastState.getDurationMs()));
            }
        }));
        smoothUpdater.setCycleCount(Timeline.INDEFINITE);
        smoothUpdater.play();

        paintFilledTrackWhite(progressSlider);
        paintFilledTrackWhite(volumeSlider);
    }

    public void refreshPlayback() {
        apiClient.getPlaybackState(state -> {
            long now = System.currentTimeMillis();
            String newTitle = state.getTrackName() + " - " + state.getArtistName();
            String currentTitle = trackInfo.getText();
            boolean isNewTrack = !newTitle.equals(currentTitle);
            int apiProg = state.getProgressMs();
            int lastProg = (lastState != null) ? lastState.getProgressMs() : -1;

            this.currentTrackUri = state.getTrackUri(); 

            if (isNewTrack || apiProg >= lastProg) {
                lastState = state;
                lastStateFetchTimestamp = now;
                Platform.runLater(() -> {
                    trackInfo.setText(newTitle);
                    playPauseBtn.setText(state.isPlaying() ? "⏸" : "▶");

                    // album art
                    String url = state.getAlbumImageUrl();
                    if (url != null && !url.isBlank()) {
                        if (!url.equals(currentAlbumUrl)) {
                            currentAlbumUrl = url;

                            Image cached = albumImageCache.get(url);
                            if (cached != null && cached.getProgress() >= 1.0 && !cached.isError()) {
                                albumCover.setImage(cached);
                            } else {
                                Image pending = new Image(url, true);
                                albumImageCache.put(url, pending);

                                if (pending.isBackgroundLoading()) {
                                    pending.progressProperty().addListener((obs, ov, nv) -> {
                                        if (nv.doubleValue() >= 1.0) {
                                            if (url.equals(currentAlbumUrl)) {
                                                albumCover.setImage(pending);
                                            }
                                        }
                                    });
                                } else {
                                    albumCover.setImage(pending);
                                }
                            }
                        }
                    }

                    if (!progressSlider.isValueChanging() && !isSeeking) {
                        double frac = (double) apiProg / Math.max(1, state.getDurationMs());
                        progressSlider.setValue(frac);
                        timeLabel.setText(formatTime(apiProg) + " / " + formatTime(state.getDurationMs()));
                    }
                });
            }
        }, ex -> updateStatus("Refresh failed: " + ex.getMessage()));
    }

    private void onSeekComplete(int pos) {
        if (lastState != null) lastState.setProgressMs(pos);
        lastStateFetchTimestamp = System.currentTimeMillis();
        updateStatus("Seeked to " + formatTime(pos));
        isSeeking = false;
        if (smoothUpdater != null) smoothUpdater.play();
    }

    private void onSeekFail(String msg) {
        updateStatus("Seek failed: " + msg);
        isSeeking = false;
        if (smoothUpdater != null) smoothUpdater.play();
    }

    private void runCmd(Runnable cmd) {
        new Thread(cmd, "PlaybackCmd").start();
    }

    private void updateStatus(String msg) {
        Platform.runLater(() -> statusLabel.setText(msg));
    }

    private String formatTime(int ms) {
        int total = ms / 1000;
        int mins = total / 60;
        int secs = total % 60;
        return String.format("%d:%02d", mins, secs);
    }

    private void paintFilledTrackWhite(Slider slider) {
        Platform.runLater(() -> {
            Region track = (Region) slider.lookup(".track");
            if (track == null) return;

            ChangeListener<Number> updater = (obs, oldV, newV) -> {
                double pct = (slider.getValue() - slider.getMin()) /
                             Math.max(1e-6, (slider.getMax() - slider.getMin()));
                double p = Math.max(0, Math.min(100, pct * 100.0));
                track.setStyle(String.format(
                    "-fx-background-color: linear-gradient(to right, " +
                    "white %.2f%%, rgba(255,255,255,0.25) %.2f%%);" +
                    "-fx-background-insets: 0;" +
                    "-fx-background-radius: 3;",
                    p, p
                ));
            };

            slider.valueProperty().addListener(updater);
            updater.changed(null, null, null); 
        });
    }

    public Node getNode() {
        return root;
    }

    public void stop() {
        if (smoothUpdater != null) smoothUpdater.stop();
    }
}
