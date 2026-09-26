package com.fitsync.recommendation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.recommendation.entity.RecommendationContextEntity;
import com.fitsync.recommendation.entity.RecommendationResultEntity;
import com.fitsync.recommendation.entity.RecommendationSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecommendationRepositoryTests {

    @Autowired
    private RecommendationContextRepository contextRepository;

    @Autowired
    private RecommendationResultRepository resultRepository;

    @Test
    void persistsAndReadsContextWithAuditingAndStaleDefault() {
        UUID userId = UUID.randomUUID();
        RecommendationContextEntity context = new RecommendationContextEntity();
        context.setUserId(userId);
        context.setCurrentGoalType("WEIGHT_LOSS");
        context.setEnergyTargetCalories(2100);

        contextRepository.saveAndFlush(context);

        RecommendationContextEntity loaded = contextRepository.findById(userId).orElseThrow();
        assertThat(loaded.getEnergyTargetCalories()).isEqualTo(2100);
        assertThat(loaded.isStale()).isTrue();
        assertThat(loaded.getCreatedBy()).isEqualTo("system");
        assertThat(loaded.getVersion()).isNotNull();
    }

    @Test
    void enforcesOneResultPerUserAndType() {
        UUID userId = UUID.randomUUID();
        RecommendationResultEntity result = new RecommendationResultEntity();
        result.setId(UUID.randomUUID());
        result.setUserId(userId);
        result.setType("NUTRITION_GAP");
        result.setSource(RecommendationSource.RULE);
        result.setPayload("{\"remainingKcal\":540}");
        result.setGeneratedAt(Instant.now());

        resultRepository.saveAndFlush(result);

        RecommendationResultEntity loaded =
                resultRepository.findByUserIdAndType(userId, "NUTRITION_GAP").orElseThrow();
        assertThat(loaded.getSource()).isEqualTo(RecommendationSource.RULE);
        assertThat(loaded.getPayload()).contains("remainingKcal");
        assertThat(resultRepository.findByUserId(userId)).hasSize(1);
    }
}
