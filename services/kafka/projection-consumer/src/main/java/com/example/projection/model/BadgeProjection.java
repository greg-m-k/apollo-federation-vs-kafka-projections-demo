package com.example.projection.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Local projection of BadgeHolder data from Security CDC events.
 */
@Entity
@Table(name = "badge_projections")
public class BadgeProjection extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "person_id", nullable = false)
    public String personId;

    @Column(name = "badge_number", nullable = false)
    public String badgeNumber;

    @Column(name = "access_level", nullable = false)
    public String accessLevel;

    @Column(nullable = false)
    public String clearance;

    @Column(nullable = false)
    public boolean active = true;

    @Column(name = "last_updated", nullable = false)
    public Instant lastUpdated;

    @Column(name = "event_version")
    public Long eventVersion = 0L;

    public BadgeProjection() {
    }

    public void updateFrom(String personId, String badgeNumber, String accessLevel,
                           String clearance, boolean active) {
        this.personId = personId;
        this.badgeNumber = badgeNumber;
        this.accessLevel = accessLevel;
        this.clearance = clearance;
        this.active = active;
        this.lastUpdated = Instant.now();
        this.eventVersion++;
    }
}
