package com.example.employment.service;

import com.example.employment.dto.EmployeeEvent;
import com.example.employment.dto.EmployeeInput;
import com.example.employment.model.Employee;
import com.example.employment.model.OutboxEvent;
import com.example.employment.repository.EmployeeRepository;
import com.example.employment.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class EmployeeService {

    @Inject
    EmployeeRepository employeeRepository;

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    ObjectMapper objectMapper;

    public List<Employee> getAllEmployees() {
        return employeeRepository.listAll();
    }

    public Employee getEmployee(String id) {
        return employeeRepository.findById(id);
    }

    public Employee getEmployeeByPersonId(String personId) {
        return employeeRepository.findByPersonId(personId).orElse(null);
    }

    @Transactional
    public Employee assignEmployee(EmployeeInput input) {
        // Check if already assigned
        if (employeeRepository.findByPersonId(input.personId()).isPresent()) {
            return null;
        }

        String id = "emp-" + UUID.randomUUID().toString().substring(0, 8);
        Employee employee = new Employee(id, input.personId(), input.title(), input.department(), input.salary());
        employeeRepository.persist(employee);

        createOutboxEvent("EmployeeAssigned", employee);
        return employee;
    }

    @Transactional
    public Employee promoteEmployee(String id, String newTitle, BigDecimal newSalary) {
        Employee employee = employeeRepository.findById(id);
        if (employee == null) {
            return null;
        }

        if (newTitle != null) {
            employee.title = newTitle;
        }
        if (newSalary != null) {
            employee.salary = newSalary;
        }

        createOutboxEvent("EmployeePromoted", employee);
        return employee;
    }

    @Transactional
    public Employee transferEmployee(String id, String newDepartment) {
        Employee employee = employeeRepository.findById(id);
        if (employee == null) {
            return null;
        }

        employee.department = newDepartment;
        createOutboxEvent("EmployeeTransferred", employee);
        return employee;
    }

    @Transactional
    public Employee terminateEmployee(String id) {
        Employee employee = employeeRepository.findById(id);
        if (employee == null) {
            return null;
        }

        employee.active = false;
        createOutboxEvent("EmployeeTerminated", employee);
        return employee;
    }

    private void createOutboxEvent(String eventType, Employee employee) {
        try {
            EmployeeEvent.EmployeeData data = new EmployeeEvent.EmployeeData(
                employee.id, employee.personId, employee.title,
                employee.department, employee.salary, employee.active
            );
            EmployeeEvent event = new EmployeeEvent(eventType, employee.id, data, Instant.now());
            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = new OutboxEvent("employment.employee", employee.id, eventType, payload);
            outboxRepository.persist(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize employee event", e);
        }
    }
}
