package com.fitsync.user.entity;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import com.fitsync.user.domain.AccountDataExportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "account_data_export_requests")
public class AccountDataExportRequestEntity extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "export_request_id", nullable = false)
    private UUID exportRequestId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AccountDataExportStatus status = AccountDataExportStatus.REQUESTED;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public UUID getExportRequestId() { return exportRequestId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public AccountDataExportStatus getStatus() { return status; }
    public void setStatus(AccountDataExportStatus status) { this.status = status; }
    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
