package com.mydj.backend.service;

import com.mydj.backend.model.RequestRecord;
import com.mydj.backend.util.UriUtils;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.Track;
import java.util.*;

/**
 * Used to reclassify songs (valid or invalid) after the user makes
 * a preference change for which types of songs they would like to accept.
 */
public class RequestReclassifier {
    private final SpotifyService spotifyService;
    private final RequestClassificationService classificationService;
    private final RequestQueueService queues;

    public RequestReclassifier(SpotifyService s, RequestClassificationService c, RequestQueueService q) {
        this.spotifyService = s; this.classificationService = c; this.queues = q;
    }

    public void reclassifyAllForOwner(String owner) {
        var all = new ArrayList<RequestRecord>();
        all.addAll(queues.getValid(owner));
        all.addAll(queues.getInvalid(owner));

        queues.clearAll(owner); 

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
                queues.add(owner, re);
            } catch (Exception e) {
                queues.add(owner, r); 
            }
        }
    }
}

