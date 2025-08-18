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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@RestController
public class RequestController {

    private final SpotifyService spotifyService;
    private final RequestClassificationService classificationService;

    private final List<RequestRecord> validRequests = new CopyOnWriteArrayList<>();
    private final List<RequestRecord> invalidRequests = new CopyOnWriteArrayList<>();

    public RequestController(SpotifyService spotifyService,
                             RequestClassificationService classificationService) {
        this.spotifyService = spotifyService;
        this.classificationService = classificationService;
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
    public ResponseEntity<Map<String, List<Map<String, String>>>> getRequests() {
        List<Map<String, String>> valid = validRequests.stream()
            .map(RequestRecord::toMap)
            .collect(Collectors.toList());
        List<Map<String, String>> invalid = invalidRequests.stream()
            .map(RequestRecord::toMap)
            .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("valid", valid, "invalid", invalid));
    }

    @PostMapping("/request")
    public ResponseEntity<String> requestTrack(@RequestBody Map<String, String> body) {
        String uri = body.get("uri");
        if (uri == null || uri.isBlank()) {
            return ResponseEntity.badRequest().body("Missing uri");
        }

        String canonicalUri = UriUtils.canonicalTrackUri(uri);
        try {
            // dedupe
            boolean already = validRequests.stream().anyMatch(r -> r.getUri().equals(canonicalUri)) ||
                    invalidRequests.stream().anyMatch(r -> r.getUri().equals(canonicalUri));
            if (already) {
                return ResponseEntity.ok("Track already requested: " + canonicalUri);
            }

            Track track = spotifyService.getTrack(canonicalUri);
            Artist artistInfo = spotifyService.getArtist(track.getArtists()[0].getId());
            List<String> artistGenres = Arrays.asList(artistInfo.getGenres());
            boolean explicit = track.getIsExplicit();

            RequestRecord rec = classificationService.classify(
                track.getName(),
                track.getArtists()[0].getName(),
                artistGenres,
                explicit,
                canonicalUri
            );

            if (rec.isValid()) {
                validRequests.add(rec);
            } else {
                invalidRequests.add(rec);
            }

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

            validRequests.removeIf(r -> r.getUri().equals(uri));
            invalidRequests.removeIf(r -> r.getUri().equals(uri));

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
            validRequests.removeIf(r -> r.getUri().equals(canonicalUri));
            invalidRequests.removeIf(r -> r.getUri().equals(canonicalUri));
            return ResponseEntity.ok("Track added: " + track.getName());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error adding track: " + e.getMessage());
        }
    }

    @GetMapping("/allowExplicit")
    public ResponseEntity<Map<String, Object>> getAllowExplicit() {
        return ResponseEntity.ok(Map.of("allowExplicit", classificationService.isAllowExplicit()));
    }

    @PostMapping("/allowExplicit")
    public ResponseEntity<Void> setAllowExplicit(@RequestParam boolean value) {
        classificationService.setAllowExplicit(value);
        reclassifyAllRequests(); 
        return ResponseEntity.ok().build();
    }

    private void reclassifyAllRequests() {
        List<RequestRecord> all = new ArrayList<>();
        all.addAll(validRequests);
        all.addAll(invalidRequests);

        List<RequestRecord> newValid = new ArrayList<>();
        List<RequestRecord> newInvalid = new ArrayList<>();

        for (RequestRecord r : all) {
            try {
                Track track = spotifyService.getTrack(r.getUri());
                Artist artistInfo = spotifyService.getArtist(track.getArtists()[0].getId());
                List<String> artistGenres = Arrays.asList(artistInfo.getGenres());

                RequestRecord re = classificationService.classify(
                    track.getName(),
                    track.getArtists()[0].getName(),
                    artistGenres,
                    track.getIsExplicit(),
                    r.getUri()
                );
                if (re.isValid()) newValid.add(re); else newInvalid.add(re);
            } catch (Exception e) {
                if (r.isValid()) newValid.add(r); else newInvalid.add(r);
            }
        }

        validRequests.clear();
        validRequests.addAll(newValid);
        invalidRequests.clear();
        invalidRequests.addAll(newInvalid);
    }
}
