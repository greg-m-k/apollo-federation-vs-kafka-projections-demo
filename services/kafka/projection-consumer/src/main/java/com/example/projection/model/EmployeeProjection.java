package com.example.projection.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Local projection of Employee data from Employment events.
 */
@Entity
@Table(name = "employee_projections")
public class EmployeeProjection extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "person_id", nullable = false)
    public String personId;

    @Column(nullable = false)
    public String title;

    @Column(nullable = false)
    public String department;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal salary;

    @Column(nullable = false)
    public boolean active = true;

    @Column(name = "last_updated", nullable = false)
    public Instant lastUpdated;

    @Column(name = "event_version")
    public Long eventVersion = 0L;

    public EmployeeProjection() {
    }

    public void updateFrom(String personId, String title, String department,
                           BigDecimal salary, boolean active) {
        this.personId = personId;
        this.title = title;
        this.department = department;
        this.salary = salary;
        this.active = active;
        this.lastUpdated = Instant.now();
        this.eventVersion++;
    }
}
