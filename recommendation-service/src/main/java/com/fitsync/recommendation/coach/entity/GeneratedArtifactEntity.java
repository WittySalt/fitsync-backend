package com.fitsync.recommendation.coach.entity;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Something the coach produced that a user can act on.
 *
 * <p>Stored as the serialised draft rather than as normalised rows on purpose. A draft is not the
 * domain object; it is a proposal about one, and accepting it is what creates the real thing in the
 * service that owns it. Modelling a proposed session as a session would mean two tables that look
 * alike and drift, and it would put workout-service's schema inside this one.
 *
 * <p>The generation columns are the point of the table as much as the payload is. Whether a draft
 * passed first time, needed a repair, or came from the rules cannot be reconstructed later, so it
 * is written as it happens.
 */
@Entity
@Table(name = "coach_generated_artifact")
public class GeneratedArtifactEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "artifact_id", nullable = false)
    private UUID artifactId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Null when the draft was produced from a studio surface rather than inside a conversation. */
    @Column(name = "thread_id")
    private UUID threadId;

    @Column(name = "turn_id")
    private UUID turnId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private ArtifactKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ArtifactStatus status = ArtifactStatus.DRAFT;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private DraftSource source;

    @Column(name = "validation_attempts", nullable = false)
    private int validationAttempts = 1;

    /**
     * What the first validation found, kept even when a repair fixed it. The version that finally
     * passed says less about the model than the one that did not.
     */
    @Column(name = "first_pass_violations", columnDefinition = "text")
    private String firstPassViolations;

    /** The plan or session this became, set when the draft is accepted. */
    @Column(name = "accepted_ref")
    private UUID acceptedRef;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    public UUID getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(UUID artifactId) {
        this.artifactId = artifactId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getThreadId() {
        return threadId;
    }

    public void setThreadId(UUID threadId) {
        this.threadId = threadId;
    }

    public UUID getTurnId() {
        return turnId;
    }

    public void setTurnId(UUID turnId) {
        this.turnId = turnId;
    }

    public ArtifactKind getKind() {
        return kind;
    }

    public void setKind(ArtifactKind kind) {
        this.kind = kind;
    }

    public ArtifactStatus getStatus() {
        return status;
    }

    public void setStatus(ArtifactStatus status) {
        this.status = status;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public DraftSource getSource() {
        return source;
    }

    public void setSource(DraftSource source) {
        this.source = source;
    }

    public int getValidationAttempts() {
        return validationAttempts;
    }

    public void setValidationAttempts(int validationAttempts) {
        this.validationAttempts = validationAttempts;
    }

    public String getFirstPassViolations() {
        return firstPassViolations;
    }

    public void setFirstPassViolations(String firstPassViolations) {
        this.firstPassViolations = firstPassViolations;
    }

    public UUID getAcceptedRef() {
        return acceptedRef;
    }

    public void setAcceptedRef(UUID acceptedRef) {
        this.acceptedRef = acceptedRef;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }
}
