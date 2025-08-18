package com.mydj.backend.util;

public class UriUtils {
    public static String extractTrackId(String uri) {
        if (uri.startsWith("spotify:track:")) {
            return uri.substring("spotify:track:".length());
        } else if (uri.contains("track/")) {
            int idx = uri.indexOf("track/");
            String id = uri.substring(idx + "track/".length());
            int q = id.indexOf('?');
            if (q != -1) id = id.substring(0, q);
            return id;
        } else {
            return uri; 
        }
    }

    public static String canonicalTrackUri(String rawOrUri) {
        String id = extractTrackId(rawOrUri);
        return "spotify:track:" + id;
    }
}

