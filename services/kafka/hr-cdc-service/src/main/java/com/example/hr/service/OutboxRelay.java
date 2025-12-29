package com.example.hr.service;

import com.example.hr.model.OutboxEvent;
import com.example.hr.repository.OutboxRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Outbox relay that polls the outbox table and publishes events to Kafka.
 * Uses polling instead of Debezium for simplicity in this demo.
 */
@ApplicationScoped
public class OutboxRelay {

    private static final Logger LOG = Logger.getLogger(OutboxRelay.class);

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    @Channel("hr-person-out")
    Emitter<String> emitter;

    @Scheduled(every = "1s")
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> events = outboxRepository.findUnpublishedWithLimit(100);

        for (OutboxEvent event : events) {
            try {
                // Create metadata with the key
                OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata.<String>builder()
                    .withKey(event.aggregateId)
                    .build();

                // Use CompletableFuture to track ack/nack
                CompletableFuture<Void> future = new CompletableFuture<>();

                // Create message with payload, metadata, and ack/nack handlers
                Message<String> message = Message.of(event.payload)
                    .addMetadata(metadata)
                    .withAck(() -> {
                        future.complete(null);
                        return CompletableFuture.completedFuture(null);
                    })
                    .withNack(reason -> {
                        future.completeExceptionally(reason);
                        return CompletableFuture.completedFuture(null);
                    });

                // Send the message
                emitter.send(message);

                // Wait for acknowledgment
                future.get();

                // Mark as published only after confirmed send
                event.markPublished();

                LOG.infof("Published event to Kafka: %s/%s [%s]",
                    event.aggregateType, event.aggregateId, event.eventType);
            } catch (Exception e) {
                LOG.errorf(e, "Failed to publish event %d: %s", event.id, e.getMessage());
                // Don't mark as published - will retry on next poll
            }
        }
    }
}
