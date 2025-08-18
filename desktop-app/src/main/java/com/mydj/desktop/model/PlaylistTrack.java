package com.mydj.desktop.model;

public class PlaylistTrack {
    private final String name;
    private final String artist;
    private final String uri;

    public PlaylistTrack(String name, String artist, String uri) {
        this.name = name;
        this.artist = artist;
        this.uri = uri;
    }

    public String getName() { return name; }
    public String getArtist() { return artist; }
    public String getUri()    { return uri; }

    public String display() {
        return name + " by " + artist;
    }
}

