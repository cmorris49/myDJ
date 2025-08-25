package com.mydj.backend.controller;

import com.mydj.backend.service.SpotifyService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

@RestController
public class AuthController {

    private final SpotifyService spotifyService;

    public AuthController(SpotifyService spotifyService) {
        this.spotifyService = spotifyService;
    }

    @GetMapping("/login")
    public void login(
            @RequestParam(value = "switch", required = false, defaultValue = "false") boolean forceSwitch,
            HttpServletResponse resp
    ) throws IOException {
        String authorizeUrl = spotifyService.buildAuthorizationUri().toString();
        if (forceSwitch) {
            authorizeUrl = addShowDialog(authorizeUrl);
        }
        resp.sendRedirect(authorizeUrl);
    }

    private static String addShowDialog(String url) {
        if (url.contains("show_dialog=true")) return url;
        return url + (url.contains("?") ? "&" : "?") + "show_dialog=true";
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("code") String code) {
        try {
            spotifyService.exchangeAuthorizationCode(code); 
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/")).build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/?auth=error")).build();
        }
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me() {
        if (!spotifyService.hasUserAuth()) {
            return ResponseEntity.status(401).body(Map.of("error", "NOT_AUTHENTICATED"));
        }
        try {
            var me = spotifyService.getCurrentUserProfile();
            return ResponseEntity.ok(Map.of("id", me.getId(), "name", me.getDisplayName()));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "NOT_AUTHENTICATED"));
        }
    }

    @GetMapping("/access-token")
    public ResponseEntity<Map<String, String>> accessToken() {
        String token = spotifyService.getAccessToken();
        return ResponseEntity.ok(Map.of("accessToken", token == null ? "" : token));
    }

    @PostMapping("/api/logout")
    public ResponseEntity<Void> logout() {
        spotifyService.logout();               
        return ResponseEntity.noContent().build(); 
    }
}
