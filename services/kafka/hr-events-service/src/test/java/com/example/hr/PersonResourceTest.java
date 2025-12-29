package com.example.hr;

import com.example.hr.model.Person;
import com.example.hr.repository.OutboxRepository;
import com.example.hr.repository.PersonRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for PersonResource REST API using H2 in-memory database.
 * These tests run without Docker.
 */
@QuarkusTest
class PersonResourceTest {

    @Inject
    PersonRepository personRepository;

    @Inject
    OutboxRepository outboxRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        outboxRepository.deleteAll();
        personRepository.deleteAll();

        Person person1 = new Person("person-001", "John Doe", "john.doe@example.com",
                LocalDate.of(2020, 1, 15));
        Person person2 = new Person("person-002", "Jane Smith", "jane.smith@example.com",
                LocalDate.of(2021, 3, 20));

        personRepository.persist(person1);
        personRepository.persist(person2);
    }

    @Test
    void testGetAllPersons() {
        given()
        .when()
            .get("/api/persons")
        .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("name", hasItems("John Doe", "Jane Smith"));
    }

    @Test
    void testGetPersonById() {
        given()
        .when()
            .get("/api/persons/person-001")
        .then()
            .statusCode(200)
            .body("id", is("person-001"))
            .body("name", is("John Doe"))
            .body("email", is("john.doe@example.com"))
            .body("active", is(true));
    }

    @Test
    void testGetPersonById_NotFound() {
        given()
        .when()
            .get("/api/persons/non-existent")
        .then()
            .statusCode(404);
    }

    @Test
    void testCreatePerson() {
        String personJson = """
            {
                "name": "Alice Brown",
                "email": "alice@example.com",
                "hireDate": "2023-06-15"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(personJson)
        .when()
            .post("/api/persons")
        .then()
            .statusCode(201)
            .body("name", is("Alice Brown"))
            .body("email", is("alice@example.com"))
            .body("active", is(true))
            .body("id", startsWith("person-"));
    }

    @Test
    void testUpdatePerson() {
        given()
            .queryParam("name", "John Updated")
            .queryParam("email", "john.updated@example.com")
        .when()
            .put("/api/persons/person-001")
        .then()
            .statusCode(200)
            .body("id", is("person-001"))
            .body("name", is("John Updated"))
            .body("email", is("john.updated@example.com"));
    }

    @Test
    void testUpdatePerson_PartialUpdate() {
        given()
            .queryParam("name", "John Renamed")
        .when()
            .put("/api/persons/person-001")
        .then()
            .statusCode(200)
            .body("name", is("John Renamed"))
            .body("email", is("john.doe@example.com")); // unchanged
    }

    @Test
    void testUpdatePerson_NotFound() {
        given()
            .queryParam("name", "New Name")
        .when()
            .put("/api/persons/non-existent")
        .then()
            .statusCode(404);
    }

    @Test
    void testTerminatePerson() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/persons/person-001/terminate")
        .then()
            .statusCode(200)
            .body("id", is("person-001"))
            .body("active", is(false));
    }

    @Test
    void testTerminatePerson_NotFound() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/persons/non-existent/terminate")
        .then()
            .statusCode(404);
    }

    @Test
    void testHealthEndpoint() {
        given()
        .when()
            .get("/q/health/ready")
        .then()
            .statusCode(200)
            .body("status", is("UP"));
    }
}
