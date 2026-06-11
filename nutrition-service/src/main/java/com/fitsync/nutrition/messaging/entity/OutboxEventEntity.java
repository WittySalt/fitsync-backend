package com.fitsync.nutrition.messaging.entity;

import com.fitsync.persistence.outbox.BaseOutboxEventEntity;
import com.fitsync.nutrition.messaging.OutboxEventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity extends BaseOutboxEventEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OutboxEventStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    public OutboxEventStatus getStatus() {
        return status;
    }

    public void setStatus(OutboxEventStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
