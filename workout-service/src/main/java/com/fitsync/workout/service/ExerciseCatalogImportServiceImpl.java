package com.fitsync.workout.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fitsync.media.MediaStorageService;
import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.dto.ExerciseCatalogImportResponse;
import com.fitsync.workout.dto.ExerciseCatalogUnmappedTermView;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.entity.ExerciseImageEntity;
import com.fitsync.workout.entity.ExerciseInstructionEntity;
import com.fitsync.workout.entity.ExerciseSecondaryMuscleEntity;
import com.fitsync.workout.repository.ExerciseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ExerciseCatalogImportServiceImpl implements ExerciseCatalogImportService {

    static final String FREE_EXERCISE_DB = "free-exercise-db";

    private static final Logger LOGGER = LoggerFactory.getLogger(ExerciseCatalogImportServiceImpl.class);
    private static final String CATALOG_IMAGE_KEY_PREFIX = "exercises/";
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";

    private final ObjectReader catalogReader;
    private final ExerciseRepository exerciseRepository;
    private final ObjectProvider<MediaStorageService> mediaStorageServiceProvider;
    private final TransactionTemplate transactionTemplate;
    private final Resource catalogResource;
    private final Optional<Path> imageSourceDirectory;

    public ExerciseCatalogImportServiceImpl(
            ObjectMapper objectMapper,
            ExerciseRepository exerciseRepository,
            ObjectProvider<MediaStorageService> mediaStorageServiceProvider,
            PlatformTransactionManager transactionManager,
            @Value("${fitsync.workout.catalog.free-exercise-db.resource:classpath:catalog/free-exercise-db/exercises.json}")
            Resource catalogResource,
            @Value("${fitsync.workout.catalog.free-exercise-db.image-source-directory:}")
            String imageSourceDirectory) {
        this.catalogReader = objectMapper.readerFor(FreeExercise[].class);
        this.exerciseRepository = exerciseRepository;
        this.mediaStorageServiceProvider = mediaStorageServiceProvider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.catalogResource = catalogResource;
        this.imageSourceDirectory = normalizeImageSourceDirectory(imageSourceDirectory);
    }

    @Override
    public ExerciseCatalogImportResponse importFreeExerciseDbCatalog() {
        List<FreeExercise> sourceExercises = readCatalog();
        Map<FallbackKey, Integer> fallbackCounts = new LinkedHashMap<>();
        int created = 0;
        int updated = 0;
        int rejected = 0;

        for (FreeExercise source : sourceExercises) {
            try {
                boolean isNew = Boolean.TRUE.equals(transactionTemplate.execute(
                        ignored -> upsertSourceExercise(source, fallbackCounts)));
                if (isNew) {
                    created++;
                } else {
                    updated++;
                }
            } catch (RuntimeException ex) {
                rejected++;
                LOGGER.warn("Skipping free-exercise-db catalog row {} because it could not be imported",
                        sanitizeForLog(source == null ? "<null>" : source.id()));
                LOGGER.debug("free-exercise-db catalog row import failure", ex);
            }
        }

        List<ExerciseCatalogUnmappedTermView> unmappedTerms = fallbackCounts.entrySet().stream()
                .map(entry -> new ExerciseCatalogUnmappedTermView(
                        entry.getKey().field(),
                        entry.getKey().term(),
                        entry.getKey().mappedTo(),
                        entry.getValue()))
                .toList();
        if (!unmappedTerms.isEmpty()) {
            LOGGER.info("free-exercise-db catalog import completed with fallback mappings: {}",
                    sanitizeForLog(unmappedTerms.toString()));
        }
        return new ExerciseCatalogImportResponse(sourceExercises.size(), created, updated, rejected, unmappedTerms);
    }

    private boolean upsertSourceExercise(FreeExercise source, Map<FallbackKey, Integer> fallbackCounts) {
        ExerciseEntity entity = exerciseRepository
                .findByExternalSourceAndExternalRef(FREE_EXERCISE_DB, source.id())
                .orElseGet(() -> {
                    ExerciseEntity createdEntity = new ExerciseEntity();
                    createdEntity.setExerciseId(UUID.randomUUID());
                    return createdEntity;
                });
        boolean isNew = entity.getExternalRef() == null;
        applySourceExercise(entity, source, fallbackCounts);
        exerciseRepository.saveAndFlush(entity);
        return isNew;
    }

    private List<FreeExercise> readCatalog() {
        try (InputStream inputStream = catalogResource.getInputStream()) {
            FreeExercise[] exercises = catalogReader.readValue(inputStream);
            return List.of(exercises);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read free-exercise-db catalog", ex);
        }
    }

    private void applySourceExercise(ExerciseEntity entity,
                                     FreeExercise source,
                                     Map<FallbackKey, Integer> fallbackCounts) {
        entity.setOwnerUserId(null);
        entity.setCode(source.id().toLowerCase(Locale.ROOT));
        entity.setName(source.name());
        entity.setDescription(firstInstruction(source.instructions()));
        entity.setExerciseType(toExerciseType(source.category(), fallbackCounts));
        entity.setPrimaryMuscleGroup(toPrimaryMuscleGroup(source.primaryMuscles(), fallbackCounts));
        entity.setEquipment(toEquipment(source.equipment(), fallbackCounts));
        entity.setDifficulty(toDifficulty(source.level(), fallbackCounts));
        entity.setStatus(ExerciseStatus.ACTIVE);
        entity.setCustom(false);
        entity.setVideoUrl(null);
        entity.setAnimationUrl(null);
        entity.setExternalSource(FREE_EXERCISE_DB);
        entity.setExternalRef(source.id());
        replaceInstructions(entity, source.instructions());
        replaceImages(entity, source.id(), source.images());
        replaceSecondaryMuscles(entity, source.primaryMuscles(), source.secondaryMuscles(), fallbackCounts);
    }

    private String firstInstruction(List<String> instructions) {
        if (instructions == null || instructions.isEmpty()) {
            return null;
        }
        String instruction = instructions.get(0);
        if (instruction == null || instruction.isBlank()) {
            return null;
        }
        return instruction.length() > 1000 ? instruction.substring(0, 1000) : instruction;
    }

    private void replaceInstructions(ExerciseEntity entity, List<String> instructions) {
        if (instructions == null) {
            entity.getInstructions().clear();
            return;
        }
        Set<Integer> retainedSteps = new LinkedHashSet<>();
        for (int index = 0; index < instructions.size(); index++) {
            String text = instructions.get(index);
            if (text == null || text.isBlank()) {
                continue;
            }
            int stepNumber = index + 1;
            retainedSteps.add(stepNumber);
            ExerciseInstructionEntity instruction = entity.getInstructions().stream()
                    .filter(existing -> stepNumber == existing.getStepNumber())
                    .findFirst()
                    .orElseGet(() -> {
                        ExerciseInstructionEntity created = new ExerciseInstructionEntity();
                        created.setInstructionId(UUID.randomUUID());
                        created.setExercise(entity);
                        created.setStepNumber(stepNumber);
                        entity.getInstructions().add(created);
                        return created;
                    });
            instruction.setText(text.length() > 1000 ? text.substring(0, 1000) : text);
        }
        entity.getInstructions().removeIf(instruction -> !retainedSteps.contains(instruction.getStepNumber()));
    }

    private void replaceImages(ExerciseEntity entity, String externalRef, List<String> images) {
        if (images == null) {
            entity.getImages().clear();
            return;
        }
        if (imageSourceDirectory.isEmpty()) {
            if (!entity.getImages().isEmpty()) {
                LOGGER.debug("Skipping free-exercise-db image refresh for {} because no image source directory is configured",
                        sanitizeForLog(externalRef));
            }
            return;
        }
        Set<Integer> retainedPositions = new LinkedHashSet<>();
        for (int index = 0; index < images.size(); index++) {
            String image = images.get(index);
            if (image == null || image.isBlank()) {
                continue;
            }
            int position = index;
            Optional<byte[]> imageBytes = readImageBytes(image);
            if (imageBytes.isEmpty()) {
                LOGGER.warn("Skipping free-exercise-db image {} for {} because the source file is unavailable",
                        sanitizeForLog(image),
                        sanitizeForLog(externalRef));
                continue;
            }
            String imageUrl = mediaStorageServiceProvider.getObject()
                    .put(imageStorageKey(externalRef, position), imageBytes.get(), contentType(image));
            retainedPositions.add(position);
            ExerciseImageEntity imageEntity = entity.getImages().stream()
                    .filter(existing -> position == existing.getPosition())
                    .findFirst()
                    .orElseGet(() -> {
                        ExerciseImageEntity created = new ExerciseImageEntity();
                        created.setImageId(UUID.randomUUID());
                        created.setExercise(entity);
                        created.setPosition(position);
                        entity.getImages().add(created);
                        return created;
                    });
            imageEntity.setUrl(imageUrl);
        }
        entity.getImages().removeIf(image -> !retainedPositions.contains(image.getPosition()));
    }

    private Optional<Path> normalizeImageSourceDirectory(String configuredDirectory) {
        if (configuredDirectory == null || configuredDirectory.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Path.of(configuredDirectory).toAbsolutePath().normalize());
    }

    private Optional<byte[]> readImageBytes(String sourceImage) {
        Path baseDirectory = imageSourceDirectory.orElseThrow();
        Path imagePath = baseDirectory.resolve(sourceImage.replace('\\', '/')).normalize();
        if (!imagePath.startsWith(baseDirectory)) {
            throw new IllegalArgumentException("free-exercise-db image path escapes the configured source directory");
        }
        if (!Files.isRegularFile(imagePath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(imagePath));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read free-exercise-db image " + sourceImage, ex);
        }
    }

    private String imageStorageKey(String externalRef, int position) {
        return CATALOG_IMAGE_KEY_PREFIX + externalRef + "/" + position + ".jpg";
    }

    private String contentType(String sourceImage) {
        String normalized = normalize(sourceImage);
        if (normalized.endsWith(".png")) {
            return "image/png";
        }
        if (normalized.endsWith(".webp")) {
            return "image/webp";
        }
        return JPEG_CONTENT_TYPE;
    }

    private void replaceSecondaryMuscles(ExerciseEntity entity,
                                         List<String> primaryMuscles,
                                         List<String> secondaryMuscles,
                                         Map<FallbackKey, Integer> fallbackCounts) {
        LinkedHashSet<MuscleGroup> mapped = new LinkedHashSet<>();
        if (primaryMuscles != null && primaryMuscles.size() > 1) {
            primaryMuscles.subList(1, primaryMuscles.size()).forEach(
                    muscle -> mapped.add(toMuscleGroup("primaryMuscles", muscle, fallbackCounts)));
        }
        if (secondaryMuscles != null) {
            secondaryMuscles.forEach(muscle -> mapped.add(toMuscleGroup("secondaryMuscles", muscle, fallbackCounts)));
        }
        mapped.remove(entity.getPrimaryMuscleGroup());
        entity.getSecondaryMuscles().removeIf(secondaryMuscle -> !mapped.contains(secondaryMuscle.getMuscleGroup()));
        for (MuscleGroup muscleGroup : mapped) {
            boolean alreadyPresent = entity.getSecondaryMuscles().stream()
                    .anyMatch(existing -> muscleGroup == existing.getMuscleGroup());
            if (!alreadyPresent) {
                ExerciseSecondaryMuscleEntity secondaryMuscle = new ExerciseSecondaryMuscleEntity();
                secondaryMuscle.setSecondaryMuscleId(UUID.randomUUID());
                secondaryMuscle.setExercise(entity);
                secondaryMuscle.setMuscleGroup(muscleGroup);
                entity.getSecondaryMuscles().add(secondaryMuscle);
            }
        }
    }

    private WorkoutDifficulty toDifficulty(String level, Map<FallbackKey, Integer> fallbackCounts) {
        return switch (normalize(level)) {
            case "beginner" -> WorkoutDifficulty.BEGINNER;
            case "intermediate" -> WorkoutDifficulty.INTERMEDIATE;
            case "expert" -> WorkoutDifficulty.ADVANCED;
            default -> {
                recordFallback(fallbackCounts, "level", level, WorkoutDifficulty.INTERMEDIATE.name());
                yield WorkoutDifficulty.INTERMEDIATE;
            }
        };
    }

    private ExerciseType toExerciseType(String category, Map<FallbackKey, Integer> fallbackCounts) {
        return switch (normalize(category)) {
            case "strength", "powerlifting", "strongman", "olympic weightlifting" -> ExerciseType.STRENGTH;
            case "cardio" -> ExerciseType.CARDIO;
            case "stretching" -> ExerciseType.MOBILITY;
            case "plyometrics" -> {
                recordFallback(fallbackCounts, "category", category, ExerciseType.SPORT.name());
                yield ExerciseType.SPORT;
            }
            default -> {
                recordFallback(fallbackCounts, "category", category, ExerciseType.STRENGTH.name());
                yield ExerciseType.STRENGTH;
            }
        };
    }

    private ExerciseEquipment toEquipment(String equipment, Map<FallbackKey, Integer> fallbackCounts) {
        return switch (normalize(equipment)) {
            case "" -> ExerciseEquipment.NONE;
            case "body only" -> ExerciseEquipment.BODYWEIGHT;
            case "barbell", "e-z curl bar" -> ExerciseEquipment.BARBELL;
            case "dumbbell" -> ExerciseEquipment.DUMBBELL;
            case "kettlebells" -> ExerciseEquipment.KETTLEBELL;
            case "machine" -> ExerciseEquipment.MACHINE;
            case "cable" -> ExerciseEquipment.CABLE;
            case "bands" -> ExerciseEquipment.BAND;
            case "none" -> ExerciseEquipment.NONE;
            default -> {
                recordFallback(fallbackCounts, "equipment", equipment, ExerciseEquipment.OTHER.name());
                yield ExerciseEquipment.OTHER;
            }
        };
    }

    private MuscleGroup toPrimaryMuscleGroup(List<String> primaryMuscles, Map<FallbackKey, Integer> fallbackCounts) {
        if (primaryMuscles == null || primaryMuscles.isEmpty()) {
            recordFallback(fallbackCounts, "primaryMuscles", "<missing>", MuscleGroup.FULL_BODY.name());
            return MuscleGroup.FULL_BODY;
        }
        return toMuscleGroup("primaryMuscles", primaryMuscles.get(0), fallbackCounts);
    }

    private MuscleGroup toMuscleGroup(String field, String muscle, Map<FallbackKey, Integer> fallbackCounts) {
        return switch (normalize(muscle)) {
            case "chest" -> MuscleGroup.CHEST;
            case "abdominals" -> MuscleGroup.CORE;
            case "biceps", "triceps", "forearms" -> MuscleGroup.ARMS;
            case "lats", "lower back", "middle back", "traps" -> MuscleGroup.BACK;
            case "shoulders" -> MuscleGroup.SHOULDERS;
            case "glutes" -> MuscleGroup.GLUTES;
            case "quadriceps", "hamstrings", "calves", "abductors", "adductors" -> MuscleGroup.LEGS;
            default -> {
                recordFallback(fallbackCounts, field, muscle, MuscleGroup.FULL_BODY.name());
                yield MuscleGroup.FULL_BODY;
            }
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String sanitizeForLog(String value) {
        if (value == null) {
            return "<null>";
        }
        return value.replace('\r', '_').replace('\n', '_');
    }

    private void recordFallback(Map<FallbackKey, Integer> fallbackCounts,
                                String field,
                                String term,
                                String mappedTo) {
        fallbackCounts.merge(new FallbackKey(field, term == null || term.isBlank() ? "<blank>" : term, mappedTo), 1, Integer::sum);
    }

    private record FallbackKey(String field, String term, String mappedTo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FreeExercise(
            String id,
            String name,
            String level,
            String equipment,
            String category,
            List<String> primaryMuscles,
            List<String> secondaryMuscles,
            List<String> instructions,
            List<String> images) {
    }
}
