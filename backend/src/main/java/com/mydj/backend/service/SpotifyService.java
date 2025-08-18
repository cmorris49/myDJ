package com.mydj.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.mydj.backend.util.UriUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.enums.AuthorizationScope;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;
import se.michaelthelin.spotify.model_objects.miscellaneous.Device;
import se.michaelthelin.spotify.model_objects.specification.*;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRefreshRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.*;

@Service
public class SpotifyService {

    @Value("${spotify.clientId}")     private String clientId;       
    @Value("${spotify.clientSecret}") private String clientSecret;   
    @Value("${spotify.redirectUri}")  private String redirectUri;    

    private SpotifyApi spotifyApi;

    // Persistence
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path dataDir = Paths.get(
        System.getenv().getOrDefault("MYDJ_DATA_DIR",
            Paths.get(System.getProperty("user.home"), ".mydj").toString())
    );
    private final Path tokenFile = dataDir.resolve("tokens.json");

    private String accessToken;
    private String refreshToken;
    private String currentDeviceId;

    // Init 
    @PostConstruct
    public void init() {
        this.spotifyApi = new SpotifyApi.Builder()
            .setClientId(clientId)
            .setClientSecret(clientSecret)
            .setRedirectUri(SpotifyHttpManager.makeUri(redirectUri))
            .build();
        loadTokensIfPresent();
        if (spotifyApi.getAccessToken() == null || spotifyApi.getAccessToken().isBlank()) {
            try {
                initClientCredentials();
            } catch (Exception e) {
                System.err.println("Failed to init client credentials: " + e.getMessage());
            }
        }
    }

    // Authorization/token management 

    private void initClientCredentials() throws Exception {
        var creds = spotifyApi.clientCredentials().build().execute();
        spotifyApi.setAccessToken(creds.getAccessToken());
    }

    public URI buildAuthorizationUri() {
        AuthorizationCodeUriRequest uriRequest = spotifyApi.authorizationCodeUri()
            .scope(new AuthorizationScope[] {
                AuthorizationScope.PLAYLIST_READ_PRIVATE,
                AuthorizationScope.PLAYLIST_READ_COLLABORATIVE,
                AuthorizationScope.PLAYLIST_MODIFY_PUBLIC,
                AuthorizationScope.PLAYLIST_MODIFY_PRIVATE,
                AuthorizationScope.USER_READ_PLAYBACK_STATE,
                AuthorizationScope.USER_READ_CURRENTLY_PLAYING,
                AuthorizationScope.USER_MODIFY_PLAYBACK_STATE,
                AuthorizationScope.USER_READ_EMAIL
            })
            .show_dialog(false)
            .build();
        return uriRequest.execute();
    }

    public void exchangeAuthorizationCode(String code) throws Exception {
        AuthorizationCodeRequest codeRequest = spotifyApi.authorizationCode(code).build();
        AuthorizationCodeCredentials creds = codeRequest.execute();

        this.accessToken  = creds.getAccessToken();
        this.refreshToken = creds.getRefreshToken();

        spotifyApi.setAccessToken(accessToken);
        if (refreshToken != null && !refreshToken.isBlank()) {
            spotifyApi.setRefreshToken(refreshToken);
        }
        persistTokens();
    }

    public synchronized void refreshIfNeeded() {
        try {
            String rt = spotifyApi.getRefreshToken();
            if (rt != null && !rt.isBlank()) {
                AuthorizationCodeRefreshRequest refreshRequest = spotifyApi.authorizationCodeRefresh().build();
                var creds = refreshRequest.execute();
                this.accessToken = creds.getAccessToken();
                spotifyApi.setAccessToken(accessToken);
                persistTokens();
                return;
            }

            if (spotifyApi.getAccessToken() == null || spotifyApi.getAccessToken().isBlank()) {
                initClientCredentials();
            }
        } catch (Exception e) {
            System.err.println("Failed to refresh token: " + e.getMessage());
        }
    }

