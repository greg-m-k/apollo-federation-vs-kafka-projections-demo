package com.example.employment.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "employees")
public class Employee extends PanacheEntityBase {

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

    public Employee() {
    }

    public Employee(String id, String personId, String title, String department, BigDecimal salary) {
        this.id = id;
        this.personId = personId;
        this.title = title;
        this.department = department;
        this.salary = salary;
        this.active = true;
    }
}
