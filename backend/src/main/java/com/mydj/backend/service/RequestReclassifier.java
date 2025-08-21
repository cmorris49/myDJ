package com.mydj.backend.service;

import com.mydj.backend.model.RequestRecord;
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
      var all = new ArrayList<RequestRecord>();
      all.addAll(queues.getValid(owner));
      all.addAll(queues.getInvalid(owner));
      if (all.isEmpty()) return;

      var rebuilt = new ArrayList<RequestRecord>(all.size()); 

      for (RequestRecord r : all) {
        try {
            String uri = com.mydj.backend.util.UriUtils.canonicalTrackUri(r.getUri());
            String g = r.getGenre();
            boolean haveGenre = g != null && !g.isBlank() && !"unknown".equalsIgnoreCase(g);

            if (haveGenre) {
                var rec = classificationService.classify(
                    owner,
                    r.getTitle(),
                    r.getArtist(),
                    java.util.List.of(g),
                    r.isExplicit(),
                    uri
                );
                rebuilt.add(rec);
            } else {
                var track = spotifyService.getTrack(uri);
                var artist = spotifyService.getArtist(track.getArtists()[0].getId());
                var artistGenres = java.util.Arrays.asList(artist.getGenres());

                var rec = classificationService.classify(
                    owner,
                    track.getName(),
                    track.getArtists()[0].getName(),
                    artistGenres,
                    track.getIsExplicit(),
                    uri
                );
                rebuilt.add(rec);
            }
          } catch (Exception e) {
              rebuilt.add(r); 
          }
      }
    queues.replaceAll(owner, rebuilt);
  }
}

