package com.example.employment.repository;

import com.example.employment.model.Employee;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class EmployeeRepository implements PanacheRepositoryBase<Employee, String> {

    public Optional<Employee> findByPersonId(String personId) {
        return find("personId", personId).firstResultOptional();
    }

    public List<Employee> findByDepartment(String department) {
        return find("department", department).list();
    }

    public List<Employee> findAllActive() {
        return find("active", true).list();
    }
}
