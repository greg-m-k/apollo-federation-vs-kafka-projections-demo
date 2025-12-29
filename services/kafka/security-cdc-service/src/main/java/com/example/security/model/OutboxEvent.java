package com.example.security.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "aggregate_type", nullable = false)
    public String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    public String aggregateId;

    @Column(name = "event_type", nullable = false)
    public String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String payload;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(nullable = false)
    public boolean published = false;

    @Column(name = "published_at")
    public Instant publishedAt;

    public OutboxEvent() {
    }

    public OutboxEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.published = false;
    }

    public void markPublished() {
        this.published = true;
        this.publishedAt = Instant.now();
    }
}
