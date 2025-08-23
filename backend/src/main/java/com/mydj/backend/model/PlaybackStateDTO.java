package com.mydj.backend.model;

public class PlaybackStateDTO {
    private String trackName;
    private String artistName;
    private boolean isPlaying;
    private int progressMs;
    private int durationMs;
    private int volumePercent;
    private String deviceId;
    private String albumImageUrl;
    private String trackUri;

    public PlaybackStateDTO() {} 

    public PlaybackStateDTO(String trackName, String artistName, boolean isPlaying,
                            int progressMs, int durationMs, int volumePercent, String deviceId) {
        this.trackName = trackName;
        this.artistName = artistName;
        this.isPlaying = isPlaying;
        this.progressMs = progressMs;
        this.durationMs = durationMs;
        this.volumePercent = volumePercent;
        this.deviceId = deviceId;
    }

    public String getTrackUri() { 
        return trackUri; 
    }

    public void setTrackUri(String trackUri) { 
        this.trackUri = trackUri; 
    }

    public String getTrackName() {
        return trackName;
    }

    public String getArtistName() {
        return artistName;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public int getProgressMs() {
        return progressMs;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public int getVolumePercent() {
        return volumePercent;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setTrackName(String trackName) {
        this.trackName = trackName;
    }

    public void setArtistName(String artistName) {
        this.artistName = artistName;
    }

    public void setPlaying(boolean playing) {
        isPlaying = playing;
    }

    public void setProgressMs(int progressMs) {
        this.progressMs = progressMs;
    }

    public void setDurationMs(int durationMs) {
        this.durationMs = durationMs;
    }

    public void setVolumePercent(int volumePercent) {
        this.volumePercent = volumePercent;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getAlbumImageUrl() { return albumImageUrl; }
    public void setAlbumImageUrl(String albumImageUrl) { this.albumImageUrl = albumImageUrl; }
}

