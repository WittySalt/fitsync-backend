package com.fitsync.recommendation.coach.reading;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * The cached reading for one page, for one user: at most one row per (user, surface), because a
 * newer set of facts simply replaces what an older set produced rather than accumulating a history
 * nobody reads.
 *
 * <p>The row also carries a rejection. That is deliberate: a {@code REJECTED} row with its hash is
 * what stops the service from asking the model again for facts it already failed on, for the hour the
 * rejection is remembered. Storing only successes would mean re-running a failing prompt on every
 * request until it happened to pass, which is both wasteful and exactly the "keep trying until the
 * safety check gets tired" pattern the validator exists to prevent.
 */
@Entity
@Table(name = "coach_page_reading",
        uniqueConstraints = @UniqueConstraint(name = "uq_coach_page_reading_user_surface", columnNames = {"user_id", "surface"}))
public class CoachPageReadingEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "surface", nullable = false, length = 20)
    private ReadingSurface surface;

    /** The SHA-256 of the facts this row was generated from, so a changed page starts a new attempt. */
    @Column(name = "facts_hash", nullable = false, length = 64)
    private String factsHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private ReadingOutcome outcome;

    @Column(name = "headline", length = 200)
    private String headline;

    @Column(name = "body", length = 800)
    private String body;

    /** Comma-separated fact numbers the body cited, e.g. {@code "1,2"}. */
    @Column(name = "citations", length = 40)
    private String citations;

    @Column(name = "source", length = 20)
    private String source;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public ReadingSurface getSurface() {
        return surface;
    }

    public void setSurface(ReadingSurface surface) {
        this.surface = surface;
    }

    public String getFactsHash() {
        return factsHash;
    }

    public void setFactsHash(String factsHash) {
        this.factsHash = factsHash;
    }

    public ReadingOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(ReadingOutcome outcome) {
        this.outcome = outcome;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getCitations() {
        return citations;
    }

    public void setCitations(String citations) {
        this.citations = citations;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
