package com.mydj.backend.controller;

import com.mydj.backend.model.RequestRecord;
import com.mydj.backend.service.RequestClassificationService;
import com.mydj.backend.service.SpotifyService;
import com.mydj.backend.util.UriUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import com.mydj.backend.service.RequestQueueService;
import com.mydj.backend.service.RequestReclassifier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@RestController
public class RequestController {

    private final SpotifyService spotifyService;
    private final RequestClassificationService classificationService;
    private final RequestQueueService queues;
    private final RequestReclassifier reclassifier;

    public RequestController(RequestClassificationService classificationService,
                            SpotifyService spotifyService,
                            RequestQueueService queues,
                            RequestReclassifier reclassifier) {
        this.classificationService = classificationService;
        this.spotifyService = spotifyService;
        this.queues = queues;
        this.reclassifier = reclassifier;
    }

    @Value("${qr.signing.secret:}")
    private String signingSecret;

    private String currentOwner() throws Exception {
        return spotifyService.getCurrentUserProfile().getId();
    }

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, String>>> search(
            @RequestParam String track,
            @RequestParam(name = "limit", defaultValue = "25") int limit) {
        try {
            List<Map<String, String>> results = spotifyService.searchTrackSummaries(track, limit);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(List.of(Map.of("error", e.getMessage() == null ? "unknown" : e.getMessage())));
        }
    }

    @GetMapping("/requests")
    public ResponseEntity<?> getRequests() {
        try {
            String owner = currentOwner();
            var valid = queues.getValid(owner).stream().map(RequestRecord::toMap).collect(Collectors.toList());
            var invalid = queues.getInvalid(owner).stream().map(RequestRecord::toMap).collect(Collectors.toList());
            return ResponseEntity.ok(Map.of("valid", valid, "invalid", invalid));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "not authenticated"));
        }
    }

    @PostMapping("/request")
    public ResponseEntity<String> requestTrack(@RequestParam("owner") String owner,
                                            @RequestParam(value = "sig", required = false) String sig,
                                            @RequestBody Map<String, String> body) {
        String uri = body.get("uri");
        if (uri == null || uri.isBlank()) {
            return ResponseEntity.badRequest().body("Missing uri");
        }

        try {
            if (signingSecret != null && !signingSecret.isEmpty()) {
                if (sig == null || !sig.equals(hmac(owner, signingSecret))) {
                    return ResponseEntity.status(403).body("invalid signature");
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(500).body("sig error");
        }

        String canonicalUri = UriUtils.canonicalTrackUri(uri);
        try {
            if (queues.containsUri(owner, canonicalUri)) {
                return ResponseEntity.ok("Track already requested: " + canonicalUri);
            }

            Track track = spotifyService.getTrack(canonicalUri);
            Artist artistInfo = spotifyService.getArtist(track.getArtists()[0].getId());
            List<String> artistGenres = Arrays.asList(artistInfo.getGenres());
            boolean explicit = track.getIsExplicit();

            RequestRecord rec = classificationService.classify(
                owner,
                track.getName(),
                track.getArtists()[0].getName(),
                artistGenres,
                explicit,
                canonicalUri
            );

            queues.add(owner, rec);
            return ResponseEntity.ok("Track requested (uri=" + canonicalUri + ")");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }



    @PostMapping("/addToPlaylist")
    public ResponseEntity<String> addToPlaylist(@RequestBody Map<String, String> body) {
        String trackTitle = body.get("track");
        String playlistId = body.get("playlistId");
        if (trackTitle == null || playlistId == null) {
            return ResponseEntity.badRequest().body("Missing track or playlistId");
        }
        try {
            Paging<Track> result = spotifyService.searchTracks(trackTitle, 1);
            if (result.getItems().length == 0) {
                return ResponseEntity.status(404).body("Track not found for title: " + trackTitle);
            }
            Track track = result.getItems()[0];
            String uri = UriUtils.canonicalTrackUri(track.getUri());

            spotifyService.addTrackToPlaylistByUri(playlistId, uri);

            String owner = currentOwner();
            queues.removeByUri(owner, uri);

            return ResponseEntity.ok("Track added to playlist: " + track.getName());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error adding track: " + e.getMessage());
        }
    }

    @PostMapping("/addToPlaylistByUri")
    public ResponseEntity<String> addToPlaylistByUri(@RequestBody Map<String, String> body) {
        String uri = body.get("trackUri");
        String playlistId = body.get("playlistId");
        if (uri == null || playlistId == null) {
            return ResponseEntity.badRequest().body("Missing trackUri or playlistId");
        }
        String canonicalUri = UriUtils.canonicalTrackUri(uri);
        try {
            Track track = spotifyService.getTrack(canonicalUri);
            spotifyService.addTrackToPlaylistByUri(playlistId, canonicalUri);
            String owner = currentOwner();
            queues.removeByUri(owner, canonicalUri);
            return ResponseEntity.ok("Track added: " + track.getName());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error adding track: " + e.getMessage());
        }
    }

    @GetMapping("/allowExplicit")
    public ResponseEntity<Map<String, Object>> isAllowExplicit() {
        try {
            String owner = currentOwner();
            return ResponseEntity.ok(Map.of("allowExplicit", classificationService.isAllowExplicit(owner)));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/allowExplicit")
    public ResponseEntity<String> setAllowExplicit(@RequestBody Map<String, Object> body) {
        try {
            boolean allow = Boolean.TRUE.equals(body.get("allowExplicit"));
            String owner = currentOwner();
            classificationService.setAllowExplicit(owner, allow);
            reclassifier.reclassifyAllForOwner(owner);
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    private static String hmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
