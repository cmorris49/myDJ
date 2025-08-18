package com.mydj.backend.controller;

import com.mydj.backend.service.SpotifyService;
import com.mydj.backend.util.UriUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.Paging;

import java.util.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@RestController
public class PlaylistController {

    private final SpotifyService spotifyService;
    private String selectedPlaylistId = null;

    public PlaylistController(SpotifyService spotifyService) {
        this.spotifyService = spotifyService;
    }

    @GetMapping("/playlists")
    public ResponseEntity<List<Map<String, String>>> getPlaylists() {
        try {
            Paging<PlaylistSimplified> paging = spotifyService.getCurrentUserPlaylists(50);
            List<Map<String, String>> out = new ArrayList<>();
            for (PlaylistSimplified p : paging.getItems()) {
                out.add(Map.of("name", p.getName(), "id", p.getId()));
            }
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(List.of(Map.of("error", e.getMessage())));
        }
    }

    @PostMapping("/selectPlaylist")
    public ResponseEntity<String> selectPlaylist(@RequestBody Map<String, String> body) {
        selectedPlaylistId = body.get("playlistId");
        return ResponseEntity.ok("Playlist selected: " + selectedPlaylistId);
    }

    @DeleteMapping("/clearPlaylist")
    public ResponseEntity<String> clearPlaylist(@RequestBody Map<String, String> body) {
        String pid = body.get("playlistId");
        try {
            // fetch up to 100 items
            Paging<PlaylistTrack> itemsPage = spotifyService.getPlaylistTracks(pid, 100);
            PlaylistTrack[] items = itemsPage.getItems();
            if (items.length == 0) {
                return ResponseEntity.ok("Playlist is already empty.");
            }

            JsonArray jsonArray = new JsonArray();
            for (PlaylistTrack item : items) {
                if (item.getTrack() instanceof Track t) {
                    String uri = t.getUri();
                    JsonObject obj = new JsonObject();
                    obj.addProperty("uri", UriUtils.canonicalTrackUri(uri));
                    jsonArray.add(obj);
                }
            }

            spotifyService.removeItemsFromPlaylist(pid, jsonArray);
            return ResponseEntity.ok("Cleared " + items.length + " tracks from playlist.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error clearing playlist: " + e.getMessage());
        }
    }

    @GetMapping("/playlists/{id}/tracks")
    public ResponseEntity<List<Map<String, String>>> getPlaylistTracks(@PathVariable("id") String playlistId) {
        try {
            Paging<PlaylistTrack> paging = spotifyService.getPlaylistTracks(playlistId, 50);
            List<Map<String, String>> response = new ArrayList<>();
            for (PlaylistTrack pt : paging.getItems()) {
                if (pt.getTrack() instanceof Track t) {
                    response.add(Map.of(
                        "name",   t.getName(),
                        "artist", t.getArtists()[0].getName(),
                        "uri",    UriUtils.canonicalTrackUri(t.getUri())
                    ));
                }
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Collections.emptyList());
        }
    }
}
