package com.example.hr.graphql;

import com.example.hr.model.Person;
import com.example.hr.repository.PersonRepository;
import com.example.hr.timing.TimingContext;
import io.micrometer.core.annotation.Timed;
import io.smallrye.graphql.api.federation.Resolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * GraphQL API for HR/Person subgraph.
 * This subgraph owns Person - the canonical source of identity data.
 * All DB operations are timed and recorded in TimingContext.
 */
@GraphQLApi
@ApplicationScoped
public class PersonGraphQL {

    @Inject
    PersonRepository personRepository;

    @Inject
    TimingContext timingContext;

    @Query("persons")
    @Description("Get all persons")
    @Timed(value = "graphql.query", extraTags = {"subgraph", "hr", "operation", "persons"})
    public List<Person> getAllPersons() {
        return timingContext.measureOperation("db_query", () -> personRepository.listAll());
    }

    @Query("person")
    @Description("Get a person by ID")
    @Timed(value = "graphql.query", extraTags = {"subgraph", "hr", "operation", "person"})
    public Person getPerson(@Name("id") String id) {
        return timingContext.measureOperation("db_query", () -> personRepository.findById(id));
    }

    @Query("personByEmail")
    @Description("Get a person by email")
    public Person getPersonByEmail(@Name("email") String email) {
        return timingContext.measureOperation("db_query", () -> personRepository.findByEmail(email).orElse(null));
    }

    @Query("activePersons")
    @Description("Get all active persons")
    public List<Person> getActivePersons() {
        return timingContext.measureOperation("db_query", () -> personRepository.findAllActive());
    }

    @Query("searchPersons")
    @Description("Search persons by name")
    public List<Person> searchPersons(@Name("name") String name) {
        return timingContext.measureOperation("db_query", () -> personRepository.searchByName(name));
    }

    /**
     * Federation entity resolver for Person.
     * Called when other subgraphs reference a Person by ID.
     */
    @Resolver
    @Timed(value = "graphql.resolve", extraTags = {"subgraph", "hr", "entity", "Person"})
    public Person resolvePerson(@Name("id") String id) {
        return timingContext.measureOperation("db_resolve", () -> personRepository.findById(id));
    }

    @Mutation("createPerson")
    @Description("Create a new person")
    @Transactional
    public Person createPerson(
            @Name("name") String name,
            @Name("email") String email,
            @Name("hireDate") LocalDate hireDate) {

        String id = "person-" + UUID.randomUUID().toString().substring(0, 8);
        Person person = new Person(id, name, email, hireDate);
        timingContext.measureOperation("db_write", () -> {
            personRepository.persist(person);
            return null;
        });
        return person;
    }

    @Mutation("updatePerson")
    @Description("Update an existing person")
    @Transactional
    public Person updatePerson(
            @Name("id") String id,
            @Name("name") String name,
            @Name("email") String email) {

        Person person = timingContext.measureOperation("db_read", () -> personRepository.findById(id));
        if (person == null) {
            return null;
        }
        if (name != null) {
            person.name = name;
        }
        if (email != null) {
            person.email = email;
        }
        timingContext.recordTiming("db_write", 0); // Implicit on transaction commit
        return person;
    }

    @Mutation("terminatePerson")
    @Description("Mark a person as terminated (inactive)")
    @Transactional
    public Person terminatePerson(@Name("id") String id) {
        Person person = timingContext.measureOperation("db_read", () -> personRepository.findById(id));
        if (person == null) {
            return null;
        }
        person.active = false;
        timingContext.recordTiming("db_write", 0); // Implicit on transaction commit
        return person;
    }
}
