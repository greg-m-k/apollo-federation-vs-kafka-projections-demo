package com.example.employment;

import com.example.employment.model.Employee;
import com.example.employment.repository.EmployeeRepository;
import com.example.employment.repository.OutboxRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for EmployeeResource REST API using H2 in-memory database.
 * These tests run without Docker.
 */
@QuarkusTest
class EmployeeResourceTest {

    @Inject
    EmployeeRepository employeeRepository;

    @Inject
    OutboxRepository outboxRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        outboxRepository.deleteAll();
        employeeRepository.deleteAll();

        Employee employee1 = new Employee("emp-001", "person-001", "Software Engineer",
                "Engineering", new BigDecimal("75000.00"));
        Employee employee2 = new Employee("emp-002", "person-002", "Product Manager",
                "Product", new BigDecimal("95000.00"));

        employeeRepository.persist(employee1);
        employeeRepository.persist(employee2);
    }

    @Test
    void testGetAllEmployees() {
        given()
        .when()
            .get("/api/employees")
        .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("title", hasItems("Software Engineer", "Product Manager"));
    }

    @Test
    void testGetEmployeeById() {
        given()
        .when()
            .get("/api/employees/emp-001")
        .then()
            .statusCode(200)
            .body("id", is("emp-001"))
            .body("personId", is("person-001"))
            .body("title", is("Software Engineer"))
            .body("department", is("Engineering"))
            .body("active", is(true));
    }

    @Test
    void testGetEmployeeById_NotFound() {
        given()
        .when()
            .get("/api/employees/non-existent")
        .then()
            .statusCode(404);
    }

    @Test
    void testGetEmployeeByPersonId() {
        given()
        .when()
            .get("/api/employees/by-person/person-001")
        .then()
            .statusCode(200)
            .body("id", is("emp-001"))
            .body("personId", is("person-001"))
            .body("title", is("Software Engineer"));
    }

    @Test
    void testGetEmployeeByPersonId_NotFound() {
        given()
        .when()
            .get("/api/employees/by-person/non-existent")
        .then()
            .statusCode(404);
    }

    @Test
    void testAssignEmployee() {
        String employeeJson = """
            {
                "personId": "person-003",
                "title": "Data Analyst",
                "department": "Analytics",
                "salary": 65000.00
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(employeeJson)
        .when()
            .post("/api/employees")
        .then()
            .statusCode(201)
            .body("personId", is("person-003"))
            .body("title", is("Data Analyst"))
            .body("department", is("Analytics"))
            .body("active", is(true))
            .body("id", startsWith("emp-"));
    }

    @Test
    void testAssignEmployee_DuplicatePersonId() {
        String employeeJson = """
            {
                "personId": "person-001",
                "title": "New Title",
                "department": "New Dept",
                "salary": 50000.00
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(employeeJson)
        .when()
            .post("/api/employees")
        .then()
            .statusCode(409)
            .body(containsString("already has an employee record"));
    }

    @Test
    void testPromoteEmployee() {
        given()
            .queryParam("title", "Senior Software Engineer")
            .queryParam("salary", "95000.00")
        .when()
            .put("/api/employees/emp-001/promote")
        .then()
            .statusCode(200)
            .body("id", is("emp-001"))
            .body("title", is("Senior Software Engineer"))
            .body("salary", is(95000.00f));
    }

    @Test
    void testPromoteEmployee_PartialUpdate_TitleOnly() {
        given()
            .queryParam("title", "Lead Engineer")
        .when()
            .put("/api/employees/emp-001/promote")
        .then()
            .statusCode(200)
            .body("title", is("Lead Engineer"))
            .body("salary", is(75000.00f)); // unchanged
    }

    @Test
    void testPromoteEmployee_NotFound() {
        given()
            .queryParam("title", "New Title")
        .when()
            .put("/api/employees/non-existent/promote")
        .then()
            .statusCode(404);
    }

    @Test
    void testTransferEmployee() {
        given()
            .queryParam("department", "Data Science")
        .when()
            .put("/api/employees/emp-001/transfer")
        .then()
            .statusCode(200)
            .body("id", is("emp-001"))
            .body("department", is("Data Science"))
            .body("title", is("Software Engineer")); // unchanged
    }

    @Test
    void testTransferEmployee_NotFound() {
        given()
            .queryParam("department", "New Dept")
        .when()
            .put("/api/employees/non-existent/transfer")
        .then()
            .statusCode(404);
    }

    @Test
    void testTerminateEmployee() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/employees/emp-001/terminate")
        .then()
            .statusCode(200)
            .body("id", is("emp-001"))
            .body("active", is(false));
    }

    @Test
    void testTerminateEmployee_NotFound() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/employees/non-existent/terminate")
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
