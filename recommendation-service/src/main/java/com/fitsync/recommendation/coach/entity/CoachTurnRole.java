package com.fitsync.recommendation.coach.entity;

/**
 * Who produced a turn.
 *
 * <p>{@link #TOOL} is not a speaker. It marks an observation the orchestrator appended to the
 * thread after a tool returned, so that a later turn can be reconstructed with the same context
 * the model actually saw. Keeping it in the same table as the conversation means a thread replays
 * exactly as it ran, which is what the trace view needs.
 */
public enum CoachTurnRole {
    USER,
    ASSISTANT,
    TOOL
}
