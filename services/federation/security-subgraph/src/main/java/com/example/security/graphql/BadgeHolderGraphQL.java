package com.example.security.graphql;

import com.example.security.model.BadgeHolder;
import com.example.security.model.BadgeHolder.AccessLevel;
import com.example.security.model.BadgeHolder.Clearance;
import com.example.security.model.Person;
import com.example.security.repository.BadgeHolderRepository;
import com.example.security.timing.TimingContext;
import io.smallrye.graphql.api.federation.Resolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Source;

import java.util.List;
import java.util.UUID;

/**
 * GraphQL API for Security/BadgeHolder subgraph.
 * This subgraph owns BadgeHolder and extends Person with badge data.
 * All DB operations are timed and recorded in TimingContext.
 */
@GraphQLApi
@ApplicationScoped
public class BadgeHolderGraphQL {

    @Inject
    BadgeHolderRepository badgeHolderRepository;

    @Inject
    TimingContext timingContext;

    @Query("badgeHolders")
    @Description("Get all badge holders")
    public List<BadgeHolder> getAllBadgeHolders() {
        return timingContext.measureOperation("db_query", () -> badgeHolderRepository.listAll());
    }

    @Query("badgeHolder")
    @Description("Get a badge holder by ID")
    public BadgeHolder getBadgeHolder(@Name("id") String id) {
        return timingContext.measureOperation("db_query", () -> badgeHolderRepository.findById(id));
    }

    @Query("badgeHolderByPersonId")
    @Description("Get a badge holder by person ID")
    public BadgeHolder getBadgeHolderByPersonId(@Name("personId") String personId) {
        return timingContext.measureOperation("db_query", () -> badgeHolderRepository.findByPersonId(personId).orElse(null));
    }

    @Query("badgeHolderByBadgeNumber")
    @Description("Get a badge holder by badge number")
    public BadgeHolder getBadgeHolderByBadgeNumber(@Name("badgeNumber") String badgeNumber) {
        return timingContext.measureOperation("db_query", () -> badgeHolderRepository.findByBadgeNumber(badgeNumber).orElse(null));
    }

    @Query("badgeHoldersByAccessLevel")
    @Description("Get badge holders by access level")
    public List<BadgeHolder> getBadgeHoldersByAccessLevel(@Name("accessLevel") AccessLevel accessLevel) {
        return timingContext.measureOperation("db_query", () -> badgeHolderRepository.findByAccessLevel(accessLevel));
    }

    @Query("activeBadgeHolders")
    @Description("Get all active badge holders")
    public List<BadgeHolder> getActiveBadgeHolders() {
        return timingContext.measureOperation("db_query", () -> badgeHolderRepository.findAllActive());
    }

    /**
     * Federation entity resolver for BadgeHolder.
     * Called when other subgraphs reference a BadgeHolder by ID.
     */
    @Resolver
    public BadgeHolder resolveBadgeHolder(@Name("id") String id) {
        return timingContext.measureOperation("db_resolve", () -> badgeHolderRepository.findById(id));
    }

    /**
     * Federation entity resolver for Person.
     * Resolves Person entities when the router needs badge information.
     */
    @Resolver
    public Person resolvePerson(@Name("id") String id) {
        return new Person(id);
    }

    /**
     * Field resolver that adds 'badge' field to Person.
     * This is resolved by the security-subgraph when a Person is queried.
     */
    @Description("The badge holder record for this person (if they have a badge)")
    public BadgeHolder badge(@Source Person person) {
        if (person.getId() == null) {
            return null;
        }
        return timingContext.measureOperation("db_resolve", () -> badgeHolderRepository.findByPersonId(person.getId()).orElse(null));
    }

    @Mutation("provisionBadge")
    @Description("Provision a badge for a person")
    @Transactional
    public BadgeHolder provisionBadge(
            @Name("personId") String personId,
            @Name("accessLevel") AccessLevel accessLevel,
            @Name("clearance") Clearance clearance) {

        // Check if already provisioned
        if (timingContext.measureOperation("db_read", () -> badgeHolderRepository.findByPersonId(personId)).isPresent()) {
            return null; // Already has a badge
        }

        String id = "badge-" + UUID.randomUUID().toString().substring(0, 8);
        String badgeNumber = "B" + System.currentTimeMillis() % 100000;

        BadgeHolder badgeHolder = new BadgeHolder(id, personId, badgeNumber, accessLevel, clearance);
        timingContext.measureOperation("db_write", () -> { badgeHolderRepository.persist(badgeHolder); return null; });
        return badgeHolder;
    }

    @Mutation("changeAccessLevel")
    @Description("Change a badge holder's access level")
    @Transactional
    public BadgeHolder changeAccessLevel(
            @Name("id") String id,
            @Name("newAccessLevel") AccessLevel newAccessLevel) {

        BadgeHolder badgeHolder = timingContext.measureOperation("db_read", () -> badgeHolderRepository.findById(id));
        if (badgeHolder == null) {
            return null;
        }
        badgeHolder.accessLevel = newAccessLevel;
        return badgeHolder;
    }

    @Mutation("changeClearance")
    @Description("Change a badge holder's clearance level")
    @Transactional
    public BadgeHolder changeClearance(
            @Name("id") String id,
            @Name("newClearance") Clearance newClearance) {

        BadgeHolder badgeHolder = timingContext.measureOperation("db_read", () -> badgeHolderRepository.findById(id));
        if (badgeHolder == null) {
            return null;
        }
        badgeHolder.clearance = newClearance;
        return badgeHolder;
    }

    @Mutation("revokeBadge")
    @Description("Revoke a badge (mark as inactive)")
    @Transactional
    public BadgeHolder revokeBadge(@Name("id") String id) {
        BadgeHolder badgeHolder = timingContext.measureOperation("db_read", () -> badgeHolderRepository.findById(id));
        if (badgeHolder == null) {
            return null;
        }
        badgeHolder.active = false;
        return badgeHolder;
    }
}
