package com.example.hr;

import com.example.hr.model.OutboxEvent;
import com.example.hr.model.Person;
import com.example.hr.repository.OutboxRepository;
import com.example.hr.repository.PersonRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for the complete CDC flow.
 * Tests verify that REST operations create proper outbox events for downstream processing.
 * These tests run without Docker - Kafka is mocked via in-memory connector.
 */
@QuarkusTest
class CdcFlowIntegrationTest {

    @Inject
    PersonRepository personRepository;

    @Inject
    OutboxRepository outboxRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        outboxRepository.deleteAll();
        personRepository.deleteAll();
    }

    @Test
    void testCreatePersonFlow_GeneratesOutboxEvent() {
        String personJson = """
            {
                "name": "CDC Test Person",
                "email": "cdc.test@example.com",
                "hireDate": "2023-06-15"
            }
            """;

        // Create person via REST API
        Response response = given()
            .contentType(ContentType.JSON)
            .body(personJson)
        .when()
            .post("/api/persons")
        .then()
            .statusCode(201)
            .body("name", is("CDC Test Person"))
            .extract().response();

        String personId = response.path("id");

        // Verify outbox event was created
        List<OutboxEvent> events = outboxRepository.findUnpublished();
        assertThat(events).hasSize(1);

        OutboxEvent event = events.get(0);
        assertThat(event.aggregateType).isEqualTo("hr.person");
        assertThat(event.aggregateId).isEqualTo(personId);
        assertThat(event.eventType).isEqualTo("PersonCreated");
        assertThat(event.published).isFalse();
        assertThat(event.payload).contains("CDC Test Person");
        assertThat(event.payload).contains("cdc.test@example.com");
    }

    @Test
    void testUpdatePersonFlow_GeneratesOutboxEvent() {
        // Create initial person via API
        String personJson = """
            {
                "name": "Original Name",
                "email": "original@example.com",
                "hireDate": "2023-01-01"
            }
            """;

        Response createResponse = given()
            .contentType(ContentType.JSON)
            .body(personJson)
        .when()
            .post("/api/persons")
        .then()
            .statusCode(201)
            .extract().response();

        String personId = createResponse.path("id");

        // Clear the creation event
        clearOutboxEvents();

        // Update via REST API
        given()
            .queryParam("name", "Updated Name")
            .queryParam("email", "updated@example.com")
        .when()
            .put("/api/persons/" + personId)
        .then()
            .statusCode(200)
            .body("name", is("Updated Name"));

        // Verify outbox event was created
        List<OutboxEvent> events = outboxRepository.findUnpublished();
        assertThat(events).hasSize(1);

        OutboxEvent event = events.get(0);
        assertThat(event.eventType).isEqualTo("PersonUpdated");
        assertThat(event.aggregateId).isEqualTo(personId);
        assertThat(event.payload).contains("Updated Name");
    }

    @Transactional
    void clearOutboxEvents() {
        outboxRepository.deleteAll();
    }

    @Test
    void testTerminatePersonFlow_GeneratesOutboxEvent() {
        // Create initial person via API
        String personJson = """
            {
                "name": "Active Person",
                "email": "active@example.com",
                "hireDate": "2023-01-01"
            }
            """;

        Response createResponse = given()
            .contentType(ContentType.JSON)
            .body(personJson)
        .when()
            .post("/api/persons")
        .then()
            .statusCode(201)
            .extract().response();

        String personId = createResponse.path("id");

        // Clear the creation event
        clearOutboxEvents();

        // Terminate via REST API
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/persons/" + personId + "/terminate")
        .then()
            .statusCode(200)
            .body("active", is(false));

        // Verify outbox event was created
        List<OutboxEvent> events = outboxRepository.findUnpublished();
        assertThat(events).hasSize(1);

        OutboxEvent event = events.get(0);
        assertThat(event.eventType).isEqualTo("PersonTerminated");
        assertThat(event.aggregateId).isEqualTo(personId);
        assertThat(event.payload).contains("\"active\":false");
    }

    @Test
    void testMultipleOperations_GeneratesMultipleOutboxEvents() {
        // Create first person
        String person1Json = """
            {
                "name": "Person One",
                "email": "one@example.com",
                "hireDate": "2023-01-01"
            }
            """;

        Response response1 = given()
            .contentType(ContentType.JSON)
            .body(person1Json)
        .when()
            .post("/api/persons")
        .then()
            .statusCode(201)
            .extract().response();

        String person1Id = response1.path("id");

        // Create second person
        String person2Json = """
            {
                "name": "Person Two",
                "email": "two@example.com",
                "hireDate": "2023-02-01"
            }
            """;

        Response response2 = given()
            .contentType(ContentType.JSON)
            .body(person2Json)
        .when()
            .post("/api/persons")
        .then()
            .statusCode(201)
            .extract().response();

        String person2Id = response2.path("id");

        // Update first person
        given()
            .queryParam("name", "Person One Updated")
        .when()
            .put("/api/persons/" + person1Id)
        .then()
            .statusCode(200);

        // Terminate second person
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/persons/" + person2Id + "/terminate")
        .then()
            .statusCode(200);

        // Verify all outbox events were created
        List<OutboxEvent> events = outboxRepository.findUnpublished();
        assertThat(events).hasSize(4);

        // Verify event types
        assertThat(events).extracting(e -> e.eventType)
                .containsExactly("PersonCreated", "PersonCreated", "PersonUpdated", "PersonTerminated");
    }

    @Test
    void testOutboxEventPayloadContainsAllRequiredFields() {
        String personJson = """
            {
                "name": "Full Payload Test",
                "email": "payload@example.com",
                "hireDate": "2023-07-20"
            }
            """;

        Response response = given()
            .contentType(ContentType.JSON)
            .body(personJson)
        .when()
            .post("/api/persons")
        .then()
            .statusCode(201)
            .extract().response();

        String personId = response.path("id");

        List<OutboxEvent> events = outboxRepository.findUnpublished();
        assertThat(events).hasSize(1);

        String payload = events.get(0).payload;

        // Verify payload contains all required fields for CDC consumers
        assertThat(payload).contains("eventType");
        assertThat(payload).contains("personId");
        assertThat(payload).contains("data");
        assertThat(payload).contains("timestamp");
        assertThat(payload).contains("Full Payload Test");
        assertThat(payload).contains("payload@example.com");
        assertThat(payload).contains(personId);
    }

    @Test
    void testOutboxEventOrdering() {
        // Create multiple persons in sequence
        for (int i = 1; i <= 3; i++) {
            String personJson = String.format("""
                {
                    "name": "Person %d",
                    "email": "person%d@example.com",
                    "hireDate": "2023-0%d-01"
                }
                """, i, i, i);

            given()
                .contentType(ContentType.JSON)
                .body(personJson)
            .when()
                .post("/api/persons")
            .then()
                .statusCode(201);
        }

        // Verify events are ordered by creation time
        List<OutboxEvent> events = outboxRepository.findUnpublished();
        assertThat(events).hasSize(3);

        for (int i = 0; i < events.size() - 1; i++) {
            assertThat(events.get(i).createdAt)
                    .isBeforeOrEqualTo(events.get(i + 1).createdAt);
        }
    }
}
