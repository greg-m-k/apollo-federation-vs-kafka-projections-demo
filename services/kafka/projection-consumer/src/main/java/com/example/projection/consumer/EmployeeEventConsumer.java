package com.example.projection.consumer;

import com.example.projection.model.EmployeeProjection;
import com.example.projection.model.ProcessedEvent;
import com.example.projection.repository.EmployeeProjectionRepository;
import com.example.projection.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;

@ApplicationScoped
public class EmployeeEventConsumer {

    private static final Logger LOG = Logger.getLogger(EmployeeEventConsumer.class);

    @Inject
    EmployeeProjectionRepository employeeRepository;

    @Inject
    ProcessedEventRepository processedEventRepository;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("employee-events")
    @Transactional
    public void consume(Record<String, String> record) {
        try {
            String key = record.key();
            String value = record.value();

            LOG.infof("Received employee event for key: %s", key);

            JsonNode eventNode = objectMapper.readTree(value);
            String eventType = eventNode.get("eventType").asText();
            String employeeId = eventNode.get("employeeId").asText();
            JsonNode data = eventNode.get("data");

            String eventId = "employee-" + employeeId + "-" + eventNode.get("timestamp").asText();

            if (processedEventRepository.hasProcessed(eventId)) {
                LOG.infof("Skipping already processed event: %s", eventId);
                return;
            }

            EmployeeProjection projection = employeeRepository.findById(employeeId);
            if (projection == null) {
                projection = new EmployeeProjection();
                projection.id = employeeId;
                projection.lastUpdated = Instant.now();
            }

            String personId = data.get("personId").asText();
            String title = data.get("title").asText();
            String department = data.get("department").asText();
            BigDecimal salary = new BigDecimal(data.get("salary").asText());
            boolean active = data.get("active").asBoolean();

            projection.updateFrom(personId, title, department, salary, active);

            employeeRepository.persist(projection);

            processedEventRepository.persist(new ProcessedEvent(eventId, "events.employment.employee", 0L, 0));

            LOG.infof("Processed %s for employee %s", eventType, employeeId);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to process employee event");
            throw new RuntimeException("Failed to process employee event", e);
        }
    }
}
