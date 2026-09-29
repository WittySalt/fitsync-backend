package com.fitsync.recommendation.coach.repository;

/**
 * The columns {@link ToolInvocationRepository#findQualityRows} projects for the admin quality
 * report: which tool, whether it failed, how long it took. Never {@code argsJson} or
 * {@code resultSummary}, both of which can carry data derived from a user's records.
 */
public record ToolInvocationQualityRow(String tool, boolean ok, Integer latencyMs) {
}
