package com.fitsync.recommendation.coach.entity;

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
 * One turn in a thread.
 *
 * <p>The telemetry columns are the reason this table is shaped the way it is. Which tier answered,
 * on which model, for how many tokens and how long, is not collected for a dashboard somewhere; it
 * is rendered in the product under every answer. Recording it on the turn means the trace a user
 * expands is the same record the evaluation harness aggregates, so the two can never disagree.
 *
 * <p>All of those columns are null on a {@link CoachTurnRole#USER} turn, and a tier of 0 or 1 means
 * no model was called at all, which is the common case and worth being able to prove.
 */
@Entity
@Table(
        name = "coach_turn",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_coach_turn_thread_seq",
                columnNames = {"thread_id", "seq"}))
public class CoachTurnEntity extends BaseAuditableEntity {

    /** Long enough for {@code ANSWER_REJECTED:} followed by the longest rule and its detail. */
    public static final int GUARDRAIL_VERDICT_LENGTH = 64;

    @Id
    @Column(name = "turn_id", nullable = false)
    private UUID turnId;

    @Column(name = "thread_id", nullable = false)
    private UUID threadId;

    /** Position within the thread, starting at 1. Unique per thread, so ordering never relies on time. */
    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private CoachTurnRole role;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    /** 0 rule · 1 template · 2 local model · 3 frontier model. Null on a user turn. */
    @Column(name = "tier")
    private Integer tier;

    @Column(name = "model", length = 80)
    private String model;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    /** {@code PASSED}, {@code REFUSED_UNSAFE}, {@code REFUSED_OUT_OF_SCOPE}, and so on. */
    @Column(name = "guardrail_verdict", length = GUARDRAIL_VERDICT_LENGTH)
    private String guardrailVerdict;

    public UUID getTurnId() {
        return turnId;
    }

    public void setTurnId(UUID turnId) {
        this.turnId = turnId;
    }

    public UUID getThreadId() {
        return threadId;
    }

    public void setThreadId(UUID threadId) {
        this.threadId = threadId;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public CoachTurnRole getRole() {
        return role;
    }

    public void setRole(CoachTurnRole role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getTier() {
        return tier;
    }

    public void setTier(Integer tier) {
        this.tier = tier;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getGuardrailVerdict() {
        return guardrailVerdict;
    }

    /** A verdict longer than the column keeps its start, which is the part that names the rule. */
    public void setGuardrailVerdict(String guardrailVerdict) {
        this.guardrailVerdict = guardrailVerdict != null && guardrailVerdict.length() > GUARDRAIL_VERDICT_LENGTH
                ? guardrailVerdict.substring(0, GUARDRAIL_VERDICT_LENGTH)
                : guardrailVerdict;
    }
}
