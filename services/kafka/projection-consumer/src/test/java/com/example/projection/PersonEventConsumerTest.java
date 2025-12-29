package com.example.projection;

import com.example.projection.model.PersonProjection;
import com.example.projection.model.ProcessedEvent;
import com.example.projection.repository.PersonProjectionRepository;
import com.example.projection.repository.ProcessedEventRepository;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.kafka.Record;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for PersonEventConsumer.
 * Tests verify that Kafka events are consumed and projections are created correctly.
 * Requires Docker for Kafka DevServices.
 */
@QuarkusTest
@ExtendWith(DockerAvailableCondition.class)
class PersonEventConsumerTest {

    @Inject
    PersonProjectionRepository personProjectionRepository;

    @Inject
    ProcessedEventRepository processedEventRepository;

    @Inject
    @Any
    InMemoryConnector connector;

    @BeforeEach
    @Transactional
    void setUp() {
        processedEventRepository.deleteAll();
        personProjectionRepository.deleteAll();
    }

    @Test
    void testPersonCreatedEvent_CreatesProjection() {
        // Arrange
        String personId = "person-001";
        String timestamp = Instant.now().toString();
        String eventPayload = createPersonEventPayload(
            "PersonCreated",
            personId,
            "John Doe",
            "john.doe@example.com",
            "2023-06-15",
            true,
            timestamp
        );

        InMemorySource<Record<String, String>> source = connector.source("person-events");

        // Act
        source.send(Record.of(personId, eventPayload));

        // Assert - wait for async processing
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> withRequestContext(() -> {
            PersonProjection projection = personProjectionRepository.findById(personId);
            assertThat(projection).isNotNull();
            assertThat(projection.name).isEqualTo("John Doe");
            assertThat(projection.email).isEqualTo("john.doe@example.com");
            assertThat(projection.hireDate).isNotNull();
            assertThat(projection.active).isTrue();
            return null;
        }));

