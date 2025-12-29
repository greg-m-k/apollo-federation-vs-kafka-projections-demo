package com.example.hr;

import com.example.hr.model.OutboxEvent;
import com.example.hr.repository.OutboxRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for OutboxRepository using H2 in-memory database.
 * These tests run without Docker.
 */
@QuarkusTest
class OutboxRepositoryTest {

    @Inject
    OutboxRepository outboxRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        outboxRepository.deleteAll();

        OutboxEvent event1 = new OutboxEvent("hr.person", "person-001", "PersonCreated",
                "{\"id\":\"person-001\",\"name\":\"John Doe\"}");
        OutboxEvent event2 = new OutboxEvent("hr.person", "person-002", "PersonCreated",
                "{\"id\":\"person-002\",\"name\":\"Jane Smith\"}");
        OutboxEvent event3 = new OutboxEvent("hr.person", "person-001", "PersonUpdated",
                "{\"id\":\"person-001\",\"name\":\"John Updated\"}");
        event3.markPublished(); // Mark this one as published

        outboxRepository.persist(event1);
        outboxRepository.persist(event2);
        outboxRepository.persist(event3);
    }

    @Test
    void testFindUnpublished() {
        List<OutboxEvent> unpublished = outboxRepository.findUnpublished();

        assertThat(unpublished).hasSize(2);
        assertThat(unpublished).allMatch(e -> !e.published);
    }

    @Test
    void testFindUnpublishedWithLimit() {
        List<OutboxEvent> unpublished = outboxRepository.findUnpublishedWithLimit(1);

        assertThat(unpublished).hasSize(1);
        assertThat(unpublished.get(0).eventType).isEqualTo("PersonCreated");
    }

    @Test
    void testMarkPublished() {
        List<OutboxEvent> unpublished = outboxRepository.findUnpublished();
        OutboxEvent event = unpublished.get(0);

        markAsPublished(event.id);

        List<OutboxEvent> remaining = outboxRepository.findUnpublished();
        assertThat(remaining).hasSize(1);
    }

    @Transactional
    void markAsPublished(Long eventId) {
        OutboxEvent event = outboxRepository.findById(eventId);
        event.markPublished();
    }

    @Test
    void testOutboxEventCreation() {
        OutboxEvent event = new OutboxEvent("hr.person", "person-003", "PersonTerminated",
                "{\"id\":\"person-003\",\"active\":false}");

        assertThat(event.published).isFalse();
        assertThat(event.publishedAt).isNull();
        assertThat(event.createdAt).isNotNull();
    }

    @Test
    @Transactional
    void testPersistOutboxEvent() {
        OutboxEvent event = new OutboxEvent("hr.person", "person-004", "PersonCreated",
                "{\"id\":\"person-004\",\"name\":\"New Person\"}");
        outboxRepository.persist(event);

        assertThat(event.id).isNotNull();

        List<OutboxEvent> unpublished = outboxRepository.findUnpublished();
        assertThat(unpublished).hasSize(3); // 2 original + 1 new
    }

    @Test
    void testOutboxEventMarkPublished() {
        OutboxEvent event = new OutboxEvent("hr.person", "test", "Test", "{}");

        assertThat(event.published).isFalse();
        assertThat(event.publishedAt).isNull();

        event.markPublished();

        assertThat(event.published).isTrue();
        assertThat(event.publishedAt).isNotNull();
    }

    @Test
    void testListAll() {
        List<OutboxEvent> all = outboxRepository.listAll();

        assertThat(all).hasSize(3); // 2 unpublished + 1 published
    }
}
