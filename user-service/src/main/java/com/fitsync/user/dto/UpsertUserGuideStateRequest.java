package com.fitsync.user.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record UpsertUserGuideStateRequest(JsonNode state) {
}
