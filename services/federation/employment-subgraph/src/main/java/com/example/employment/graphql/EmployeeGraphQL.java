package com.example.employment.graphql;

import com.example.employment.model.Employee;
import com.example.employment.model.Person;
import com.example.employment.repository.EmployeeRepository;
import com.example.employment.timing.TimingContext;
import io.smallrye.graphql.api.federation.FieldSet;
import io.smallrye.graphql.api.federation.Requires;
import io.smallrye.graphql.api.federation.Resolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Source;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * GraphQL API for Employment/Employee subgraph.
 * This subgraph owns Employee and extends Person with employee data.
 * All DB operations are timed and recorded in TimingContext.
 */
@GraphQLApi
@ApplicationScoped
public class EmployeeGraphQL {

    @Inject
    EmployeeRepository employeeRepository;

    @Inject
    TimingContext timingContext;

    @Query("employees")
    @Description("Get all employees")
    public List<Employee> getAllEmployees() {
        return timingContext.measureOperation("db_query", () -> employeeRepository.listAll());
    }

    @Query("employee")
    @Description("Get an employee by ID")
    public Employee getEmployee(@Name("id") String id) {
        return timingContext.measureOperation("db_query", () -> employeeRepository.findById(id));
    }

    @Query("employeeByPersonId")
    @Description("Get an employee by person ID")
    public Employee getEmployeeByPersonId(@Name("personId") String personId) {
        return timingContext.measureOperation("db_query", () -> employeeRepository.findByPersonId(personId).orElse(null));
    }

    @Query("employeesByDepartment")
    @Description("Get employees by department")
    public List<Employee> getEmployeesByDepartment(@Name("department") String department) {
        return timingContext.measureOperation("db_query", () -> employeeRepository.findByDepartment(department));
    }

    @Query("activeEmployees")
    @Description("Get all active employees")
    public List<Employee> getActiveEmployees() {
        return timingContext.measureOperation("db_query", () -> employeeRepository.findAllActive());
    }

    /**
     * Federation entity resolver for Employee.
     * Called when other subgraphs reference an Employee by ID.
     */
    @Resolver
    public Employee resolveEmployee(@Name("id") String id) {
        return timingContext.measureOperation("db_resolve", () -> employeeRepository.findById(id));
    }

    /**
     * Federation entity resolver for Person.
     * Resolves Person entities when the router needs employee information.
     */
    @Resolver
    public Person resolvePerson(@Name("id") String id) {
        return new Person(id);
    }

    /**
     * Field resolver that adds 'employee' field to Person.
     * This is resolved by the employment-subgraph when a Person is queried.
     */
    @Description("The employee record for this person (if they are an employee)")
    public Employee employee(@Source Person person) {
        if (person.getId() == null) {
            return null;
        }
        return timingContext.measureOperation("db_resolve", () -> employeeRepository.findByPersonId(person.getId()).orElse(null));
    }

    @Mutation("assignEmployee")
    @Description("Create an employee record for a person")
    @Transactional
    public Employee assignEmployee(
            @Name("personId") String personId,
            @Name("title") String title,
            @Name("department") String department,
            @Name("salary") BigDecimal salary) {

        // Check if already assigned
        if (timingContext.measureOperation("db_read", () -> employeeRepository.findByPersonId(personId)).isPresent()) {
            return null; // Already exists
        }

        String id = "emp-" + UUID.randomUUID().toString().substring(0, 8);
        Employee employee = new Employee(id, personId, title, department, salary);
        timingContext.measureOperation("db_write", () -> { employeeRepository.persist(employee); return null; });
        return employee;
    }

    @Mutation("promoteEmployee")
    @Description("Promote an employee to a new title and/or salary")
    @Transactional
    public Employee promoteEmployee(
            @Name("id") String id,
            @Name("newTitle") String newTitle,
            @Name("newSalary") BigDecimal newSalary) {

        Employee employee = timingContext.measureOperation("db_read", () -> employeeRepository.findById(id));
        if (employee == null) {
            return null;
        }
        if (newTitle != null) {
            employee.title = newTitle;
        }
        if (newSalary != null) {
            employee.salary = newSalary;
        }
        return employee;
    }

    @Mutation("transferEmployee")
    @Description("Transfer an employee to a new department")
    @Transactional
    public Employee transferEmployee(
            @Name("id") String id,
            @Name("newDepartment") String newDepartment) {

        Employee employee = timingContext.measureOperation("db_read", () -> employeeRepository.findById(id));
        if (employee == null) {
            return null;
        }
        employee.department = newDepartment;
        return employee;
    }

    @Mutation("terminateEmployee")
    @Description("Mark an employee as terminated (inactive)")
    @Transactional
    public Employee terminateEmployee(@Name("id") String id) {
        Employee employee = timingContext.measureOperation("db_read", () -> employeeRepository.findById(id));
        if (employee == null) {
            return null;
        }
        employee.active = false;
        return employee;
    }
}
