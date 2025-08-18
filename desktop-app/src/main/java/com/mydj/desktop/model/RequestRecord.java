package com.mydj.desktop.model;

public class RequestRecord {
    private final String title;
    private final String artist;
    private final String genre;
    private final boolean explicit;
    private final String uri;

    public RequestRecord(String title, String artist, String genre, boolean explicit, String uri) {
        this.title = title;
        this.artist = artist;
        this.genre = genre;
        this.explicit = explicit;
        this.uri = uri;
    }

    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getGenre() { return genre; }
    public boolean isExplicit() { return explicit; }
    public String getUri() { return uri; }

    public String display() {
        return title + " by " + artist;
    }
}

