package com.mydj.backend.service;

import com.mydj.backend.model.RequestRecord;
import com.mydj.backend.util.UriUtils;
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
        var all = new ArrayList<RequestRecord>();
        all.addAll(queues.getValid(owner));
        all.addAll(queues.getInvalid(owner));
        if (all.isEmpty()) return;
        
        var rebuilt = new ArrayList<RequestRecord>(all.size()); 

        for (RequestRecord r : all) {
            try {
                String uri = UriUtils.canonicalTrackUri(r.getUri());
                Track track = spotifyService.getTrack(uri);
                Artist artistInfo = spotifyService.getArtist(track.getArtists()[0].getId());
                List<String> artistGenres = Arrays.asList(artistInfo.getGenres());
                boolean explicit = track.getIsExplicit();

                RequestRecord re = classificationService.classify(
                    owner, track.getName(), track.getArtists()[0].getName(),
                    artistGenres, explicit, uri
                );
                rebuilt.add(re);
            } catch (Exception e) {
                rebuilt.add(r); 
            }
        }
      queues.replaceAll(owner, rebuilt);
    }
}

