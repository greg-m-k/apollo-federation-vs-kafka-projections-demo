package com.example.hr.repository;

import com.example.hr.model.OutboxEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class OutboxRepository implements PanacheRepository<OutboxEvent> {

    public List<OutboxEvent> findUnpublished() {
        return find("published = false ORDER BY createdAt ASC").list();
    }

    public List<OutboxEvent> findUnpublishedWithLimit(int limit) {
        return find("published = false ORDER BY createdAt ASC")
                .page(0, limit)
                .list();
    }
}
