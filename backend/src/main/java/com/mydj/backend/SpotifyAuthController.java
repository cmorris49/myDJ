package com.mydj.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import java.net.URI;

@RestController
public class SpotifyAuthController {

    private final List<String> requestedTracks = new ArrayList<>();
    private final String clientId = "9c6f0d97f6c945a19cb13ed371b63e73";
    private final String clientSecret = "ddf690d987db4f25b5ae381ae841a229";
    private final URI redirectUri = SpotifyHttpManager.makeUri("https://5334156839ee.ngrok-free.app/callback");

    private final SpotifyApi spotifyApi = new SpotifyApi.Builder()
            .setClientId(clientId)
            .setClientSecret(clientSecret)
            .setRedirectUri(redirectUri)
            .build();

    @GetMapping("/login")
    public String login() {
        AuthorizationCodeUriRequest uriRequest = spotifyApi.authorizationCodeUri()
                .scope("playlist-modify-public playlist-modify-private")
                .build();

        URI uri = uriRequest.execute();
        return "<a href=\"" + uri + "\">Login with Spotify</a>";
    }

    @GetMapping("/callback")
    public String callback(@RequestParam("code") String code) {
        try {
            AuthorizationCodeRequest codeRequest = spotifyApi.authorizationCode(code).build();
            AuthorizationCodeCredentials creds = codeRequest.execute();

            spotifyApi.setAccessToken(creds.getAccessToken());
            spotifyApi.setRefreshToken(creds.getRefreshToken());

            return "✅ Access token: " + creds.getAccessToken();
        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }

    @GetMapping("/search")
    public Object search(@RequestParam("track") String track) {
        try {
            var searchRequest = spotifyApi.searchTracks(track).limit(5).build();
            var result = searchRequest.execute();
            return result.getItems(); 
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    @PostMapping("/request")
    public String requestTrack(@RequestBody Map<String, String> payload) {
        String trackName = payload.get("track");
        if (trackName == null || trackName.isBlank()) {
            return "❌ Missing 'track' in request body";
        }
        try {
            var searchRequest = spotifyApi.searchTracks(trackName).limit(1).build();
            var result = searchRequest.execute();

            if (result.getItems().length == 0) {
                return "❌ No matching track found.";
            }

            var track = result.getItems()[0];
            var trackUri = track.getUri(); 
            var addRequest = spotifyApi.addItemsToPlaylist("02JFtWDptavNruBEZowVvd", new String[]{trackUri}).build();
            addRequest.execute();
            requestedTracks.add(track.getName());
            return "✅ Track added to playlist: " + track.getName();
        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }

    @GetMapping("/requests")
    public List<String> getRequests() {
        return requestedTracks;
    }
}







