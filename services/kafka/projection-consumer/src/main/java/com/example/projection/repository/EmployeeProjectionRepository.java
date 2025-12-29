package com.example.projection.repository;

import com.example.projection.model.EmployeeProjection;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class EmployeeProjectionRepository implements PanacheRepositoryBase<EmployeeProjection, String> {

    public Optional<EmployeeProjection> findByPersonId(String personId) {
        return find("personId", personId).firstResultOptional();
    }

    public Optional<Instant> getLastUpdateTime() {
        return find("ORDER BY lastUpdated DESC")
            .firstResultOptional()
            .map(e -> e.lastUpdated);
    }
}
