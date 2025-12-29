package com.example.projection.consumer;

import com.example.projection.model.BadgeProjection;
import com.example.projection.model.ProcessedEvent;
import com.example.projection.repository.BadgeProjectionRepository;
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

@ApplicationScoped
public class BadgeEventConsumer {

    private static final Logger LOG = Logger.getLogger(BadgeEventConsumer.class);

    @Inject
    BadgeProjectionRepository badgeRepository;

    @Inject
    ProcessedEventRepository processedEventRepository;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("badge-events")
    @Transactional
    public void consume(Record<String, String> record) {
        try {
            String key = record.key();
            String value = record.value();

            LOG.infof("Received badge event for key: %s", key);

            JsonNode eventNode = objectMapper.readTree(value);
            String eventType = eventNode.get("eventType").asText();
            String badgeId = eventNode.get("badgeId").asText();
            JsonNode data = eventNode.get("data");

            String eventId = "badge-" + badgeId + "-" + eventNode.get("timestamp").asText();

            if (processedEventRepository.hasProcessed(eventId)) {
                LOG.infof("Skipping already processed event: %s", eventId);
                return;
            }

            BadgeProjection projection = badgeRepository.findById(badgeId);
            if (projection == null) {
                projection = new BadgeProjection();
                projection.id = badgeId;
                projection.lastUpdated = Instant.now();
            }

            String personId = data.get("personId").asText();
            String badgeNumber = data.get("badgeNumber").asText();
            String accessLevel = data.get("accessLevel").asText();
            String clearance = data.get("clearance").asText();
            boolean active = data.get("active").asBoolean();

            projection.updateFrom(personId, badgeNumber, accessLevel, clearance, active);

            badgeRepository.persist(projection);

            processedEventRepository.persist(new ProcessedEvent(eventId, "events.security.badge", 0L, 0));

            LOG.infof("Processed %s for badge %s", eventType, badgeId);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to process badge event");
            throw new RuntimeException("Failed to process badge event", e);
        }
    }
}
