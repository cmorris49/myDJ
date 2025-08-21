package com.mydj.desktop.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydj.desktop.model.DeviceInfo;
import com.mydj.desktop.model.PlaylistInfo;
import com.mydj.desktop.model.PlaylistTrack;
import com.mydj.desktop.model.RequestRecord;
import javafx.application.Platform;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ApiClient {

    private final String baseUrl;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final CookieManager cookieManager;
    private volatile String lastSetDeviceId = null;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.client = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .version(HttpClient.Version.HTTP_1_1) 
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private HttpRequest build(String method, String path, String body) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path));
        String csrf = getCsrfToken();
        if (csrf != null) {
            b.header("X-XSRF-TOKEN", csrf);
        }
        if ("GET".equalsIgnoreCase(method)) b.GET();
        else b.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body))
              .header("Content-Type", "application/json");
        return b.build();
    }

    private String getCsrfToken() {
        try {
            URI uri = URI.create(baseUrl);
            List<HttpCookie> cookies = cookieManager.getCookieStore().get(uri);
            for (HttpCookie c : cookies) {
                if ("XSRF-TOKEN".equalsIgnoreCase(c.getName()) || "CSRF-TOKEN".equalsIgnoreCase(c.getName())) {
                    return c.getValue();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void log(String msg) {
        System.out.println("[ApiClient] " + msg);
    }

    public void checkLogin(java.util.function.Consumer<Boolean> onResult) {
        client.sendAsync(build("GET", "/me", null), java.net.http.HttpResponse.BodyHandlers.ofString())
            .whenComplete((resp, err) -> {
                boolean ok = (err == null && resp.statusCode() == 200);
                javafx.application.Platform.runLater(() -> onResult.accept(ok));
            });
    }
    
    private void withRetries(Supplier<CompletableFuture<String>> attempt,
                             Consumer<String> onSuccess,
                             Consumer<Throwable> onError,
                             int maxAttempts) {
        attempt.get().whenComplete((body, err) -> {
            if (err == null) {
                Platform.runLater(() -> onSuccess.accept(body));
            } else {
                String m = err.getMessage() != null ? err.getMessage().toLowerCase() : "";
                boolean transientErr = m.contains("goaway") || m.contains("connection reset") || m.contains("broken pipe");
                if (transientErr && maxAttempts > 1) {
                    int attemptNum = (4 - maxAttempts) + 1;
                    long delay = Math.min(1000L * (1 << attemptNum), 2000);
                    new Timer().schedule(new TimerTask() {
                        @Override public void run() {
                            withRetries(attempt, onSuccess, onError, maxAttempts - 1);
                        }
                    }, delay);
                } else {
                    Platform.runLater(() -> onError.accept(err));
                }
            }
        });
    }

    public void getPlaylists(Consumer<List<PlaylistInfo>> onSuccess, Consumer<Throwable> onError) {
        withRetries(
            () -> client.sendAsync(build("GET", "/playlists", null), HttpResponse.BodyHandlers.ofString())
                        .thenApply(resp -> { log("GET /playlists status="+resp.statusCode()); return resp.body(); }),
            body -> {
                try {
                    List<Map<String,String>> list = mapper.readValue(body, new TypeReference<>() {});
                    List<PlaylistInfo> out = new ArrayList<>();
                    for (var m : list) out.add(new PlaylistInfo(m.get("name"), m.get("id")));
                    Platform.runLater(() -> onSuccess.accept(out));
                } catch (Exception e) {
                    Platform.runLater(() -> onError.accept(e));
                }
            },
            onError,
            3
        );
    }

    public void getPlaylistTracks(String playlistId, Consumer<List<PlaylistTrack>> onSuccess, Consumer<Throwable> onError) {
        withRetries(
            () -> client.sendAsync(build("GET", "/playlists/" + playlistId + "/tracks", null), HttpResponse.BodyHandlers.ofString())
                        .thenApply(resp -> { log("GET /playlists/"+playlistId+"/tracks status="+resp.statusCode()); return resp.body(); }),
            body -> {
                try {
                    List<Map<String,String>> list = mapper.readValue(body, new TypeReference<>() {});
                    List<PlaylistTrack> out = new ArrayList<>();
                    for (var m : list) out.add(new PlaylistTrack(m.get("name"), m.get("artist"), m.get("uri")));
                    Platform.runLater(() -> onSuccess.accept(out));
                } catch (Exception e) {
                    Platform.runLater(() -> onError.accept(e));
                }
            },
            onError,
            2
        );
    }

    public void getDevices(Consumer<List<DeviceInfo>> onSuccess, Consumer<Throwable> onError) {
        withRetries(
            () -> client.sendAsync(build("GET", "/devices", null), HttpResponse.BodyHandlers.ofString())
                        .thenApply(resp -> { log("GET /devices status="+resp.statusCode()); return resp.body(); }),
            body -> {
                try {
                    List<Map<String,String>> list = mapper.readValue(body, new TypeReference<>() {});
                    List<DeviceInfo> out = new ArrayList<>();
                    for (var m : list) out.add(new DeviceInfo(m.get("name"), m.get("id")));
                    Platform.runLater(() -> onSuccess.accept(out));
                } catch (Exception e) {
                    Platform.runLater(() -> onError.accept(e));
                }
            },
            onError,
            3
        );
    }

    public void getRequests(Consumer<Map<String, List<RequestRecord>>> onSuccess, Consumer<Throwable> onError) {
        withRetries(
            () -> client.sendAsync(build("GET", "/requests", null), HttpResponse.BodyHandlers.ofString())
                        .thenApply(resp -> { log("GET /requests status="+resp.statusCode()); return resp.body(); }),
            body -> {
                try {
                    Map<String,List<Map<String,String>>> raw = mapper.readValue(body, new TypeReference<>() {});
                    Map<String,List<RequestRecord>> out = new HashMap<>();
                    out.put("valid", convert(raw.getOrDefault("valid", List.of())));
                    out.put("invalid", convert(raw.getOrDefault("invalid", List.of())));
                    Platform.runLater(() -> onSuccess.accept(out));
                } catch (Exception e) {
                    Platform.runLater(() -> onError.accept(e));
                }
            },
            onError,
            3
        );
    }

    private List<RequestRecord> convert(List<Map<String,String>> raw) {
        List<RequestRecord> list = new ArrayList<>();
        for (var m : raw) {
            list.add(new RequestRecord(
                m.get("title"), m.get("artist"), m.getOrDefault("genre","unknown"),
                Boolean.parseBoolean(m.getOrDefault("explicit","false")), m.getOrDefault("uri","")));
        }
        return list;
    }

    public void sendAllowedGenres(List<String> allowed, Runnable onSuccess, Consumer<Throwable> onError) {
        try {
            String body = mapper.writeValueAsString(allowed);
            client.sendAsync(build("POST", "/genres", body), HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> { log("POST /genres status="+resp.statusCode()); return resp; })
                .thenAccept(r -> Platform.runLater(onSuccess))
                .exceptionally(t -> { Platform.runLater(() -> onError.accept(t)); return null; });
        } catch (Exception e) {
            Platform.runLater(() -> onError.accept(e));
        }
    }

    public void addToPlaylistByUri(String uri, String playlistId, Consumer<String> onSuccess) {
        try {
            String payload = mapper.writeValueAsString(Map.of("trackUri", uri, "playlistId", playlistId));
            client.sendAsync(build("POST", "/addToPlaylistByUri", payload), HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> { log("POST /addToPlaylistByUri status="+resp.statusCode()); return resp; })
                .thenAccept(r -> Platform.runLater(() -> onSuccess.accept(r.body())));
        } catch (Exception e) {
            Platform.runLater(() -> onSuccess.accept("Error: " + e.getMessage()));
        }
    }

    public void addToPlaylist(String title, String playlistId, Consumer<String> onSuccess) {
        try {
            String payload = mapper.writeValueAsString(Map.of("track", title, "playlistId", playlistId));
            client.sendAsync(build("POST", "/addToPlaylist", payload), HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> { log("POST /addToPlaylist status="+resp.statusCode()); return resp; })
                .thenAccept(r -> Platform.runLater(() -> onSuccess.accept(r.body())));
        } catch (Exception e) {
            Platform.runLater(() -> onSuccess.accept("Error: " + e.getMessage()));
        }
    }

    public void getGenreSeeds(Consumer<List<String>> onSuccess, Consumer<Throwable> onError) {
        withRetries(
            () -> client.sendAsync(build("GET", "/genres", null), HttpResponse.BodyHandlers.ofString())
                        .thenApply(resp -> { log("GET /genres seeds status="+resp.statusCode()); return resp.body(); }),
            body -> {
                try {
                    List<String> seeds = mapper.readValue(body, new TypeReference<List<String>>() {});
                    Platform.runLater(() -> onSuccess.accept(seeds));
                } catch (Exception e) {
                    Platform.runLater(() -> onError.accept(e));
                }
            },
            onError,
            2
        );
    }

    public void getAllowedGenres(Consumer<List<String>> onSuccess, Consumer<Throwable> onError) {
        withRetries(
            () -> client.sendAsync(build("GET", "/allowedGenres", null), HttpResponse.BodyHandlers.ofString())
                        .thenApply(resp -> { log("GET /allowedGenres status="+resp.statusCode()); return resp.body(); }),
            body -> {
                try {
                    List<String> allowed = mapper.readValue(body, new TypeReference<List<String>>() {});
                    Platform.runLater(() -> onSuccess.accept(allowed));
                } catch (Exception e) {
                    Platform.runLater(() -> onError.accept(e));
                }
            },
            onError,
            2
        );
    }

    public void getPlaybackState(Consumer<com.mydj.desktop.model.PlaybackState> onSuccess, Consumer<Throwable> onError) {
        withRetries(
            () -> client.sendAsync(build("GET", "/playback", null), HttpResponse.BodyHandlers.ofString())
                        .thenApply(resp -> { log("GET /playback status="+resp.statusCode()); return resp.body(); }),
            body -> {
                if (body == null || body.isBlank()) {
                    Platform.runLater(() -> onSuccess.accept(new com.mydj.desktop.model.PlaybackState()));
                } else {
                    try {
                        com.mydj.desktop.model.PlaybackState state = mapper.readValue(body, new TypeReference<>() {});
                        Platform.runLater(() -> onSuccess.accept(state));
                    } catch (Exception e) {
                        Platform.runLater(() -> onError.accept(e));
                    }
                }
            },
            onError,
            3
        );
    }

    public void play(Runnable onSuccess, Consumer<Throwable> onError) {
        client.sendAsync(build("POST", "/playback/play", null), HttpResponse.BodyHandlers.ofString())
            .thenApply(resp -> { log("POST /playback/play status="+resp.statusCode()); return resp; })
            .thenAccept(r -> Platform.runLater(onSuccess))
            .exceptionally(t -> { Platform.runLater(() -> onError.accept(t)); return null; });
    }

    public void pause(Runnable onSuccess, Consumer<Throwable> onError) {
        client.sendAsync(build("POST", "/playback/pause", null), HttpResponse.BodyHandlers.ofString())
            .thenApply(resp -> { log("POST /playback/pause status="+resp.statusCode()); return resp; })
            .thenAccept(r -> Platform.runLater(onSuccess))
            .exceptionally(t -> { Platform.runLater(() -> onError.accept(t)); return null; });
    }

    public void next(Runnable onSuccess, Consumer<Throwable> onError) {
        client.sendAsync(build("POST", "/playback/next", null), HttpResponse.BodyHandlers.ofString())
            .thenApply(resp -> { log("POST /playback/next status="+resp.statusCode()); return resp; })
            .thenAccept(r -> Platform.runLater(onSuccess))
            .exceptionally(t -> { Platform.runLater(() -> onError.accept(t)); return null; });
    }

    public void previous(Runnable onSuccess, Consumer<Throwable> onError) {
        client.sendAsync(build("POST", "/playback/previous", null), HttpResponse.BodyHandlers.ofString())
            .thenApply(resp -> { log("POST /playback/previous status="+resp.statusCode()); return resp; })
            .thenAccept(r -> Platform.runLater(onSuccess))
            .exceptionally(t -> { Platform.runLater(() -> onError.accept(t)); return null; });
    }

    public void seek(int positionMs, Runnable onSuccess, Consumer<Throwable> onError) {
        try {
            String payload = mapper.writeValueAsString(Map.of("positionMs", positionMs));
            client.sendAsync(build("POST", "/playback/seek", payload), HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> { log("POST /playback/seek status="+resp.statusCode()); return resp; })
                .thenAccept(r -> Platform.runLater(onSuccess))
                .exceptionally(t -> {
                    Platform.runLater(() -> onError.accept(t));
                    return null;
                });
        } catch (Exception e) {
            Platform.runLater(() -> onError.accept(e));
        }
    }

    public void setVolume(int percent, Runnable onSuccess, Consumer<Throwable> onError) {
        try {
            String payload = mapper.writeValueAsString(Map.of("volumePercent", percent));
            client.sendAsync(build("POST", "/playback/volume", payload), HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> { log("POST /playback/volume status="+resp.statusCode()); return resp; })
                .thenAccept(r -> Platform.runLater(onSuccess))
                .exceptionally(t -> {
                    Platform.runLater(() -> onError.accept(t));
                    return null;
                });
        } catch (Exception e) {
            Platform.runLater(() -> onError.accept(e));
        }
    }

    public void queue(String uri, Runnable onSuccess, Consumer<Throwable> onError) {
        try {
            String payload = mapper.writeValueAsString(Map.of("uri", uri));
            client.sendAsync(build("POST", "/playback/queue", payload), HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> { log("POST /playback/queue status=" + resp.statusCode()); return resp; })
                .thenAccept(r -> Platform.runLater(onSuccess))
                .exceptionally(t -> { Platform.runLater(() -> onError.accept(t)); return null; });
        } catch (Exception e) {
            Platform.runLater(() -> onError.accept(e));
        }
    }

    // Read current backend setting
    public void getAllowExplicit(Consumer<Boolean> onSuccess, Consumer<Throwable> onError) {
        withRetries(
            () -> client.sendAsync(build("GET", "/allowExplicit", null), HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> { log("GET /allowExplicit status="+resp.statusCode()); return resp.body(); }),
            body -> {
                try {
                    boolean v = mapper.readTree(body).path("allowExplicit").asBoolean(false);
                    Platform.runLater(() -> onSuccess.accept(v));
                } catch (Exception e) {
                    Platform.runLater(() -> onError.accept(e));
                }
            },
            onError,
            2
        );
    }

    public void setAllowExplicit(boolean value, Runnable onSuccess, Consumer<Throwable> onError) {
        String path = "/allowExplicit?value=" + value;
        withRetries(
            () -> client
                .sendAsync(build("POST", path, null), HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> { 
                    log("POST " + path + " status=" + resp.statusCode()); 
                    return resp.body() == null ? "" : resp.body(); 
                }),
            _ignoredBody -> Platform.runLater(onSuccess),   
            onError,
            1
        );
    }

    public void setDevice(String deviceId, Runnable onSuccess, Consumer<Throwable> onError) {
        if (deviceId == null || deviceId.isBlank()) {
            onError.accept(new IllegalArgumentException("deviceId is blank"));
            return;
        }
        if (Objects.equals(deviceId, lastSetDeviceId)) {
            javafx.application.Platform.runLater(onSuccess);
            return;
        }

        withRetries(
            () -> client
                .sendAsync(build("POST", "/setDevice?deviceId=" + URLEncoder.encode(deviceId, StandardCharsets.UTF_8), null),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> { log("POST /setDevice status=" + resp.statusCode()); return resp.body()==null?"":resp.body(); }),
            body -> {
                lastSetDeviceId = deviceId;
                javafx.application.Platform.runLater(onSuccess);
            },
            onError,
            1
        );
    }
}
