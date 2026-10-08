package com.fitsync.workout.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fitsync.media.MediaStorageService;
import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.GoalFocus;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.dto.CreateExerciseRequest;
import com.fitsync.workout.dto.ExerciseImageView;
import com.fitsync.workout.dto.ExerciseInstructionView;
import com.fitsync.workout.dto.ExerciseMetadataView;
import com.fitsync.workout.dto.ExerciseView;
import com.fitsync.workout.dto.SportActivityView;
import com.fitsync.workout.dto.UpdateExerciseRequest;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.entity.ExerciseImageEntity;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.repository.ExerciseShortStepRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.web.error.BadRequestException;
import com.fitsync.web.error.ForbiddenException;
import com.fitsync.web.error.ResourceNotFoundException;
import com.fitsync.web.response.PageResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The exercise library: the shared catalogue (no owner) plus each user's own custom exercises. A user sees the
 * catalogue and their own exercises; only their own can be edited, archived or given uploaded media.
 */
@Service
public class ExerciseServiceImpl implements ExerciseService {

    private static final long MAX_EXERCISE_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_EXERCISE_VIDEO_BYTES = 50L * 1024 * 1024;
    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp");
    private static final Map<String, String> VIDEO_EXTENSIONS = Map.of(
            "video/mp4", "mp4",
            "video/webm", "webm");

    private final ExerciseRepository exerciseRepository;
    private final ExerciseShortStepRepository shortStepRepository;
    private final ObjectProvider<MediaStorageService> mediaStorageServiceProvider;

