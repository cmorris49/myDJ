package com.mydj.backend.service;

import com.mydj.backend.model.RequestRecord;

import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.Track;

import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Used to reclassify songs (valid or invalid) after the user makes
 * a preference change for which types of songs they would like to accept.
 */
@Service
public class RequestReclassifier {

  private final SpotifyService spotifyService;
  private final RequestClassificationService classificationService;
  private final RequestQueueService queues;

  public RequestReclassifier(SpotifyService spotifyService,
                              RequestClassificationService classificationService,
                              RequestQueueService queues) {
      this.spotifyService = spotifyService;
      this.classificationService = classificationService;
      this.queues = queues;
  }

  public void reclassifyAllForOwner(String owner) {
      var snapshot = new ArrayList<RequestRecord>();
      snapshot.addAll(queues.getValid(owner));
      snapshot.addAll(queues.getInvalid(owner));
      if (snapshot.isEmpty()) return;

      Map<String, Track> trackCache = new HashMap<>();
      Map<String, Artist> artistCache = new HashMap<>();

      var rebuilt = new ArrayList<RequestRecord>(snapshot.size());

      for (RequestRecord r : snapshot) {
          String uri = com.mydj.backend.util.UriUtils.canonicalTrackUri(r.getUri());
          Track track = trackCache.computeIfAbsent(uri, u -> {
              try { return spotifyService.getTrack(u); } catch (Exception e) { return null; }
          });
          if (track == null) { rebuilt.add(r); continue; }

          java.util.Set<String> genreSet = new java.util.LinkedHashSet<>();
          for (se.michaelthelin.spotify.model_objects.specification.ArtistSimplified a : track.getArtists()) {
              Artist full = artistCache.computeIfAbsent(a.getId(), id -> {
                  try { return spotifyService.getArtist(id); } catch (Exception e) { return null; }
              });
              if (full != null && full.getGenres() != null) {
                  for (String g : full.getGenres()) {
                      if (g != null && !g.isBlank()) genreSet.add(g.toLowerCase(java.util.Locale.ROOT).trim());
                  }
              }
          }
          java.util.List<String> artistGenres = new java.util.ArrayList<>(genreSet);

          RequestRecord re = classificationService.classify(
              owner,
              track.getName(),
              track.getArtists()[0].getName(),
              artistGenres,
              track.getIsExplicit(),
              uri
          );
          rebuilt.add(re);
      }
      queues.replaceAll(owner, rebuilt);
  }

}

