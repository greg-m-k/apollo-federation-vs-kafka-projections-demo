package com.example.projection.timing;

import java.time.Instant;

/**
 * Records timing breakdown for event propagation through the pipeline.
 * Tracks: eventCreated -> kafkaReceived -> projectionUpdated
 */
public record PropagationTiming(
    String entityId,
    String eventType,
    Instant eventCreatedAt,      // When hr-events-service created the event
    Instant kafkaReceivedAt,     // When consumer received from Kafka
    Instant projectionUpdatedAt, // When projection DB was updated
    long outboxToKafkaMs,        // eventCreated -> kafkaReceived
    long kafkaToConsumerMs,      // (included in outboxToKafka for now)
    long consumerToProjectionMs  // kafkaReceived -> projectionUpdated
) {
    public static PropagationTiming calculate(
            String entityId,
            String eventType,
            Instant eventCreatedAt,
            Instant kafkaReceivedAt,
            Instant projectionUpdatedAt) {

        long outboxToKafkaMs = kafkaReceivedAt.toEpochMilli() - eventCreatedAt.toEpochMilli();
        long consumerToProjectionMs = projectionUpdatedAt.toEpochMilli() - kafkaReceivedAt.toEpochMilli();

        return new PropagationTiming(
            entityId,
            eventType,
            eventCreatedAt,
            kafkaReceivedAt,
            projectionUpdatedAt,
            outboxToKafkaMs,
            0, // kafkaToConsumer included in outboxToKafka
            consumerToProjectionMs
        );
    }

    public long totalPropagationMs() {
        return outboxToKafkaMs + consumerToProjectionMs;
    }
}
