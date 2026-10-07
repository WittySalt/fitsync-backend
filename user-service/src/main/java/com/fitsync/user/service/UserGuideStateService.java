package com.fitsync.user.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.user.dto.UserGuideStateView;
import com.fitsync.user.entity.UserGuideStateEntity;
import com.fitsync.user.repository.UserGuideStateRepository;
import com.fitsync.web.error.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the frontend's guide (tour) state as one opaque JSON object per user. The backend only checks the shape
 * (a JSON object) and the size cap; the contents belong to the frontend.
 */
@Service
public class UserGuideStateService {

    static final int MAX_STATE_BYTES = 8 * 1024;

    private final UserGuideStateRepository repository;
    private final ObjectMapper objectMapper;

    public UserGuideStateService(UserGuideStateRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public UserGuideStateView get(UUID userId) {
        return repository.findById(userId)
                .map(this::toView)
                .orElseGet(() -> new UserGuideStateView(null, null));
    }

    @Transactional
    public UserGuideStateView upsert(UUID userId, JsonNode state) {
        if (state == null || !state.isObject()) {
            throw new BadRequestException("Guide state must be a JSON object");
        }
        String json = state.toString();
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_STATE_BYTES) {
            throw new BadRequestException("Guide state must be 8 kB or less");
        }
        Instant now = Instant.now(Clock.systemUTC());
        UserGuideStateEntity entity = repository.findById(userId)
                .orElseGet(() -> new UserGuideStateEntity(userId, json, now));
        entity.setState(json);
        entity.setUpdatedAt(now);
        return toView(repository.save(entity));
    }

    private UserGuideStateView toView(UserGuideStateEntity entity) {
        try {
            return new UserGuideStateView(objectMapper.readTree(entity.getState()), entity.getUpdatedAt());
        } catch (JsonProcessingException ex) {
            // A stored value that no longer parses is treated as "no state" so the client starts clean.
            return new UserGuideStateView(null, null);
        }
    }
}
