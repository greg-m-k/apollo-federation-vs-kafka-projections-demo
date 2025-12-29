package com.example.projection.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Local projection of Person data from HR events.
 */
@Entity
@Table(name = "person_projections")
public class PersonProjection extends PanacheEntityBase {

    @Id
    public String id;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public String email;

    @Column(name = "hire_date")
    public LocalDate hireDate;

    @Column(nullable = false)
    public boolean active = true;

    @Column(name = "last_updated", nullable = false)
    public Instant lastUpdated;

    @Column(name = "event_version")
    public Long eventVersion = 0L;

    public PersonProjection() {
    }

    public void updateFrom(String name, String email, LocalDate hireDate, boolean active) {
        this.name = name;
        this.email = email;
        this.hireDate = hireDate;
        this.active = active;
        this.lastUpdated = Instant.now();
        this.eventVersion++;
    }
}
