package com.example.hr.resource;

import com.example.hr.dto.PersonInput;
import com.example.hr.model.Person;
import com.example.hr.service.PersonService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * REST API for HR/Person operations in the Events architecture.
 */
@Path("/api/persons")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PersonResource {

    @Inject
    PersonService personService;

    @GET
    public List<Person> getAllPersons() {
        return personService.getAllPersons();
    }

    @GET
    @Path("/{id}")
    public Response getPerson(@PathParam("id") String id) {
        Person person = personService.getPerson(id);
        if (person == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(person).build();
    }

    @POST
    public Response createPerson(PersonInput input) {
        Person person = personService.createPerson(input);
        return Response.status(Response.Status.CREATED).entity(person).build();
    }

    @PUT
    @Path("/{id}")
    public Response updatePerson(
            @PathParam("id") String id,
            @QueryParam("name") String name,
            @QueryParam("email") String email) {

        Person person = personService.updatePerson(id, name, email);
        if (person == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(person).build();
    }

    @POST
    @Path("/{id}/terminate")
    public Response terminatePerson(@PathParam("id") String id) {
        Person person = personService.terminatePerson(id);
        if (person == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(person).build();
    }
}
