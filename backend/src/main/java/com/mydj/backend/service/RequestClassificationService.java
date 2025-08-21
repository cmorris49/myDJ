package com.mydj.backend.service;

import com.mydj.backend.model.RequestRecord;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class RequestClassificationService {

    private static final String GLOBAL = "GLOBAL";

    private static final class OwnerConfig {
        final Set<String> allowedGenres = new CopyOnWriteArraySet<>();
        volatile boolean allowExplicit = false;
    }

    private final Map<String, OwnerConfig> byOwner = new ConcurrentHashMap<>();

    private OwnerConfig cfg(String owner) {
        return byOwner.computeIfAbsent(owner == null ? GLOBAL : owner, k -> new OwnerConfig());
    }

    public boolean isAllowExplicit(String owner) { 
        return cfg(owner).allowExplicit; 
    }

    public void setAllowExplicit(String owner, boolean allow) { 
        cfg(owner).allowExplicit = allow; 
    }

    public List<String> getAllowedGenres(String owner) {
        return new ArrayList<>(cfg(owner).allowedGenres);
    }

    public void setAllowedGenres(String owner, Collection<String> genres) {
        OwnerConfig c = cfg(owner);
        c.allowedGenres.clear();
        if (genres == null) return;
        for (String g : genres) {
            if (g == null) continue;
            String norm = g.toLowerCase(Locale.ROOT).trim();
            if (!norm.isEmpty()) c.allowedGenres.add(norm);
        }
    }

    private static String norm(String s) {
        if (s == null) return "";
        String x = s.toLowerCase(Locale.ROOT).trim();
        x = x.replace("&", "and").replace("+", "and");
        x = x.replaceAll("[^a-z0-9]+", ""); 
        if (x.equals("rnb")) x = "randb";   
        return x;
    }

    private String matchToAllowedGenre(String owner, List<String> artistGenres) {
        Set<String> allowed = cfg(owner).allowedGenres;
        if (artistGenres == null || artistGenres.isEmpty() || allowed.isEmpty()) return null;

        List<String> allowedList = new ArrayList<>(allowed);
        List<String> allowedNorm = new ArrayList<>(allowedList.size());
        for (String a : allowedList) allowedNorm.add(norm(a));

        for (String g : artistGenres) {
            if (g == null) continue;
            String gn = norm(g);
            for (int i = 0; i < allowedList.size(); i++) {
                String an = allowedNorm.get(i);
                if (gn.equals(an) || gn.contains(an) || an.contains(gn)) {
                    return allowedList.get(i); 
                }
            }
        }
        return null;
    }

    public RequestRecord classify(String owner,
                                  String title,
                                  String artist,
                                  List<String> artistGenres,
                                  boolean explicit,
                                  String uri) {
        Set<String> allowed = cfg(owner).allowedGenres;
        boolean filterActive = !allowed.isEmpty();
        String matched = matchToAllowedGenre(owner, artistGenres);

        String displayGenre = "unknown";
        if (matched != null) displayGenre = matched;
        else if (artistGenres != null && !artistGenres.isEmpty() && artistGenres.get(0) != null)
            displayGenre = artistGenres.get(0);

        boolean allowExplicit = cfg(owner).allowExplicit;
        boolean valid = (!filterActive || matched != null) && (allowExplicit || !explicit);

        return new RequestRecord(title, artist, displayGenre, explicit, uri, valid);
    }


    public boolean isAllowExplicit() { 
        return isAllowExplicit(GLOBAL); 
    }

    public void setAllowExplicit(boolean allow) { 
        setAllowExplicit(GLOBAL, allow); 
    }

    public List<String> getAllowedGenres() { 
        return getAllowedGenres(GLOBAL); 
    }

    public void setAllowedGenres(Collection<String> genres) { 
        setAllowedGenres(GLOBAL, genres); 
    }

    public RequestRecord classify(String title,
                                  String artist,
                                  List<String> artistGenres,
                                  boolean explicit,
                                  String uri) {
        return classify(GLOBAL, title, artist, artistGenres, explicit, uri);
    }
}
