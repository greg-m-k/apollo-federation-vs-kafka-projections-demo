package com.example.projection.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Tracks processed events for idempotency.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent extends PanacheEntityBase {

    @Id
    public String eventId;

    @Column(nullable = false)
    public String topic;

    @Column(name = "event_offset", nullable = false)
    public Long offset;

    @Column(name = "partition_id", nullable = false)
    public Integer partitionId;

    @Column(name = "processed_at", nullable = false)
    public Instant processedAt;

    public ProcessedEvent() {
    }

    public ProcessedEvent(String eventId, String topic, Long offset, Integer partitionId) {
        this.eventId = eventId;
        this.topic = topic;
        this.offset = offset;
        this.partitionId = partitionId;
        this.processedAt = Instant.now();
    }
}
