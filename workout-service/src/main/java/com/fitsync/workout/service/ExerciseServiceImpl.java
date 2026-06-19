package com.fitsync.workout.service;

import java.util.Locale;
import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.dto.CreateExerciseRequest;
import com.fitsync.workout.dto.ExerciseView;
import com.fitsync.workout.dto.UpdateExerciseRequest;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.web.error.ForbiddenException;
import com.fitsync.web.error.ResourceNotFoundException;
import com.fitsync.web.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExerciseServiceImpl implements ExerciseService {

    private final ExerciseRepository exerciseRepository;

    public ExerciseServiceImpl(ExerciseRepository exerciseRepository) {
        this.exerciseRepository = exerciseRepository;
    }

    @Override
    @Transactional
    public ExerciseView createCustomExercise(CurrentUserPrincipal currentUser, CreateExerciseRequest request) {
        String code = normalizeCode(request.code());
        if (exerciseRepository.existsByCodeAndOwnerUserId(code, currentUser.userId())) {
            throw new IllegalArgumentException("Exercise code already exists");
        }

        ExerciseEntity entity = new ExerciseEntity();
        entity.setExerciseId(UUID.randomUUID());
        entity.setOwnerUserId(currentUser.userId());
        entity.setCode(code);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setExerciseType(request.exerciseType());
        entity.setPrimaryMuscleGroup(request.primaryMuscleGroup());
        entity.setEquipment(request.equipment());
        entity.setDifficulty(request.difficulty());
        entity.setStatus(ExerciseStatus.ACTIVE);
        entity.setCustom(true);
        return toView(exerciseRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public ExerciseView getExercise(CurrentUserPrincipal currentUser, UUID exerciseId) {
        return toView(findAccessibleExercise(currentUser.userId(), exerciseId));
    }

    @Override
    @Transactional
    public ExerciseView updateCustomExercise(CurrentUserPrincipal currentUser,
                                             UUID exerciseId,
                                             UpdateExerciseRequest request) {
        ExerciseEntity entity = findEditableCustomExercise(currentUser.userId(), exerciseId);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setExerciseType(request.exerciseType());
        entity.setPrimaryMuscleGroup(request.primaryMuscleGroup());
        entity.setEquipment(request.equipment());
        entity.setDifficulty(request.difficulty());
        return toView(exerciseRepository.save(entity));
    }

    @Override
    @Transactional
    public void archiveCustomExercise(CurrentUserPrincipal currentUser, UUID exerciseId) {
        ExerciseEntity entity = findEditableCustomExercise(currentUser.userId(), exerciseId);
        entity.setStatus(ExerciseStatus.ARCHIVED);
        exerciseRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExerciseView> listExercises(CurrentUserPrincipal currentUser,
                                                    String query,
                                                    ExerciseType exerciseType,
                                                    MuscleGroup primaryMuscleGroup,
                                                    ExerciseEquipment equipment,
                                                    WorkoutDifficulty difficulty,
                                                    int page,
                                                    int size) {
        return toPageResponse(exerciseRepository.searchAccessible(
                        currentUser.userId(),
                        ExerciseStatus.ACTIVE,
                        normalizeQuery(query),
                        exerciseType,
                        primaryMuscleGroup,
                        equipment,
                        difficulty,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name")))
                .map(this::toView));
    }

    private String normalizeCode(String code) {
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private ExerciseEntity findAccessibleExercise(UUID userId, UUID exerciseId) {
        return exerciseRepository.findAccessibleById(exerciseId, userId, ExerciseStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise not found"));
    }

    private ExerciseEntity findEditableCustomExercise(UUID userId, UUID exerciseId) {
        ExerciseEntity entity = findAccessibleExercise(userId, exerciseId);
        if (entity.getOwnerUserId() == null) {
            throw new ForbiddenException("Global exercises cannot be modified");
        }
        if (!entity.getOwnerUserId().equals(userId)) {
            throw new ResourceNotFoundException("Exercise not found");
        }
        return entity;
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim();
    }

    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return PageResponse.of(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private ExerciseView toView(ExerciseEntity entity) {
        return new ExerciseView(
                entity.getExerciseId(),
                entity.getOwnerUserId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getExerciseType(),
                entity.getPrimaryMuscleGroup(),
                entity.getEquipment(),
                entity.getDifficulty(),
                entity.getStatus(),
                entity.isCustom(),
                entity.getDateTimeCreated(),
                entity.getDateTimeUpdated());
    }
}