        // Verify idempotency tracking
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> withRequestContext(() -> {
            String eventId = "person-" + personId + "-" + timestamp;
            assertThat(processedEventRepository.hasProcessed(eventId)).isTrue();
            return null;
        }));
    }

    @Test
    void testPersonUpdatedEvent_UpdatesExistingProjection() {
        // Arrange - create initial projection
        createInitialProjection("person-002", "Jane Smith", "jane.smith@example.com");

        String personId = "person-002";
        String timestamp = Instant.now().toString();
        String eventPayload = createPersonEventPayload(
            "PersonUpdated",
            personId,
            "Jane Smith-Updated",
            "jane.updated@example.com",
            "2023-01-10",
            true,
            timestamp
        );

        InMemorySource<Record<String, String>> source = connector.source("person-events");

        // Act
        source.send(Record.of(personId, eventPayload));

        // Assert
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> withRequestContext(() -> {
            PersonProjection projection = personProjectionRepository.findById(personId);
            assertThat(projection).isNotNull();
            assertThat(projection.name).isEqualTo("Jane Smith-Updated");
            assertThat(projection.email).isEqualTo("jane.updated@example.com");
            assertThat(projection.eventVersion).isGreaterThan(1L);
            return null;
        }));
    }

    @Test
    void testPersonTerminatedEvent_SetsActiveToFalse() {
        // Arrange - create initial active projection
        createInitialProjection("person-003", "Bob Wilson", "bob.wilson@example.com");

        String personId = "person-003";
        String timestamp = Instant.now().toString();
        String eventPayload = createPersonEventPayload(
            "PersonTerminated",
            personId,
            "Bob Wilson",
            "bob.wilson@example.com",
            "2023-01-01",
            false,
            timestamp
        );

        InMemorySource<Record<String, String>> source = connector.source("person-events");

        // Act
        source.send(Record.of(personId, eventPayload));

        // Assert
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> withRequestContext(() -> {
            PersonProjection projection = personProjectionRepository.findById(personId);
            assertThat(projection).isNotNull();
            assertThat(projection.active).isFalse();
            return null;
        }));
    }

    @Test
    void testIdempotency_DuplicateEventIsIgnored() {
        // Arrange
        String personId = "person-004";
        String timestamp = "2023-12-01T10:00:00Z"; // Fixed timestamp for deterministic event ID
        String eventPayload = createPersonEventPayload(
            "PersonCreated",
            personId,
            "Alice Brown",
            "alice.brown@example.com",
            "2023-03-20",
            true,
            timestamp
        );

        InMemorySource<Record<String, String>> source = connector.source("person-events");

        // Act - send the same event twice
        source.send(Record.of(personId, eventPayload));

        // Wait for first event processing
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> withRequestContext(() -> {
            PersonProjection projection = personProjectionRepository.findById(personId);
            assertThat(projection).isNotNull();
            return null;
        }));

        // Get initial version
        long initialVersion = withRequestContext(() -> {
            PersonProjection initialProjection = personProjectionRepository.findById(personId);
            return initialProjection.eventVersion;
        });

        // Send duplicate event
        source.send(Record.of(personId, eventPayload));

        // Wait a bit and verify version didn't change (event was ignored)
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long afterVersion = withRequestContext(() -> {
            PersonProjection afterDuplicate = personProjectionRepository.findById(personId);
            return afterDuplicate.eventVersion;
        });
        assertThat(afterVersion).isEqualTo(initialVersion);
    }

    @Test
    void testMultiplePersonEvents_CreatesMultipleProjections() {
        // Arrange
        InMemorySource<Record<String, String>> source = connector.source("person-events");
        String timestamp1 = Instant.now().toString();
        String timestamp2 = Instant.now().plusMillis(100).toString();
        String timestamp3 = Instant.now().plusMillis(200).toString();

        String event1 = createPersonEventPayload("PersonCreated", "person-005", "Person One", "one@example.com", "2023-01-01", true, timestamp1);
        String event2 = createPersonEventPayload("PersonCreated", "person-006", "Person Two", "two@example.com", "2023-02-01", true, timestamp2);
        String event3 = createPersonEventPayload("PersonCreated", "person-007", "Person Three", "three@example.com", "2023-03-01", true, timestamp3);

        // Act
        source.send(Record.of("person-005", event1));
        source.send(Record.of("person-006", event2));
        source.send(Record.of("person-007", event3));

        // Assert
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> withRequestContext(() -> {
            List<PersonProjection> allProjections = personProjectionRepository.listAll();
            assertThat(allProjections).hasSize(3);
            assertThat(allProjections).extracting(p -> p.id)
                .containsExactlyInAnyOrder("person-005", "person-006", "person-007");
            return null;
        }));
    }

    @Test
    void testPersonEventWithNullHireDate_HandlesGracefully() {
        // Arrange
        String personId = "person-008";
        String timestamp = Instant.now().toString();
        String eventPayload = """
            {
                "eventType": "PersonCreated",
                "personId": "%s",
                "timestamp": "%s",
                "data": {
                    "name": "No HireDate Person",
                    "email": "nohiredate@example.com",
                    "hireDate": null,
                    "active": true
                }
            }
            """.formatted(personId, timestamp);

        InMemorySource<Record<String, String>> source = connector.source("person-events");

        // Act
        source.send(Record.of(personId, eventPayload));

        // Assert
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> withRequestContext(() -> {
            PersonProjection projection = personProjectionRepository.findById(personId);
            assertThat(projection).isNotNull();
            assertThat(projection.name).isEqualTo("No HireDate Person");
            assertThat(projection.hireDate).isNull();
            return null;
        }));
    }

    @Transactional
    void createInitialProjection(String id, String name, String email) {
        PersonProjection projection = new PersonProjection();
        projection.id = id;
        projection.name = name;
        projection.email = email;
        projection.active = true;
        projection.lastUpdated = Instant.now();
        projection.eventVersion = 1L;
        personProjectionRepository.persist(projection);
    }

    private String createPersonEventPayload(
            String eventType,
            String personId,
            String name,
            String email,
            String hireDate,
            boolean active,
            String timestamp) {
        return """
            {
                "eventType": "%s",
                "personId": "%s",
                "timestamp": "%s",
                "data": {
                    "name": "%s",
                    "email": "%s",
                    "hireDate": "%s",
                    "active": %s
                }
            }
            """.formatted(eventType, personId, timestamp, name, email, hireDate, active);
    }

    /**
     * Helper to run assertions within a request context.
     * Awaitility runs in a separate thread that doesn't have CDI context active.
     */
    private <T> T withRequestContext(Callable<T> callable) {
        var requestContext = Arc.container().requestContext();
        requestContext.activate();
        try {
            return callable.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            requestContext.terminate();
        }
    }
}