    private synchronized void persistTokens() {
        try {
            Files.createDirectories(dataDir);
            Path tmp = Files.createTempFile(dataDir, "tokens", ".tmp");
            Map<String, String> out = new HashMap<>();
            out.put("accessToken",  accessToken  == null ? "" : accessToken);
            out.put("refreshToken", refreshToken == null ? "" : refreshToken);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), out);
            try {
                Files.move(tmp, tokenFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicUnsupported) {
                Files.move(tmp, tokenFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("persistTokens failed: " + e.getMessage());
        }
    }

    private synchronized void loadTokensIfPresent() {
        try {
            if (Files.exists(tokenFile)) {
                var map = objectMapper.readValue(Files.readString(tokenFile),
                        new TypeReference<Map<String,String>>() {});
                accessToken  = map.getOrDefault("accessToken",  "");
                refreshToken = map.getOrDefault("refreshToken", "");
                if (!accessToken.isBlank())  spotifyApi.setAccessToken(accessToken);
                if (!refreshToken.isBlank()) spotifyApi.setRefreshToken(refreshToken);
            }
        } catch (Exception e) {
            System.err.println("loadTokens failed: " + e.getMessage());
        }
    }

    public se.michaelthelin.spotify.model_objects.specification.User getCurrentUserProfile() throws Exception {
        refreshIfNeeded();
        return spotifyApi.getCurrentUsersProfile().build().execute();
    }

    // Search/lookup/playlist operations 

    public Paging<Track> searchTracks(String query, int limit) throws Exception {
        refreshIfNeeded();
        int capped = Math.max(1, Math.min(limit, 50));
        return spotifyApi.searchTracks(query).limit(capped).build().execute();
    }

    public List<Map<String, String>> searchTrackSummaries(String query, int limit) throws Exception {
        int capped = Math.max(1, Math.min(limit, 50));
        Paging<Track> result = searchTracks(query, capped);
        Track[] items = result.getItems();
        List<Map<String, String>> out = new ArrayList<>();
        if (items == null || items.length == 0) return out;

        List<String> artistIds = new ArrayList<>();
        for (Track t : items) {
            if (t.getArtists() != null && t.getArtists().length > 0) {
                artistIds.add(t.getArtists()[0].getId());
            }
        }

        Artist[] artists = getSeveralArtists(artistIds);
        Map<String, String> idToGenre = new HashMap<>();
        if (artists != null) {
            for (Artist a : artists) {
                String g = (a.getGenres() != null && a.getGenres().length > 0) ? a.getGenres()[0] : "unknown";
                idToGenre.put(a.getId(), g);
            }
        }

        for (Track t : items) {
            String artistName = "";
            String artistId = "";
            if (t.getArtists() != null && t.getArtists().length > 0) {
                artistName = t.getArtists()[0].getName();
                artistId = t.getArtists()[0].getId();
            }
            String genre = idToGenre.getOrDefault(artistId, "unknown");

            String imageUrl = null;
            try {
                Image[] images = (t.getAlbum() != null) ? t.getAlbum().getImages() : null;
                if (images != null && images.length > 0) {
                    imageUrl = images[images.length - 1].getUrl(); 
                }
            } catch (Exception ignored) {}

            Map<String, String> row = new HashMap<>();
            row.put("name", t.getName());
            row.put("artist", artistName);
            row.put("genre", genre);
            row.put("uri", t.getUri());
            if (imageUrl != null) row.put("imageUrl", imageUrl);
            out.add(row);
        }

        return out;
    }

    private Artist[] getSeveralArtists(List<String> artistIds) throws Exception {
        if (artistIds == null || artistIds.isEmpty()) return new Artist[0];
        String[] ids = artistIds.toArray(new String[0]);
        refreshIfNeeded();
        return spotifyApi.getSeveralArtists(ids).build().execute();
    }

    public Track getTrack(String trackIdOrUri) throws Exception {
        refreshIfNeeded();
        String trackId = UriUtils.extractTrackId(trackIdOrUri);
        return spotifyApi.getTrack(trackId).build().execute();
    }

    public Artist getArtist(String artistId) throws Exception {
        refreshIfNeeded();
        return spotifyApi.getArtist(artistId).build().execute();
    }

    public Paging<se.michaelthelin.spotify.model_objects.specification.PlaylistTrack> getPlaylistTracks(String playlistId, int limit) throws Exception {
        refreshIfNeeded();
        return spotifyApi.getPlaylistsItems(playlistId).limit(limit).build().execute();
    }

    public Paging<PlaylistSimplified> getCurrentUserPlaylists(int limit) throws Exception {
        refreshIfNeeded();
        return spotifyApi.getListOfCurrentUsersPlaylists().limit(limit).build().execute();
    }

    public void addTrackToPlaylistByUri(String playlistId, String uri) throws Exception {
        refreshIfNeeded();
        String canonicalUri = UriUtils.canonicalTrackUri(uri);
        spotifyApi.addItemsToPlaylist(playlistId, new String[]{canonicalUri}).build().execute();
    }

    public void addTrackToPlaylistBySearch(String playlistId, String title) throws Exception {
        refreshIfNeeded();
        Paging<Track> result = spotifyApi.searchTracks(title).limit(1).build().execute();
        if (result.getItems().length == 0) {
            throw new IllegalArgumentException("Track not found for title: " + title);
        }
        String uri = result.getItems()[0].getUri();
        spotifyApi.addItemsToPlaylist(playlistId, new String[]{uri}).build().execute();
    }

    public void queue(String uri, Optional<String> deviceId) throws Exception {
        refreshIfNeeded();
        var builder = spotifyApi.addItemToUsersPlaybackQueue(UriUtils.canonicalTrackUri(uri));
        deviceId.ifPresent(builder::device_id);
        builder.build().execute();
    }

    // Device/playback control 

    public Device[] getAvailableDevices() throws Exception {
        refreshIfNeeded();
        return spotifyApi.getUsersAvailableDevices().build().execute();
    }

    public void transferPlayback(String deviceId) throws Exception {
        refreshIfNeeded();
        JsonArray arr = new JsonArray();
        arr.add(deviceId);
        spotifyApi.transferUsersPlayback(arr).build().execute();
        this.currentDeviceId = deviceId;
    }

    public void play(Optional<String> optDeviceId) throws Exception {
        refreshIfNeeded();
        if (optDeviceId.isPresent()) {
            String deviceId = optDeviceId.get();
            JsonArray arr = new JsonArray();
            arr.add(deviceId);
            spotifyApi.transferUsersPlayback(arr).build().execute();
        }
        spotifyApi.startResumeUsersPlayback().build().execute();
    }

    public void pause() throws Exception {
        refreshIfNeeded();
        spotifyApi.pauseUsersPlayback().build().execute();
    }

    public void next() throws Exception {
        sendPlaybackCommand("POST", "next");
    }

    public void previous() throws Exception {
        sendPlaybackCommand("POST", "previous");
    }

    public void seek(int positionMs) throws Exception {
        refreshIfNeeded();
        spotifyApi.seekToPositionInCurrentlyPlayingTrack(positionMs).build().execute();
    }

    public void setVolume(int volumePercent) throws Exception {
        refreshIfNeeded();
        spotifyApi.setVolumeForUsersPlayback(volumePercent).build().execute();
    }

    public CurrentlyPlayingContext getCurrentPlayback() throws Exception {
        refreshIfNeeded();
        return spotifyApi.getInformationAboutUsersCurrentPlayback().build().execute();
    }

    public void transferPlayback(String deviceId, boolean play) throws Exception {
        refreshIfNeeded();
        JsonArray arr = new JsonArray();
        arr.add(deviceId);
        spotifyApi.transferUsersPlayback(arr).play(play).build().execute();
        this.currentDeviceId = deviceId;
    }

    // Genre seeds 

    public List<String> fetchAvailableGenreSeeds() {
        try {
            refreshIfNeeded();
            String token = spotifyApi.getAccessToken();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.spotify.com/v1/recommendations/available-genre-seeds"))
                .header("Authorization", "Bearer " + token)
                .GET().build();

            HttpResponse<String> resp = HttpClient.newHttpClient()
                .send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                System.err.println("Failed to fetch genre seeds: " + resp.statusCode() + " / " + resp.body());
                return Collections.emptyList();
            }
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode arr = root.get("genres");
            return objectMapper.convertValue(arr, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            System.err.println("Error fetching genre seeds: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // Helpers 

    public String getCurrentDeviceId() { return currentDeviceId; }
    public void setCurrentDeviceId(String id) { this.currentDeviceId = id; }

    public String getAccessToken() {
        refreshIfNeeded();
        return spotifyApi.getAccessToken();
    }

    public void removeItemsFromPlaylist(String playlistId, JsonArray tracks) throws Exception {
        refreshIfNeeded();
        spotifyApi.removeItemsFromPlaylist(playlistId, tracks).build().execute();
    }

    private void sendPlaybackCommand(String method, String path) throws java.io.IOException, java.lang.InterruptedException {
        refreshIfNeeded();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create("https://api.spotify.com/v1/me/player/" + path))
            .header("Authorization", "Bearer " + spotifyApi.getAccessToken());

        if ("PUT".equalsIgnoreCase(method)) builder.PUT(HttpRequest.BodyPublishers.noBody());
        else if ("POST".equalsIgnoreCase(method)) builder.POST(HttpRequest.BodyPublishers.noBody());
        else builder.method(method, HttpRequest.BodyPublishers.noBody());

        HttpResponse<String> resp = HttpClient.newHttpClient()
            .send(builder.build(), HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() >= 400) {
            throw new RuntimeException("Playback command failed: " + resp.statusCode() + " / " + resp.body());
        }
    }
}
