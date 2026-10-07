package com.fitsync.user.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

public record UserGuideStateView(JsonNode state, Instant updatedAt) {
}
