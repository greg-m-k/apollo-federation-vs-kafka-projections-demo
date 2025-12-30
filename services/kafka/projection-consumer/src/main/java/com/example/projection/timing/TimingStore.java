package com.example.projection.timing;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for recent propagation timing records.
 * Keeps the most recent timing for each entity ID.
 */
@ApplicationScoped
public class TimingStore {

    // Keep timing for each entity (personId, employeeId, etc.)
    private final Map<String, PropagationTiming> timings = new ConcurrentHashMap<>();

    // Keep only last N entries to prevent memory growth
    private static final int MAX_ENTRIES = 1000;

    public void record(PropagationTiming timing) {
        // Simple eviction: if too many entries, clear old ones
        if (timings.size() >= MAX_ENTRIES) {
            // Remove oldest half
            timings.keySet().stream()
                .limit(MAX_ENTRIES / 2)
                .toList()
                .forEach(timings::remove);
        }
        timings.put(timing.entityId(), timing);
    }

    public Optional<PropagationTiming> get(String entityId) {
        return Optional.ofNullable(timings.get(entityId));
    }

    public Map<String, PropagationTiming> getAll() {
        return Map.copyOf(timings);
    }

    public void clear() {
        timings.clear();
    }
}
