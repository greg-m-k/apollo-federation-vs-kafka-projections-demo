package com.example.projection.repository;

import com.example.projection.model.ProcessedEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProcessedEventRepository implements PanacheRepositoryBase<ProcessedEvent, String> {

    public boolean hasProcessed(String eventId) {
        return findById(eventId) != null;
    }
}
