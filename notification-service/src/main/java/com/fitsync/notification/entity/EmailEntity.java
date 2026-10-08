package com.fitsync.notification.entity;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.domain.EmailStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_emails")
public class EmailEntity {

    @Id
    @Column(name = "email_id", nullable = false)
    private UUID emailId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private EmailKind kind;

    @Column(name = "dedupe_key", nullable = false, length = 80)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EmailStatus status;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "body_text", nullable = false)
    private String bodyText;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailEntity() {
    }

    public EmailEntity(UUID userId, EmailKind kind, String dedupeKey, String subject, String bodyText,
                       Instant dueAt, Instant expiresAt, Instant now) {
        this.emailId = UUID.randomUUID();
        this.userId = userId;
        this.kind = kind;
        this.dedupeKey = dedupeKey;
        this.status = EmailStatus.PENDING;
        this.subject = subject;
        this.bodyText = bodyText;
        this.dueAt = dueAt;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public UUID getEmailId() { return emailId; }
    public UUID getUserId() { return userId; }
    public EmailKind getKind() { return kind; }
    public String getDedupeKey() { return dedupeKey; }
    public EmailStatus getStatus() { return status; }
    public void setStatus(EmailStatus status) { this.status = status; }
    public String getSubject() { return subject; }
    public String getBodyText() { return bodyText; }
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public Instant getCreatedAt() { return createdAt; }
}
