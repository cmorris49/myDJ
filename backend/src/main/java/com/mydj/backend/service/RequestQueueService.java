package com.mydj.backend.service;

import com.mydj.backend.model.RequestRecord;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class RequestQueueService {

    private static final class Queues {
        final CopyOnWriteArrayList<RequestRecord> valid = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<RequestRecord> invalid = new CopyOnWriteArrayList<>();
    }

    private final ConcurrentMap<String, Queues> byOwner = new ConcurrentHashMap<>();

    private Queues q(String owner) {
        return byOwner.computeIfAbsent(owner, k -> new Queues());
    }

    public List<RequestRecord> getValid(String owner) { return List.copyOf(q(owner).valid); }
    public List<RequestRecord> getInvalid(String owner) { return List.copyOf(q(owner).invalid); }

    public void add(String owner, RequestRecord r) {
        if (r.isValid()) q(owner).valid.add(r);
        else q(owner).invalid.add(r);
    }

    public void removeByUri(String owner, String uri) {
        q(owner).valid.removeIf(rr -> Objects.equals(rr.getUri(), uri));
        q(owner).invalid.removeIf(rr -> Objects.equals(rr.getUri(), uri));
    }

    public boolean containsUri(String owner, String uri) {
        return q(owner).valid.stream().anyMatch(r -> Objects.equals(r.getUri(), uri))
            || q(owner).invalid.stream().anyMatch(r -> Objects.equals(r.getUri(), uri));
    }

    public void clearAll(String owner) { 
      byOwner.remove(owner); 
    }

    public void replaceAll(String owner, List<RequestRecord> records) {
        Queues nq = new Queues();
        for (RequestRecord r : records) {
            if (r.isValid()) nq.valid.add(r); else nq.invalid.add(r);
        }
        byOwner.put(owner, nq);
    }

}
