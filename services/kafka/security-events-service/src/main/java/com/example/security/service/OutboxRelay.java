package com.example.security.service;

import com.example.security.model.OutboxEvent;
import com.example.security.repository.OutboxRepository;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class OutboxRelay {

    private static final Logger LOG = Logger.getLogger(OutboxRelay.class);

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    @Channel("security-badge-out")
    Emitter<Record<String, String>> emitter;

    @Scheduled(every = "1s")
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> events = outboxRepository.findUnpublishedWithLimit(100);

        for (OutboxEvent event : events) {
            try {
                emitter.send(Record.of(event.aggregateId, event.payload));
                event.markPublished();
                LOG.infof("Published event: %s/%s [%s]",
                    event.aggregateType, event.aggregateId, event.eventType);
            } catch (Exception e) {
                LOG.errorf(e, "Failed to publish event %d", event.id);
            }
        }
    }
}
