package com.mydj.backend.controller;

import com.mydj.backend.service.RequestClassificationService;
import com.mydj.backend.service.SpotifyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class GenreController {

    private final RequestClassificationService classificationService;
    private final SpotifyService spotifyService;

    public GenreController(RequestClassificationService classificationService,
                           SpotifyService spotifyService) {
        this.classificationService = classificationService;
        this.spotifyService = spotifyService;
    }

    @GetMapping("/genres")
    public ResponseEntity<List<String>> getAvailableGenres() {
        List<String> seeds = spotifyService.fetchAvailableGenreSeeds();
        if (seeds.isEmpty()) {
            // fallback if none fetched
            seeds = List.of(
                "pop", "rock", "hip-hop", "classical", "alternative", "jazz", "blues", "country",
                "dance", "electronic", "folk", "heavy-metal", "reggae", "r-n-b",
                "punk", "soul", "indie", "edm"
            );
        }
        return ResponseEntity.ok(seeds);
    }

    @GetMapping("/allowedGenres")
    public ResponseEntity<List<String>> getAllowedGenres() {
        return ResponseEntity.ok(classificationService.getAllowedGenres());
    }

    // set/update allowed genres
    @PostMapping("/genres")
    public ResponseEntity<String> setAllowedGenres(@RequestBody List<String> genres) {
        classificationService.setAllowedGenres(genres);
        return ResponseEntity.ok("Allowed genres updated: " + String.join(", ", classificationService.getAllowedGenres()));
    }
}

