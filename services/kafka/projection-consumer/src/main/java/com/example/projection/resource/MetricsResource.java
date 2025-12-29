package com.example.projection.resource;

import com.example.projection.repository.BadgeProjectionRepository;
import com.example.projection.repository.EmployeeProjectionRepository;
import com.example.projection.repository.PersonProjectionRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

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
}
