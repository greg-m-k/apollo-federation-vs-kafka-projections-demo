package com.example.hr.repository;

import com.example.hr.model.Person;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PersonRepository implements PanacheRepositoryBase<Person, String> {

    public Optional<Person> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }

    public List<Person> findAllActive() {
        return find("active", true).list();
    }
}
