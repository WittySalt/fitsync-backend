package com.fitsync.persistence.outbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

@MappedSuperclass
public abstract class BaseOutboxEventEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "topic", nullable = false, length = 120)
    private String topic;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 120)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 120)
    private String aggregateId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "date_time_produced", nullable = false)
    private Instant dateTimeProduced;

    @Column(name = "date_time_published")
    private Instant dateTimePublished;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "date_time_created", nullable = false, updatable = false)
    private Instant dateTimeCreated;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (dateTimeProduced == null) {
            dateTimeProduced = now;
        }
        if (dateTimeCreated == null) {
            dateTimeCreated = now;
        }
    }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public int getEventVersion() { return eventVersion; }
    public void setEventVersion(int eventVersion) { this.eventVersion = eventVersion; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Instant getDateTimeProduced() { return dateTimeProduced; }
    public void setDateTimeProduced(Instant dateTimeProduced) { this.dateTimeProduced = dateTimeProduced; }
    public Instant getDateTimePublished() { return dateTimePublished; }
    public void setDateTimePublished(Instant dateTimePublished) { this.dateTimePublished = dateTimePublished; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getDateTimeCreated() { return dateTimeCreated; }
    public void setDateTimeCreated(Instant dateTimeCreated) { this.dateTimeCreated = dateTimeCreated; }
}
