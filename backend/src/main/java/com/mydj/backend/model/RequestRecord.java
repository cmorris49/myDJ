package com.mydj.backend.model;

import java.util.Map;
import java.util.Objects;

public class RequestRecord {
    private final String title;
    private final String artist;
    private final String genre;
    private final boolean explicit;
    private final String uri;
    private final boolean valid;

    public RequestRecord(String title, String artist, String genre, boolean explicit, String uri, boolean valid) {
        this.title = title;
        this.artist = artist;
        this.genre = genre;
        this.explicit = explicit;
        this.uri = uri;
        this.valid = valid;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getGenre() {
        return genre;
    }

    public boolean isExplicit() {
        return explicit;
    }

    public String getUri() {
        return uri;
    }

    public boolean isValid() {
        return valid;
    }

    public Map<String, String> toMap() {
        return Map.of(
            "title", title,
            "artist", artist,
            "genre", genre,
            "explicit", Boolean.toString(explicit),
            "uri", uri
        );
    }

    public static RequestRecord fromMap(Map<String, String> m) {
        String title = m.getOrDefault("title", "");
        String artist = m.getOrDefault("artist", "");
        String genre = m.getOrDefault("genre", "unknown");
        boolean explicit = Boolean.parseBoolean(m.getOrDefault("explicit", "false"));
        String uri = m.getOrDefault("uri", "");
        boolean valid = false;
        return new RequestRecord(title, artist, genre, explicit, uri, valid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RequestRecord that = (RequestRecord) o;
        return explicit == that.explicit &&
               valid == that.valid &&
               Objects.equals(title, that.title) &&
               Objects.equals(artist, that.artist) &&
               Objects.equals(genre, that.genre) &&
               Objects.equals(uri, that.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, artist, genre, explicit, uri, valid);
    }

    @Override
    public String toString() {
        return "RequestRecord{" +
               "title='" + title + '\'' +
               ", artist='" + artist + '\'' +
               ", genre='" + genre + '\'' +
               ", explicit=" + explicit +
               ", uri='" + uri + '\'' +
               ", valid=" + valid +
               '}';
    }
}
