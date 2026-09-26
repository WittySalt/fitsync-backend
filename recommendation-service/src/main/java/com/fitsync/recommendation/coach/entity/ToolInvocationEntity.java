package com.fitsync.recommendation.coach.entity;

import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One tool call made while answering a turn.
 *
 * <p>A child of the turn rather than a column on it, because a single turn can call several tools
 * and each needs its own timing and outcome. Two of those tools read catalogues owned by other
 * bounded contexts, so this table is also where cross-context access is audited: every read of the
 * exercise or food catalogue leaves a row saying what was asked and what came back.
 *
 * <p>{@link #resultSummary} is deliberately a summary. A catalogue search can return twenty rows
 * and none of that payload belongs in an audit table; what matters later is the shape of the answer,
 * not its contents.
 */
@Entity
@Table(
        name = "tool_invocation",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_tool_invocation_turn_seq",
                columnNames = {"turn_id", "seq"}))
public class ToolInvocationEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "invocation_id", nullable = false)
    private UUID invocationId;

    @Column(name = "turn_id", nullable = false)
    private UUID turnId;

    /** Order within the turn, starting at 1, so a trace reads in the order the loop ran. */
    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Column(name = "tool", nullable = false, length = 60)
    private String tool;

    @Column(name = "args_json", columnDefinition = "text")
    private String argsJson;

    @Column(name = "result_summary", length = 500)
    private String resultSummary;

    @Column(name = "latency_ms", nullable = false)
    private Integer latencyMs;

    @Column(name = "ok", nullable = false)
    private boolean ok;

    @Column(name = "error", length = 500)
    private String error;

    public UUID getInvocationId() {
        return invocationId;
    }

    public void setInvocationId(UUID invocationId) {
        this.invocationId = invocationId;
    }

    public UUID getTurnId() {
        return turnId;
    }

    public void setTurnId(UUID turnId) {
        this.turnId = turnId;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public String getArgsJson() {
        return argsJson;
    }

    public void setArgsJson(String argsJson) {
        this.argsJson = argsJson;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
