package com.example.projection.repository;

import com.example.projection.model.PersonProjection;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class PersonProjectionRepository implements PanacheRepositoryBase<PersonProjection, String> {

    public Optional<Instant> getLastUpdateTime() {
        return find("ORDER BY lastUpdated DESC")
            .firstResultOptional()
            .map(p -> p.lastUpdated);
    }
}