    public ExerciseServiceImpl(ExerciseRepository exerciseRepository,
                               ExerciseShortStepRepository shortStepRepository,
                               ObjectProvider<MediaStorageService> mediaStorageServiceProvider) {
        this.exerciseRepository = exerciseRepository;
        this.shortStepRepository = shortStepRepository;
        this.mediaStorageServiceProvider = mediaStorageServiceProvider;
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
        entity.setVideoUrl(normalizeOptionalUrl(request.videoUrl()));
        entity.setStatus(ExerciseStatus.ACTIVE);
        entity.setCustom(true);
        return toDetailView(exerciseRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public ExerciseView getExercise(CurrentUserPrincipal currentUser, UUID exerciseId) {
        return toDetailView(findAccessibleExercise(currentUser.userId(), exerciseId));
    }

    @Override
    public ExerciseMetadataView getExerciseMetadata() {
        return new ExerciseMetadataView(
                List.of(ExerciseType.values()),
                List.of(MuscleGroup.values()),
                List.of(ExerciseEquipment.values()),
                List.of(WorkoutDifficulty.values()),
                List.of(WorkoutType.values()),
                List.of(GoalFocus.values()),
                Arrays.stream(SportActivity.values())
                        .map(activity -> new SportActivityView(activity, activity.label(), activity.effort(), activity.measure()))
                        .toList());
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
        entity.setVideoUrl(normalizeOptionalUrl(request.videoUrl()));
        return toDetailView(exerciseRepository.save(entity));
    }

    @Override
    @Transactional
    public ExerciseView uploadExerciseVideo(CurrentUserPrincipal currentUser, UUID exerciseId, MultipartFile file) {
        ExerciseEntity entity = findMediaEditableExercise(currentUser, exerciseId);
        String contentType = validateMedia(file, VIDEO_EXTENSIONS, MAX_EXERCISE_VIDEO_BYTES,
                "Exercise video must be video/mp4 or video/webm",
                "Exercise video file size must be 50MB or less");
        String extension = VIDEO_EXTENSIONS.get(contentType);
        String key = "exercises/" + entity.getExerciseId() + "/videos/" + UUID.randomUUID() + "." + extension;
        String videoUrl = storeMedia(key, file, contentType, "Exercise video file could not be read");

        entity.setVideoUrl(videoUrl);
        return toDetailView(exerciseRepository.save(entity));
    }

    @Override
    @Transactional
    public ExerciseView uploadExerciseImage(CurrentUserPrincipal currentUser, UUID exerciseId, MultipartFile file) {
        ExerciseEntity entity = findMediaEditableExercise(currentUser, exerciseId);
        String contentType = validateMedia(file, IMAGE_EXTENSIONS, MAX_EXERCISE_IMAGE_BYTES,
                "Exercise image must be image/png, image/jpeg, or image/webp",
                "Exercise image file size must be 5MB or less");
        String extension = IMAGE_EXTENSIONS.get(contentType);
        String key = "exercises/" + entity.getExerciseId() + "/images/" + UUID.randomUUID() + "." + extension;
        String imageUrl = storeMedia(key, file, contentType, "Exercise image file could not be read");

        ExerciseImageEntity image = new ExerciseImageEntity();
        image.setImageId(UUID.randomUUID());
        image.setExercise(entity);
        image.setPosition(nextImagePosition(entity));
        image.setUrl(imageUrl);
        entity.getImages().add(image);
        return toDetailView(exerciseRepository.save(entity));
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
    public List<ExerciseView> exportCustomExercises(CurrentUserPrincipal currentUser) {
        return exerciseRepository.findByOwnerUserIdOrderByNameAsc(currentUser.userId())
                .stream()
                .map(this::toSummaryView)
                .toList();
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
                .map(this::toSummaryView));
    }

    private String normalizeCode(String code) {
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private ExerciseEntity findAccessibleExercise(UUID userId, UUID exerciseId) {
        return exerciseRepository.findAccessibleById(exerciseId, userId, ExerciseStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise not found"));
    }

    /**
     * A catalogue exercise is refused with 403. Another user's exercise answers 404, so its existence is not
     * revealed.
     */
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

    /**
     * Looked up by id rather than by the caller's access, because an admin may add media to any user's custom
     * exercise. Catalogue exercises take their images from the importer only.
     */
    private ExerciseEntity findMediaEditableExercise(CurrentUserPrincipal currentUser, UUID exerciseId) {
        ExerciseEntity entity = exerciseRepository.findById(exerciseId)
                .filter(exercise -> exercise.getStatus() == ExerciseStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise not found"));
        if (!entity.isCustom() || entity.getOwnerUserId() == null) {
            throw new ForbiddenException("Catalog exercises cannot accept uploaded media");
        }
        if (!isAdmin(currentUser) && !entity.getOwnerUserId().equals(currentUser.userId())) {
            throw new ResourceNotFoundException("Exercise not found");
        }
        return entity;
    }

    private boolean isAdmin(CurrentUserPrincipal currentUser) {
        return "ADMIN".equals(currentUser.role());
    }

    private String validateMedia(MultipartFile file,
                                 Map<String, String> allowedTypes,
                                 long maxBytes,
                                 String typeMessage,
                                 String sizeMessage) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Exercise media file is required");
        }
        String rawContentType = file.getContentType();
        String contentType = rawContentType == null
                ? ""
                : rawContentType.trim().toLowerCase(Locale.ROOT);
        if (!allowedTypes.containsKey(contentType)) {
            throw new BadRequestException(typeMessage);
        }
        if (file.getSize() > maxBytes) {
            throw new BadRequestException(sizeMessage);
        }
        return contentType;
    }

    private String storeMedia(String key, MultipartFile file, String contentType, String readFailureMessage) {
        try {
            return mediaStorageServiceProvider.getObject()
                    .put(key, file.getInputStream(), file.getSize(), contentType);
        } catch (IOException ex) {
            throw new BadRequestException(readFailureMessage);
        }
    }

    private int nextImagePosition(ExerciseEntity entity) {
        return entity.getImages().stream()
                .map(ExerciseImageEntity::getPosition)
                .filter(position -> position != null)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(-1) + 1;
    }

    private String normalizeOptionalUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return url.trim();
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

    private ExerciseView toSummaryView(ExerciseEntity entity) {
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

    private ExerciseView toDetailView(ExerciseEntity entity) {
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
                entity.getDateTimeUpdated(),
                entity.getVideoUrl(),
                entity.getAnimationUrl(),
                entity.getExternalSource(),
                entity.getExternalRef(),
                entity.getInstructions().stream()
                        .map(instruction -> new ExerciseInstructionView(
                                instruction.getStepNumber(),
                                instruction.getText()))
                        .toList(),
                entity.getImages().stream()
                        .map(image -> new ExerciseImageView(
                                image.getPosition(),
                                image.getUrl()))
                        .toList(),
                entity.getSecondaryMuscles().stream()
                        .map(secondaryMuscle -> secondaryMuscle.getMuscleGroup())
                        .toList(),
                shortStepRepository.findTextsByExerciseCode(entity.getCode()));
    }
}
