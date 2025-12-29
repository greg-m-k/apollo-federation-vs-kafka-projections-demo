package com.example.security.repository;

import com.example.security.model.BadgeHolder;
import com.example.security.model.BadgeHolder.AccessLevel;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class BadgeHolderRepository implements PanacheRepositoryBase<BadgeHolder, String> {

    public Optional<BadgeHolder> findByPersonId(String personId) {
        return find("personId", personId).firstResultOptional();
    }

    public Optional<BadgeHolder> findByBadgeNumber(String badgeNumber) {
        return find("badgeNumber", badgeNumber).firstResultOptional();
    }

    public List<BadgeHolder> findByAccessLevel(AccessLevel accessLevel) {
        return find("accessLevel", accessLevel).list();
    }

    public List<BadgeHolder> findAllActive() {
        return find("active", true).list();
    }
}
