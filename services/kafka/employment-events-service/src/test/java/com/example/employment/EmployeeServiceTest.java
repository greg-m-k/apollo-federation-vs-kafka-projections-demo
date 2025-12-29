package com.example.employment;

import com.example.employment.dto.EmployeeInput;
import com.example.employment.model.Employee;
import com.example.employment.model.OutboxEvent;
import com.example.employment.repository.EmployeeRepository;
import com.example.employment.repository.OutboxRepository;
import com.example.employment.service.EmployeeService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EmployeeService using H2 in-memory database.
 * Tests verify the Outbox pattern - each change creates an outbox event.
 * These tests run without Docker.
 */
@QuarkusTest
class EmployeeServiceTest {

    @Inject
    EmployeeService employeeService;

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
        List<Employee> employees = employeeService.getAllEmployees();

        assertThat(employees).hasSize(2);
        assertThat(employees).extracting(e -> e.title)
                .containsExactlyInAnyOrder("Software Engineer", "Product Manager");
    }

    @Test
    void testGetEmployee() {
        Employee employee = employeeService.getEmployee("emp-001");

        assertThat(employee).isNotNull();
        assertThat(employee.title).isEqualTo("Software Engineer");
        assertThat(employee.department).isEqualTo("Engineering");
        assertThat(employee.salary).isEqualByComparingTo(new BigDecimal("75000.00"));
    }

    @Test
    void testGetEmployee_NotFound() {
        Employee employee = employeeService.getEmployee("non-existent");

        assertThat(employee).isNull();
    }

    @Test
    void testGetEmployeeByPersonId() {
        Employee employee = employeeService.getEmployeeByPersonId("person-001");

        assertThat(employee).isNotNull();
        assertThat(employee.id).isEqualTo("emp-001");
        assertThat(employee.title).isEqualTo("Software Engineer");
    }

    @Test
    void testGetEmployeeByPersonId_NotFound() {
        Employee employee = employeeService.getEmployeeByPersonId("non-existent");

        assertThat(employee).isNull();
    }

    @Test
    void testAssignEmployee_CreatesEmployeeAndOutboxEvent() {
        EmployeeInput input = new EmployeeInput("person-003", "Data Analyst",
                "Analytics", new BigDecimal("65000.00"));

        Employee employee = employeeService.assignEmployee(input);

        // Verify employee was created
        assertThat(employee).isNotNull();
        assertThat(employee.id).startsWith("emp-");
        assertThat(employee.personId).isEqualTo("person-003");
        assertThat(employee.title).isEqualTo("Data Analyst");
        assertThat(employee.department).isEqualTo("Analytics");
        assertThat(employee.salary).isEqualByComparingTo(new BigDecimal("65000.00"));
        assertThat(employee.active).isTrue();

        // Verify outbox event was created
        List<OutboxEvent> events = outboxRepository.findUnpublished();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType).isEqualTo("EmployeeAssigned");
        assertThat(events.get(0).aggregateId).isEqualTo(employee.id);
        assertThat(events.get(0).aggregateType).isEqualTo("employment.employee");
        assertThat(events.get(0).payload).contains("Data Analyst");
    }

    @Test
    void testAssignEmployee_DuplicatePersonId_ReturnsNull() {
        EmployeeInput input = new EmployeeInput("person-001", "New Title",
                "New Dept", new BigDecimal("50000.00"));

        Employee employee = employeeService.assignEmployee(input);

        assertThat(employee).isNull();
        assertThat(outboxRepository.findUnpublished()).isEmpty();
    }

    @Test
    void testPromoteEmployee_UpdatesTitleSalaryAndCreatesOutboxEvent() {
        Employee employee = employeeService.promoteEmployee("emp-001",
                "Senior Software Engineer", new BigDecimal("95000.00"));

        // Verify employee was updated
        assertThat(employee).isNotNull();
        assertThat(employee.title).isEqualTo("Senior Software Engineer");
        assertThat(employee.salary).isEqualByComparingTo(new BigDecimal("95000.00"));

        // Verify outbox event was created
        List<OutboxEvent> events = outboxRepository.findUnpublished();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType).isEqualTo("EmployeePromoted");
        assertThat(events.get(0).aggregateId).isEqualTo("emp-001");
    }

    @Test
    void testPromoteEmployee_PartialUpdate_TitleOnly() {
        Employee employee = employeeService.promoteEmployee("emp-001",
                "Lead Engineer", null);

        assertThat(employee).isNotNull();
        assertThat(employee.title).isEqualTo("Lead Engineer");
        assertThat(employee.salary).isEqualByComparingTo(new BigDecimal("75000.00")); // unchanged
    }

    @Test
    void testPromoteEmployee_PartialUpdate_SalaryOnly() {
        Employee employee = employeeService.promoteEmployee("emp-001",
                null, new BigDecimal("85000.00"));

        assertThat(employee).isNotNull();
        assertThat(employee.title).isEqualTo("Software Engineer"); // unchanged
        assertThat(employee.salary).isEqualByComparingTo(new BigDecimal("85000.00"));
    }

    @Test
    void testPromoteEmployee_NotFound() {
        Employee employee = employeeService.promoteEmployee("non-existent",
                "Title", new BigDecimal("100000.00"));

        assertThat(employee).isNull();
        assertThat(outboxRepository.findUnpublished()).isEmpty();
    }

    @Test
    void testTransferEmployee_UpdatesDepartmentAndCreatesOutboxEvent() {
        Employee employee = employeeService.transferEmployee("emp-001", "Data Science");

        // Verify employee was updated
        assertThat(employee).isNotNull();
        assertThat(employee.department).isEqualTo("Data Science");
        assertThat(employee.title).isEqualTo("Software Engineer"); // unchanged

        // Verify outbox event was created
        List<OutboxEvent> events = outboxRepository.findUnpublished();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType).isEqualTo("EmployeeTransferred");
        assertThat(events.get(0).aggregateId).isEqualTo("emp-001");
    }

    @Test
    void testTransferEmployee_NotFound() {
        Employee employee = employeeService.transferEmployee("non-existent", "New Dept");

        assertThat(employee).isNull();
        assertThat(outboxRepository.findUnpublished()).isEmpty();
    }

    @Test
    void testTerminateEmployee_TerminatesAndCreatesOutboxEvent() {
        Employee employee = employeeService.terminateEmployee("emp-001");

        // Verify employee was terminated
        assertThat(employee).isNotNull();
        assertThat(employee.active).isFalse();

        // Verify outbox event was created
        List<OutboxEvent> events = outboxRepository.findUnpublished();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType).isEqualTo("EmployeeTerminated");
        assertThat(events.get(0).aggregateId).isEqualTo("emp-001");
    }

    @Test
    void testTerminateEmployee_NotFound() {
        Employee employee = employeeService.terminateEmployee("non-existent");

        assertThat(employee).isNull();
        assertThat(outboxRepository.findUnpublished()).isEmpty();
    }
}
