package com.mydj.backend.service;

import com.mydj.backend.model.RequestRecord;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class RequestClassificationService {

    private final Set<String> allowedGenres = new CopyOnWriteArraySet<>();
    private volatile boolean allowExplicit = false; 

    public boolean isAllowExplicit() { return allowExplicit; }
    public void setAllowExplicit(boolean allow) { this.allowExplicit = allow; }

    public List<String> getAllowedGenres() { return new ArrayList<>(allowedGenres); }

    public void setAllowedGenres(Collection<String> genres) {
        allowedGenres.clear();
        if (genres == null) return;
        for (String g : genres) {
            if (g == null) continue;
            String norm = g.toLowerCase(Locale.ROOT).trim();
            if (!norm.isEmpty()) allowedGenres.add(norm);
        }
    }

    private String matchToAllowedGenre(List<String> artistGenres) {
        if (artistGenres == null || artistGenres.isEmpty() || allowedGenres.isEmpty()) return null;
        for (String g : artistGenres) {
            if (g == null) continue;
            String gl = g.toLowerCase(Locale.ROOT).trim();
            for (String allowed : allowedGenres) {
                if (gl.equals(allowed) || gl.contains(allowed)) return allowed;
            }
        }
        return null;
    }

    public RequestRecord classify(String title,
                                  String artist,
                                  List<String> artistGenres,
                                  boolean explicit,
                                  String uri) {
        boolean filterActive = !allowedGenres.isEmpty();
        String matched = matchToAllowedGenre(artistGenres);

        String displayGenre = "unknown";
        if (matched != null) displayGenre = matched;
        else if (artistGenres != null && !artistGenres.isEmpty() && artistGenres.get(0) != null)
            displayGenre = artistGenres.get(0);

        // allow explicit if toggle is on
        boolean valid = (!filterActive || matched != null) && (allowExplicit || !explicit);

        return new RequestRecord(title, artist, displayGenre, explicit, uri, valid);
    }
}
