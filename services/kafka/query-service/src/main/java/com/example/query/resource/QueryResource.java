package com.example.query.resource;

import com.example.query.dto.ComposedView;
import com.example.query.model.BadgeProjection;
import com.example.query.model.EmployeeProjection;
import com.example.query.model.PersonProjection;
import com.example.query.repository.BadgeProjectionRepository;
import com.example.query.repository.EmployeeProjectionRepository;
import com.example.query.repository.PersonProjectionRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Query resource for CDC architecture.
 * All queries are served from local projections - NO network calls to other services.
 * This demonstrates the key advantage of CDC: single local query for composed views.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class QueryResource {

    @Inject
    PersonProjectionRepository personRepository;

    @Inject
    EmployeeProjectionRepository employeeRepository;

    @Inject
    BadgeProjectionRepository badgeRepository;

    /**
     * Get a composed view of a person with all their data.
     * This is a SINGLE LOCAL QUERY - no network calls to other services.
     * Compare this to GraphQL Federation which makes 3 network calls.
     */
    @GET
    @Path("/composed/{personId}")
    public Response getComposedView(@PathParam("personId") String personId) {
        long startTime = System.currentTimeMillis();

        // All data comes from local database - no network calls
        PersonProjection person = personRepository.findById(personId);
        if (person == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Person not found", "personId", personId))
                .build();
        }

        EmployeeProjection employee = employeeRepository.findByPersonId(personId).orElse(null);
        BadgeProjection badge = badgeRepository.findByPersonId(personId).orElse(null);

        Instant now = Instant.now();
        long maxLag = calculateMaxLag(person.lastUpdated,
            employee != null ? employee.lastUpdated : null,
            badge != null ? badge.lastUpdated : null,
            now);

        ComposedView.FreshnessInfo freshness = new ComposedView.FreshnessInfo(
            person.lastUpdated,
            employee != null ? employee.lastUpdated : null,
            badge != null ? badge.lastUpdated : null,
            maxLag,
            formatLag(maxLag)
        );

        ComposedView view = new ComposedView(
            person.id, person.name, person.email, person.hireDate, person.active,
            employee != null ? employee.id : null,
            employee != null ? employee.title : null,
            employee != null ? employee.department : null,
            employee != null ? employee.salary : null,
            employee != null && employee.active,
            badge != null ? badge.id : null,
            badge != null ? badge.badgeNumber : null,
            badge != null ? badge.accessLevel : null,
            badge != null ? badge.clearance : null,
            badge != null && badge.active,
            freshness
        );

        long queryTime = System.currentTimeMillis() - startTime;

        return Response.ok(view)
            .header("X-Query-Time-Ms", queryTime)
            .header("X-Services-Called", 1)  // Just local DB
            .header("X-Data-Freshness", formatLag(maxLag))
            .build();
    }

    @GET
    @Path("/persons")
    public List<PersonProjection> getAllPersons() {
        return personRepository.listAll();
    }

    @GET
    @Path("/persons/{id}")
    public Response getPerson(@PathParam("id") String id) {
        PersonProjection person = personRepository.findById(id);
        if (person == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(person).build();
    }

    @GET
    @Path("/employees")
    public List<EmployeeProjection> getAllEmployees() {
        return employeeRepository.listAll();
    }

    @GET
    @Path("/badges")
    public List<BadgeProjection> getAllBadges() {
        return badgeRepository.listAll();
    }

    @GET
    @Path("/freshness")
    public Map<String, Object> getFreshness() {
        Instant now = Instant.now();
        Instant personLast = personRepository.getLastUpdateTime().orElse(null);
        Instant employeeLast = employeeRepository.getLastUpdateTime().orElse(null);
        Instant badgeLast = badgeRepository.getLastUpdateTime().orElse(null);

        return Map.of(
            "person", buildFreshnessInfo(personLast, now),
            "employee", buildFreshnessInfo(employeeLast, now),
            "badge", buildFreshnessInfo(badgeLast, now),
            "timestamp", now.toString()
        );
    }

    private long calculateMaxLag(Instant personLast, Instant employeeLast, Instant badgeLast, Instant now) {
        long maxLag = 0;
        if (personLast != null) {
            maxLag = Math.max(maxLag, Duration.between(personLast, now).toMillis());
        }
        if (employeeLast != null) {
            maxLag = Math.max(maxLag, Duration.between(employeeLast, now).toMillis());
        }
        if (badgeLast != null) {
            maxLag = Math.max(maxLag, Duration.between(badgeLast, now).toMillis());
        }
        return maxLag;
    }

    private String formatLag(long lagMs) {
        if (lagMs < 0) return "N/A";
        if (lagMs < 1000) return lagMs + "ms";
        if (lagMs < 60000) return String.format("%.1fs", lagMs / 1000.0);
        return String.format("%.1fm", lagMs / 60000.0);
    }

    private Map<String, Object> buildFreshnessInfo(Instant lastUpdate, Instant now) {
        if (lastUpdate == null) {
            return Map.of("lastUpdate", "never", "lagMs", -1, "lagHuman", "N/A");
        }
        long lagMs = Duration.between(lastUpdate, now).toMillis();
        return Map.of(
            "lastUpdate", lastUpdate.toString(),
            "lagMs", lagMs,
            "lagHuman", formatLag(lagMs)
        );
    }
}
