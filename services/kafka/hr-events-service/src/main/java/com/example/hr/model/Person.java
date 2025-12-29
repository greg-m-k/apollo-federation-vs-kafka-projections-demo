package com.example.hr.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Person entity for the HR Events service.
 */
@Entity
@Table(name = "persons")
public class Person extends PanacheEntityBase {

    @Id
    public String id;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false, unique = true)
    public String email;

    @Column(name = "hire_date")
    public LocalDate hireDate;

    @Column(nullable = false)
    public boolean active = true;

    public Person() {
    }

    public Person(String id, String name, String email, LocalDate hireDate) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.hireDate = hireDate;
        this.active = true;
    }
}
