package com.example.query.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "person_projections")
public class PersonProjection extends PanacheEntityBase {

    @Id
    public String id;

    public String name;

    public String email;

    @Column(name = "hire_date")
    public LocalDate hireDate;

    public boolean active;

    @Column(name = "last_updated")
    public Instant lastUpdated;

    @Column(name = "event_version")
    public Long eventVersion;
}
