package com.example.hr;

import com.example.hr.dto.PersonInput;
import com.example.hr.model.OutboxEvent;
import com.example.hr.model.Person;
import com.example.hr.repository.OutboxRepository;
import com.example.hr.repository.PersonRepository;
import com.example.hr.service.PersonService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PersonService using H2 in-memory database.
 * Tests verify the Outbox pattern - each change creates an outbox event.
 * These tests run without Docker.
 */
@QuarkusTest
class PersonServiceTest {

    @Inject
    PersonService personService;

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
        List<Person> persons = personService.getAllPersons();

        assertThat(persons).hasSize(2);
        assertThat(persons).extracting(p -> p.name)
                .containsExactlyInAnyOrder("John Doe", "Jane Smith");
    }

    @Test
    void testGetPerson() {
        Person person = personService.getPerson("person-001");

        assertThat(person).isNotNull();
        assertThat(person.name).isEqualTo("John Doe");
        assertThat(person.email).isEqualTo("john.doe@example.com");
    }

    @Test
    void testGetPerson_NotFound() {
        Person person = personService.getPerson("non-existent");

        assertThat(person).isNull();
    }

    @Test
    void testCreatePerson_CreatesPersonAndOutboxEvent() {
        PersonInput input = new PersonInput("Alice Brown", "alice@example.com", LocalDate.now());

        Person person = personService.createPerson(input);

        // Verify person was created
        assertThat(person).isNotNull();
        assertThat(person.id).startsWith("person-");
        assertThat(person.name).isEqualTo("Alice Brown");
        assertThat(person.email).isEqualTo("alice@example.com");
        assertThat(person.active).isTrue();

        // Verify outbox event was created
        List<OutboxEvent> events = outboxRepository.findUnpublished();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType).isEqualTo("PersonCreated");
        assertThat(events.get(0).aggregateId).isEqualTo(person.id);
        assertThat(events.get(0).aggregateType).isEqualTo("hr.person");
        assertThat(events.get(0).payload).contains("Alice Brown");
    }

    @Test
    void testUpdatePerson_UpdatesPersonAndCreatesOutboxEvent() {
        Person person = personService.updatePerson("person-001", "John Updated", "john.updated@example.com");

        // Verify person was updated
        assertThat(person).isNotNull();
        assertThat(person.name).isEqualTo("John Updated");
        assertThat(person.email).isEqualTo("john.updated@example.com");

        // Verify outbox event was created
        List<OutboxEvent> events = outboxRepository.findUnpublished();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType).isEqualTo("PersonUpdated");
        assertThat(events.get(0).aggregateId).isEqualTo("person-001");
    }

    @Test
    void testUpdatePerson_PartialUpdate() {
        Person person = personService.updatePerson("person-001", "John Renamed", null);

        assertThat(person).isNotNull();
        assertThat(person.name).isEqualTo("John Renamed");
        assertThat(person.email).isEqualTo("john.doe@example.com"); // unchanged
    }

    @Test
    void testUpdatePerson_NotFound() {
        Person person = personService.updatePerson("non-existent", "Name", "email@example.com");

        assertThat(person).isNull();
        assertThat(outboxRepository.findUnpublished()).isEmpty();
    }

    @Test
    void testTerminatePerson_TerminatesAndCreatesOutboxEvent() {
        Person person = personService.terminatePerson("person-001");

        // Verify person was terminated
        assertThat(person).isNotNull();
        assertThat(person.active).isFalse();

        // Verify outbox event was created
        List<OutboxEvent> events = outboxRepository.findUnpublished();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType).isEqualTo("PersonTerminated");
        assertThat(events.get(0).aggregateId).isEqualTo("person-001");
    }

    @Test
    void testTerminatePerson_NotFound() {
        Person person = personService.terminatePerson("non-existent");

        assertThat(person).isNull();
        assertThat(outboxRepository.findUnpublished()).isEmpty();
    }
}
