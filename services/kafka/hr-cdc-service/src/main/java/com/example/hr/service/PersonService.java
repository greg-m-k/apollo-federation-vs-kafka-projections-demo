package com.example.hr.service;

import com.example.hr.dto.PersonEvent;
import com.example.hr.dto.PersonInput;
import com.example.hr.model.OutboxEvent;
import com.example.hr.model.Person;
import com.example.hr.repository.OutboxRepository;
import com.example.hr.repository.PersonRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for Person operations with Outbox pattern.
 * All changes are persisted atomically with an outbox event.
 */
@ApplicationScoped
public class PersonService {

    @Inject
    PersonRepository personRepository;

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    ObjectMapper objectMapper;

    public List<Person> getAllPersons() {
        return personRepository.listAll();
    }

    public Person getPerson(String id) {
        return personRepository.findById(id);
    }

    @Transactional
    public Person createPerson(PersonInput input) {
        String id = "person-" + UUID.randomUUID().toString().substring(0, 8);
        Person person = new Person(id, input.name(), input.email(), input.hireDate());
        personRepository.persist(person);

        // Create outbox event in same transaction
        createOutboxEvent("PersonCreated", person);

        return person;
    }

    @Transactional
    public Person updatePerson(String id, String name, String email) {
        Person person = personRepository.findById(id);
        if (person == null) {
            return null;
        }

        if (name != null) {
            person.name = name;
        }
        if (email != null) {
            person.email = email;
        }

        // Create outbox event in same transaction
        createOutboxEvent("PersonUpdated", person);

        return person;
    }

    @Transactional
    public Person terminatePerson(String id) {
        Person person = personRepository.findById(id);
        if (person == null) {
            return null;
        }

        person.active = false;

        // Create outbox event in same transaction
        createOutboxEvent("PersonTerminated", person);

        return person;
    }

    private void createOutboxEvent(String eventType, Person person) {
        try {
            PersonEvent.PersonData data = new PersonEvent.PersonData(
                person.id, person.name, person.email, person.hireDate, person.active
            );
            PersonEvent event = new PersonEvent(eventType, person.id, data, Instant.now());
            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = new OutboxEvent("hr.person", person.id, eventType, payload);
            outboxRepository.persist(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize person event", e);
        }
    }
}
