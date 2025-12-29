package com.example.employment.resource;

import com.example.employment.dto.EmployeeInput;
import com.example.employment.model.Employee;
import com.example.employment.service.EmployeeService;
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

import java.math.BigDecimal;
import java.util.List;

@Path("/api/employees")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EmployeeResource {

    @Inject
    EmployeeService employeeService;

    @GET
    public List<Employee> getAllEmployees() {
        return employeeService.getAllEmployees();
    }

    @GET
    @Path("/{id}")
    public Response getEmployee(@PathParam("id") String id) {
        Employee employee = employeeService.getEmployee(id);
        if (employee == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(employee).build();
    }

    @GET
    @Path("/by-person/{personId}")
    public Response getEmployeeByPersonId(@PathParam("personId") String personId) {
        Employee employee = employeeService.getEmployeeByPersonId(personId);
        if (employee == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(employee).build();
    }

    @POST
    public Response assignEmployee(EmployeeInput input) {
        Employee employee = employeeService.assignEmployee(input);
        if (employee == null) {
            return Response.status(Response.Status.CONFLICT)
                .entity("Person already has an employee record").build();
        }
        return Response.status(Response.Status.CREATED).entity(employee).build();
    }

    @PUT
    @Path("/{id}/promote")
    public Response promoteEmployee(
            @PathParam("id") String id,
            @QueryParam("title") String newTitle,
            @QueryParam("salary") BigDecimal newSalary) {

        Employee employee = employeeService.promoteEmployee(id, newTitle, newSalary);
        if (employee == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(employee).build();
    }

    @PUT
    @Path("/{id}/transfer")
    public Response transferEmployee(
            @PathParam("id") String id,
            @QueryParam("department") String newDepartment) {

        Employee employee = employeeService.transferEmployee(id, newDepartment);
        if (employee == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(employee).build();
    }

    @POST
    @Path("/{id}/terminate")
    public Response terminateEmployee(@PathParam("id") String id) {
        Employee employee = employeeService.terminateEmployee(id);
        if (employee == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(employee).build();
    }
}
