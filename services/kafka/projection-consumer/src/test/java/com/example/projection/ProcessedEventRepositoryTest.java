package com.example.projection;

import com.example.projection.model.ProcessedEvent;
import com.example.projection.repository.ProcessedEventRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ProcessedEventRepository using H2 in-memory database.
 * Tests verify idempotency tracking for CDC event processing.
 * These tests run without Docker.
 */
@QuarkusTest
class ProcessedEventRepositoryTest {

    @Inject
    ProcessedEventRepository processedEventRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        processedEventRepository.deleteAll();

        // Create test data
        ProcessedEvent event1 = new ProcessedEvent("person-001-2023-12-01T10:00:00Z", "cdc.hr.person", 100L, 0);
        ProcessedEvent event2 = new ProcessedEvent("person-002-2023-12-01T11:00:00Z", "cdc.hr.person", 101L, 0);
        ProcessedEvent event3 = new ProcessedEvent("employee-001-2023-12-01T12:00:00Z", "cdc.employment.employee", 50L, 1);

        processedEventRepository.persist(event1);
        processedEventRepository.persist(event2);
        processedEventRepository.persist(event3);
    }

    @Test
    void testHasProcessed_ReturnsTrueForExistingEvent() {
        boolean result = processedEventRepository.hasProcessed("person-001-2023-12-01T10:00:00Z");

        assertThat(result).isTrue();
    }

    @Test
    void testHasProcessed_ReturnsFalseForNewEvent() {
        boolean result = processedEventRepository.hasProcessed("person-999-2023-12-01T10:00:00Z");

        assertThat(result).isFalse();
    }

    @Test
    void testFindById_ReturnsExistingEvent() {
        ProcessedEvent event = processedEventRepository.findById("person-001-2023-12-01T10:00:00Z");

        assertThat(event).isNotNull();
        assertThat(event.topic).isEqualTo("cdc.hr.person");
        assertThat(event.offset).isEqualTo(100L);
        assertThat(event.partitionId).isZero();
        assertThat(event.processedAt).isNotNull();
    }

    @Test
    void testFindById_ReturnsNullForNonExisting() {
        ProcessedEvent event = processedEventRepository.findById("non-existent-event-id");

        assertThat(event).isNull();
    }

    @Test
    @Transactional
    void testPersistNewEvent() {
        String newEventId = "person-003-2023-12-01T13:00:00Z";
        ProcessedEvent newEvent = new ProcessedEvent(newEventId, "cdc.hr.person", 102L, 0);

        processedEventRepository.persist(newEvent);

        // Verify event was persisted
        assertThat(processedEventRepository.hasProcessed(newEventId)).isTrue();

        ProcessedEvent retrieved = processedEventRepository.findById(newEventId);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.topic).isEqualTo("cdc.hr.person");
        assertThat(retrieved.offset).isEqualTo(102L);
    }

    @Test
    void testListAll_ReturnsAllEvents() {
        List<ProcessedEvent> allEvents = processedEventRepository.listAll();

        assertThat(allEvents).hasSize(3);
        assertThat(allEvents).extracting(e -> e.eventId)
            .containsExactlyInAnyOrder(
                "person-001-2023-12-01T10:00:00Z",
                "person-002-2023-12-01T11:00:00Z",
                "employee-001-2023-12-01T12:00:00Z"
            );
    }

    @Test
    void testProcessedEventCreation() {
        ProcessedEvent event = new ProcessedEvent("test-event-id", "test.topic", 999L, 2);

        assertThat(event.eventId).isEqualTo("test-event-id");
        assertThat(event.topic).isEqualTo("test.topic");
        assertThat(event.offset).isEqualTo(999L);
        assertThat(event.partitionId).isEqualTo(2);
        assertThat(event.processedAt).isNotNull();
    }

    @Test
    @Transactional
    void testDeleteById() {
        String eventId = "person-001-2023-12-01T10:00:00Z";

        // Verify exists before delete
        assertThat(processedEventRepository.hasProcessed(eventId)).isTrue();

        // Delete
        processedEventRepository.deleteById(eventId);

        // Verify deleted
        assertThat(processedEventRepository.hasProcessed(eventId)).isFalse();
    }

    @Test
    @Transactional
    void testDeleteAll() {
        // Verify events exist
        assertThat(processedEventRepository.count()).isEqualTo(3);

        // Delete all
        processedEventRepository.deleteAll();

        // Verify all deleted
        assertThat(processedEventRepository.count()).isZero();
    }

    @Test
    void testCountByTopic() {
        long personEventCount = processedEventRepository.count("topic", "cdc.hr.person");
        long employeeEventCount = processedEventRepository.count("topic", "cdc.employment.employee");

        assertThat(personEventCount).isEqualTo(2);
        assertThat(employeeEventCount).isEqualTo(1);
    }

    @Test
    @Transactional
    void testMultipleEventsFromSameAggregate() {
        // Simulate multiple events from the same person (different timestamps)
        String personId = "person-100";
        ProcessedEvent created = new ProcessedEvent(personId + "-2023-12-01T10:00:00Z", "cdc.hr.person", 200L, 0);
        ProcessedEvent updated = new ProcessedEvent(personId + "-2023-12-01T11:00:00Z", "cdc.hr.person", 201L, 0);
        ProcessedEvent terminated = new ProcessedEvent(personId + "-2023-12-01T12:00:00Z", "cdc.hr.person", 202L, 0);

        processedEventRepository.persist(created);
        processedEventRepository.persist(updated);
        processedEventRepository.persist(terminated);

        // Verify all events are tracked independently
        assertThat(processedEventRepository.hasProcessed(personId + "-2023-12-01T10:00:00Z")).isTrue();
        assertThat(processedEventRepository.hasProcessed(personId + "-2023-12-01T11:00:00Z")).isTrue();
        assertThat(processedEventRepository.hasProcessed(personId + "-2023-12-01T12:00:00Z")).isTrue();

        // Total should be 6 (3 initial + 3 new)
        assertThat(processedEventRepository.count()).isEqualTo(6);
    }
}
