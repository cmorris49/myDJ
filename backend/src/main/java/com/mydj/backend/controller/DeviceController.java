package com.mydj.backend.controller;

import com.mydj.backend.service.SpotifyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import se.michaelthelin.spotify.model_objects.miscellaneous.Device;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
public class DeviceController {

    private final SpotifyService spotifyService;

    public DeviceController(SpotifyService spotifyService) {
        this.spotifyService = spotifyService;
    }

    @GetMapping("/devices")
    public ResponseEntity<List<Map<String, String>>> getDevices() {
        try {
            Device[] devices = spotifyService.getAvailableDevices();
            List<Map<String, String>> out = Arrays.stream(devices)
                    .map(d -> Map.of("id", d.getId(), "name", d.getName()))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(List.of(Map.of("error", e.getMessage())));
        }
    }

    @PostMapping("/setDevice")
    public ResponseEntity<String> setDevice(@RequestParam String deviceId) {
        try {
            var ctx = spotifyService.getCurrentPlayback();
            String current = (ctx != null && ctx.getDevice() != null) ? ctx.getDevice().getId() : null;

            if (Objects.equals(current, deviceId)) {
                return ResponseEntity.ok("Device already active; no transfer performed");
            }

            spotifyService.transferPlayback(deviceId, true);
            return ResponseEntity.ok("Device set and playback transferred: " + deviceId);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error setting device: " + e.getMessage());
        }
    }
}
