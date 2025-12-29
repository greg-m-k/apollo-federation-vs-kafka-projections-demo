package com.example.query.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "employee_projections")
public class EmployeeProjection extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "person_id")
    public String personId;

    public String title;

    public String department;

    public BigDecimal salary;

    public boolean active;

    @Column(name = "last_updated")
    public Instant lastUpdated;

    @Column(name = "event_version")
    public Long eventVersion;
}
