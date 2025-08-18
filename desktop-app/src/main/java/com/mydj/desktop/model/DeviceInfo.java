package com.mydj.desktop.model;

public class DeviceInfo {
    private final String name;
    private final String id;

    public DeviceInfo(String name, String id) {
        this.name = name;
        this.id = id;
    }

    public String getName() { return name; }
    public String getId() { return id; }
}
