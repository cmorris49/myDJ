package com.mydj.desktop.model;

public class PlaybackState {
    private String trackName;
    private String artistName;
    private boolean isPlaying;
    private int progressMs;
    private int durationMs;
    private int volumePercent;
    private String deviceId;
    private String albumImageUrl;
    private String trackUri;

    public PlaybackState() {}

    // getters & setters
    public String getTrackName() { return trackName; }
    public void setTrackName(String trackName) { this.trackName = trackName; }

    public String getArtistName() { return artistName; }
    public void setArtistName(String artistName) { this.artistName = artistName; }

    public boolean isPlaying() { return isPlaying; }
    public void setPlaying(boolean playing) { isPlaying = playing; }

    public int getProgressMs() { return progressMs; }
    public void setProgressMs(int progressMs) { this.progressMs = progressMs; }

    public int getDurationMs() { return durationMs; }
    public void setDurationMs(int durationMs) { this.durationMs = durationMs; }

    public int getVolumePercent() { return volumePercent; }
    public void setVolumePercent(int volumePercent) { this.volumePercent = volumePercent; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getAlbumImageUrl() { return albumImageUrl; }
    public void setAlbumImageUrl(String albumImageUrl) { this.albumImageUrl = albumImageUrl; }

    public String getTrackUri() { return trackUri; }
    public void setTrackUri(String trackUri) { this.trackUri = trackUri; }
}
