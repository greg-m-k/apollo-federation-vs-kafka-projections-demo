package com.example.query.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "badge_projections")
public class BadgeProjection extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "person_id")
    public String personId;

    @Column(name = "badge_number")
    public String badgeNumber;

    @Column(name = "access_level")
    public String accessLevel;

    public String clearance;

    public boolean active;

    @Column(name = "last_updated")
    public Instant lastUpdated;

    @Column(name = "event_version")
    public Long eventVersion;
}
