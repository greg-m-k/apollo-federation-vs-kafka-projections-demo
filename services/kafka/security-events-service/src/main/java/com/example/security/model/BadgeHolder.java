package com.example.security.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "badge_holders")
public class BadgeHolder extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "person_id", nullable = false)
    public String personId;

    @Column(name = "badge_number", nullable = false, unique = true)
    public String badgeNumber;

    @Column(name = "access_level", nullable = false)
    @Enumerated(EnumType.STRING)
    public AccessLevel accessLevel = AccessLevel.STANDARD;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    public Clearance clearance = Clearance.NONE;

    @Column(nullable = false)
    public boolean active = true;

    public BadgeHolder() {
    }

    public BadgeHolder(String id, String personId, String badgeNumber, AccessLevel accessLevel, Clearance clearance) {
        this.id = id;
        this.personId = personId;
        this.badgeNumber = badgeNumber;
        this.accessLevel = accessLevel;
        this.clearance = clearance;
        this.active = true;
    }

    public enum AccessLevel {
        VISITOR,
        STANDARD,
        RESTRICTED,
        ALL_ACCESS
    }

    public enum Clearance {
        NONE,
        CONFIDENTIAL,
        SECRET,
        TOP_SECRET
    }
}
