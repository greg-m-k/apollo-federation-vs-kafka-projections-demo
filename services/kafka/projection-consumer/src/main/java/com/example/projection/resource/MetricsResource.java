package com.example.projection.resource;

import com.example.projection.repository.BadgeProjectionRepository;
import com.example.projection.repository.EmployeeProjectionRepository;
import com.example.projection.repository.PersonProjectionRepository;
import com.example.projection.timing.PropagationTiming;
import com.example.projection.timing.TimingStore;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Metrics endpoint for monitoring projection freshness.
 */
@Path("/api/metrics")
@Produces(MediaType.APPLICATION_JSON)
public class MetricsResource {

    @Inject
    PersonProjectionRepository personRepository;

    @Inject
    EmployeeProjectionRepository employeeRepository;

    @Inject
    BadgeProjectionRepository badgeRepository;

    @Inject
    TimingStore timingStore;

    @GET
    @Path("/freshness")
    public Map<String, Object> getFreshness() {
        Instant now = Instant.now();

        Instant personLastUpdate = personRepository.getLastUpdateTime().orElse(null);
        Instant employeeLastUpdate = employeeRepository.getLastUpdateTime().orElse(null);
        Instant badgeLastUpdate = badgeRepository.getLastUpdateTime().orElse(null);

        return Map.of(
            "person", buildFreshnessInfo(personLastUpdate, now),
            "employee", buildFreshnessInfo(employeeLastUpdate, now),
            "badge", buildFreshnessInfo(badgeLastUpdate, now),
            "timestamp", now.toString()
        );
    }

    @GET
    @Path("/counts")
    public Map<String, Long> getCounts() {
        return Map.of(
            "persons", personRepository.count(),
            "employees", employeeRepository.count(),
            "badges", badgeRepository.count()
        );
    }

    private Map<String, Object> buildFreshnessInfo(Instant lastUpdate, Instant now) {
        if (lastUpdate == null) {
            return Map.of(
                "lastUpdate", "never",
                "lagMs", -1,
                "lagHuman", "N/A"
            );
        }

        long lagMs = Duration.between(lastUpdate, now).toMillis();
        return Map.of(
            "lastUpdate", lastUpdate.toString(),
            "lagMs", lagMs,
            "lagHuman", formatLag(lagMs)
        );
    }

    private String formatLag(long lagMs) {
        if (lagMs < 1000) {
            return lagMs + "ms";
        } else if (lagMs < 60000) {
            return String.format("%.1fs", lagMs / 1000.0);
        } else {
            return String.format("%.1fm", lagMs / 60000.0);
        }
    }

    /**
     * Get propagation timing breakdown for a specific entity.
     * Returns timing for: outbox->kafka, kafka->consumer, consumer->projection
     */
    @GET
    @Path("/timing/{entityId}")
    public Response getTiming(@PathParam("entityId") String entityId) {
        return timingStore.get(entityId)
            .map(timing -> Response.ok(Map.of(
                "entityId", timing.entityId(),
                "eventType", timing.eventType(),
                "outboxToKafkaMs", timing.outboxToKafkaMs(),
                "consumerToProjectionMs", timing.consumerToProjectionMs(),
                "totalPropagationMs", timing.totalPropagationMs(),
                "eventCreatedAt", timing.eventCreatedAt().toString(),
                "kafkaReceivedAt", timing.kafkaReceivedAt().toString(),
                "projectionUpdatedAt", timing.projectionUpdatedAt().toString()
            )).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "No timing data for " + entityId))
                .build());
    }
}
