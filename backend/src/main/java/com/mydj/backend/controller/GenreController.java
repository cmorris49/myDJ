package com.mydj.backend.controller;

import com.mydj.backend.service.RequestClassificationService;
import com.mydj.backend.service.RequestReclassifier;
import com.mydj.backend.service.SpotifyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class GenreController {

    private final RequestClassificationService classificationService;
    private final SpotifyService spotifyService;
    private final RequestReclassifier reclassifier;

    public GenreController(RequestClassificationService c, SpotifyService s, RequestReclassifier r) {
        this.classificationService = c; this.spotifyService = s; this.reclassifier = r;
    }

    private String owner() throws Exception {
        return spotifyService.getCurrentUserProfile().getId();
    }

    @GetMapping("/genres")
    public ResponseEntity<List<String>> getAvailableGenres() {
        List<String> seeds = spotifyService.fetchAvailableGenreSeeds();
        if (seeds.isEmpty()) {
            seeds = List.of(
                "pop", "rock", "hip-hop", "classical", "alternative", "jazz", "blues", "country",
                "dance", "electronic", "folk", "heavy-metal", "reggae", "r-n-b",
                "punk", "soul", "indie", "edm"
            );
        }
        return ResponseEntity.ok(seeds);
    }

    @GetMapping("/allowedGenres")
    public ResponseEntity<?> getAllowedGenres() {
        try {
            return ResponseEntity.ok(classificationService.getAllowedGenres(owner()));
        } catch (Exception e) {
            return ResponseEntity.status(401).body("not authenticated");
        }
    }

    @PostMapping("/genres")
    public ResponseEntity<?> setAllowedGenres(@RequestBody List<String> genres) {
        try {
            String owner = owner();
            classificationService.setAllowedGenres(owner, genres);
            reclassifier.reclassifyAllForOwner(owner);
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            return ResponseEntity.status(401).body("not authenticated");
        }
    }
}

