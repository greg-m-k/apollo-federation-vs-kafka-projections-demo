package com.example.security.timing;

import jakarta.enterprise.context.RequestScoped;
import java.util.HashMap;
import java.util.Map;

/**
 * Request-scoped context to collect timing metrics during request processing.
 * Captures actual measured times for each operation.
 */
@RequestScoped
public class TimingContext {

    private final Map<String, Long> timings = new HashMap<>();
    private long requestStartTime;

    public void startRequest() {
        this.requestStartTime = System.currentTimeMillis();
    }

    public void recordTiming(String operation, long durationMs) {
        timings.put(operation, durationMs);
    }

    public <T> T measureOperation(String operation, java.util.function.Supplier<T> task) {
        long start = System.currentTimeMillis();
        T result = task.get();
        long duration = System.currentTimeMillis() - start;
        timings.put(operation, duration);
        return result;
    }

    public Map<String, Long> getTimings() {
        return new HashMap<>(timings);
    }

    public long getTotalTime() {
        if (requestStartTime > 0) {
            return System.currentTimeMillis() - requestStartTime;
        }
        return timings.values().stream().mapToLong(Long::longValue).sum();
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Long> entry : timings.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        sb.append(",\"total\":").append(getTotalTime());
        sb.append("}");
        return sb.toString();
    }
}
