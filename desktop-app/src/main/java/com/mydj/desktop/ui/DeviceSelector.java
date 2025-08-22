package com.mydj.desktop.ui;

import com.mydj.desktop.model.DeviceInfo;
import com.mydj.desktop.service.ApiClient;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;

import java.util.Objects;

public class DeviceSelector {

    private final ApiClient apiClient;
    private final HBox root = new HBox(6);
    private final ComboBox<DeviceInfo> deviceCombo = new ComboBox<>();

    public DeviceSelector(ApiClient apiClient) {
        this.apiClient = apiClient;

        deviceCombo.setConverter(new StringConverter<>() {
            @Override public String toString(DeviceInfo d) { return d == null ? "" : d.getName(); }
            @Override public DeviceInfo fromString(String s) { return null; }
        });

        HBox.setHgrow(deviceCombo, Priority.SOMETIMES);
        root.getChildren().add(deviceCombo);
        root.setFillHeight(true);
        deviceCombo.setPromptText("Device");

        deviceCombo.setOnShowing(e -> refreshDevices());

        deviceCombo.setOnAction(e -> {
            DeviceInfo d = deviceCombo.getValue();
            if (d == null) return;
            apiClient.setDevice(
                d.getId(),
                () -> System.out.println("Set device OK: " + d.getName()),
                ex -> System.err.println("Failed to set device: " + ex.getMessage())
            );
        });
    }

    public Node getNode() {
        return root;
    }

    public void refreshDevices() {
        String previouslySelectedId = getSelectedDeviceId();

        apiClient.getDevices(list -> {
            deviceCombo.getItems().setAll(list);

            if (previouslySelectedId != null) {
                for (DeviceInfo d : list) {
                    if (Objects.equals(d.getId(), previouslySelectedId)) {
                        deviceCombo.getSelectionModel().select(d);
                        return;
                    }
                }
            }
            if (!list.isEmpty() && deviceCombo.getSelectionModel().isEmpty()) {
                deviceCombo.getSelectionModel().select(0);
            }
        }, ex -> System.err.println("Failed to load devices: " + ex.getMessage()));
    }

    private String getSelectedDeviceId() {
        DeviceInfo d = deviceCombo.getValue();
        return d == null ? null : d.getId();
    }

    public void loadDevices() {
        refreshDevices();
    }
}
