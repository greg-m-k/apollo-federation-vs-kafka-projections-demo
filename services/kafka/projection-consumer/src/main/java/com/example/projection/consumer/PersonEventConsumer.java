package com.example.projection.consumer;

import com.example.projection.model.PersonProjection;
import com.example.projection.model.ProcessedEvent;
import com.example.projection.repository.PersonProjectionRepository;
import com.example.projection.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.LocalDate;

@ApplicationScoped
public class PersonEventConsumer {

    private static final Logger LOG = Logger.getLogger(PersonEventConsumer.class);

    @Inject
    PersonProjectionRepository personRepository;

    @Inject
    ProcessedEventRepository processedEventRepository;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("person-events")
    @Transactional
    public void consume(Record<String, String> record) {
        try {
            String key = record.key();
            String value = record.value();

            LOG.infof("Received person event for key: %s", key);

            JsonNode eventNode = objectMapper.readTree(value);
            String eventType = eventNode.get("eventType").asText();
            String personId = eventNode.get("personId").asText();
            JsonNode data = eventNode.get("data");

            // Generate unique event ID for idempotency
            String eventId = "person-" + personId + "-" + eventNode.get("timestamp").asText();

            // Idempotency check
            if (processedEventRepository.hasProcessed(eventId)) {
                LOG.infof("Skipping already processed event: %s", eventId);
                return;
            }

            // Process event
            PersonProjection projection = personRepository.findById(personId);
            if (projection == null) {
                projection = new PersonProjection();
                projection.id = personId;
                projection.lastUpdated = Instant.now();
            }

            String name = data.get("name").asText();
            String email = data.get("email").asText();
            LocalDate hireDate = data.has("hireDate") && !data.get("hireDate").isNull()
                ? LocalDate.parse(data.get("hireDate").asText())
                : null;
            boolean active = data.get("active").asBoolean();

            projection.updateFrom(name, email, hireDate, active);

            personRepository.persist(projection);

            // Mark as processed
            processedEventRepository.persist(new ProcessedEvent(eventId, "events.hr.person", 0L, 0));

            LOG.infof("Processed %s for person %s", eventType, personId);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to process person event");
            throw new RuntimeException("Failed to process person event", e);
        }
    }
}
