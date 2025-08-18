package com.mydj.desktop.model;

public class PlaylistInfo {
    private final String name;
    private final String id;

    public PlaylistInfo(String name, String id) {
        this.name = name;
        this.id = id;
    }

    public String getName() { return name; }
    public String getId() { return id; }
}
