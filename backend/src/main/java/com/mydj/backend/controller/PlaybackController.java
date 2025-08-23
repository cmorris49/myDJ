package com.mydj.backend.controller;

import com.mydj.backend.model.PlaybackStateDTO;
import com.mydj.backend.service.SpotifyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;
import se.michaelthelin.spotify.model_objects.specification.Track;
import com.mydj.backend.util.UriUtils;
import java.util.Map;
import java.util.Optional;

@RestController
public class PlaybackController {

    private final SpotifyService spotifyService;

    public PlaybackController(SpotifyService spotifyService) {
        this.spotifyService = spotifyService;
    }

    @GetMapping("/playback")
    public ResponseEntity<PlaybackStateDTO> getPlayback() {
        try {
            CurrentlyPlayingContext ctx = spotifyService.getCurrentPlayback();
            if (ctx == null || ctx.getItem() == null) {
                return ResponseEntity.ok(new PlaybackStateDTO("", "", false, 0, 0, 0, spotifyService.getCurrentDeviceId()));
            }

            boolean isPlaying = Boolean.TRUE.equals(ctx.getIs_playing());
            int progressMs = ctx.getProgress_ms() != null ? ctx.getProgress_ms() : 0;
            int volumePercent = ctx.getDevice() != null ? ctx.getDevice().getVolume_percent() : 0;
            String deviceId = ctx.getDevice() != null ? ctx.getDevice().getId() : spotifyService.getCurrentDeviceId();

            String trackName = "";
            String artistName = "";
            int durationMs = 0;
            String albumImageUrl = null;
            String trackUri = null;

            if (ctx.getItem() instanceof Track t) {
                trackName = t.getName();
                artistName = t.getArtists().length > 0 ? t.getArtists()[0].getName() : "";
                durationMs = t.getDurationMs();

                trackUri = t.getUri();
                
                var images = (t.getAlbum() != null) ? t.getAlbum().getImages() : null;
                if (images != null && images.length > 0) {
                    albumImageUrl = images[0].getUrl(); 
                }
            }

            PlaybackStateDTO dto = new PlaybackStateDTO(
                    trackName,
                    artistName,
                    isPlaying,
                    progressMs,
                    durationMs,
                    volumePercent,
                    deviceId
            );
            dto.setAlbumImageUrl(albumImageUrl);
            if (trackUri != null && !trackUri.isBlank()) {                 
                dto.setTrackUri(UriUtils.canonicalTrackUri(trackUri));      
            }
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(new PlaybackStateDTO("", "", false, 0, 0, 0, null));
        }
    }

    @PostMapping("/playback/play")
    public ResponseEntity<String> play(@RequestBody(required = false) Map<String, String> body) {
        try {
            Optional<String> deviceId = Optional.empty();
            if (body != null && body.containsKey("deviceId")) {
                deviceId = Optional.of(body.get("deviceId"));
            }
            spotifyService.play(deviceId);
            return ResponseEntity.ok("Resumed");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Play failed: " + e.getMessage());
        }
    }

    @PostMapping("/playback/pause")
    public ResponseEntity<String> pause() {
        try {
            spotifyService.pause();
            return ResponseEntity.ok("Paused");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Pause failed: " + e.getMessage());
        }
    }

    @PostMapping("/playback/next")
    public ResponseEntity<String> next() {
        try {
            spotifyService.next(); 
            return ResponseEntity.ok("Skipped to next");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Next failed: " + e.getMessage());
        }
    }

    @PostMapping("/playback/previous")
    public ResponseEntity<String> previous() {
        try {
            spotifyService.previous();
            return ResponseEntity.ok("Skipped to previous");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Previous failed: " + e.getMessage());
        }
    }

    @PostMapping("/playback/seek")
    public ResponseEntity<String> seek(@RequestBody Map<String, Integer> body) {
        Integer positionMs = body.get("positionMs");
        if (positionMs == null) {
            return ResponseEntity.badRequest().body("Missing positionMs");
        }
        try {
            spotifyService.seek(positionMs);
            return ResponseEntity.ok("Seeked");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Seek failed: " + e.getMessage());
        }
    }

    @PostMapping("/playback/volume")
    public ResponseEntity<String> setVolume(@RequestBody Map<String, Integer> body) {
        Integer volumePercent = body.get("volumePercent");
        if (volumePercent == null) {
            return ResponseEntity.badRequest().body("Missing volumePercent");
        }
        try {
            spotifyService.setVolume(volumePercent);
            return ResponseEntity.ok("Volume set");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Volume failed: " + e.getMessage());
        }
    }

    @PostMapping("/playback/queue")
    public ResponseEntity<String> queue(@RequestBody Map<String, String> body) {
        String uri = body.get("uri");
        if (uri == null || uri.isBlank()) {
            return ResponseEntity.badRequest().body("Missing uri");
        }
        try {
            Optional<String> deviceId = Optional.ofNullable(body.get("deviceId"));
            spotifyService.queue(uri, deviceId);
            return ResponseEntity.ok("Queued: " + uri);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Queue failed: " + e.getMessage());
        }
    }
    
}
