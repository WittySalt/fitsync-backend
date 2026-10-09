package com.fitsync.auth.dto;

import java.util.List;

public record SessionListResponse(List<SessionSummary> sessions) {

    public SessionListResponse {
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
    }

    @Override
    public List<SessionSummary> sessions() {
        return List.copyOf(sessions);
    }
}
