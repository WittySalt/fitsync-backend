package com.fitsync.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.AccountDeletedPayload;
import com.fitsync.media.MediaStorageService;
import com.fitsync.user.domain.MetricSource;
import com.fitsync.user.dto.BodyMetricView;
import com.fitsync.user.dto.CreateBodyMetricRequest;
import com.fitsync.user.messaging.AccountDeletedEventConsumer;
import com.fitsync.user.messaging.repository.OutboxEventRepository;
import com.fitsync.user.security.CurrentUserPrincipal;
import com.fitsync.user.service.UserService;
import com.fitsync.web.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Soft delete of body metrics and the erasure on AccountDeleted, against the real schema (H2). */
@SpringBootTest
@ActiveProfiles("test")
class BodyMetricSoftDeleteAndErasureTests {

    @Autowired
    private UserService userService;

    @Autowired
    private AccountDeletedEventConsumer accountDeletedEventConsumer;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private MediaStorageService mediaStorageService;

    @Test
    void aDeletedBodyMetricKeepsItsRowButLeavesEveryRead() {
        CurrentUserPrincipal person = person();
        BodyMetricView older = userService.addBodyMetrics(person, weighIn("2026-10-01T08:00:00Z", "70.0"));
        BodyMetricView newer = userService.addBodyMetrics(person, weighIn("2026-10-05T08:00:00Z", "69.0"));

        userService.deleteBodyMetric(person, newer.metricId());

        assertThat(userService.getLatestBodyMetrics(person).metricId()).isEqualTo(older.metricId());
        assertThat(userService.listBodyMetrics(person, null, null, 50))
                .extracting(BodyMetricView::metricId).containsExactly(older.metricId());
        assertThat(userService.getBodyMetricTrends(person,
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-31T00:00:00Z")).sampleCount()).isEqualTo(1);
        assertThatThrownBy(() -> userService.getBodyMetric(person, newer.metricId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> userService.deleteBodyMetric(person, newer.metricId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from body_metric_entries where metric_id = ? and deleted_at is not null",
                Integer.class, newer.metricId())).isEqualTo(1);
    }

    @Test
    void anotherPersonCannotDeleteABodyMetric() {
        CurrentUserPrincipal owner = person();
        BodyMetricView entry = userService.addBodyMetrics(owner, weighIn("2026-10-01T08:00:00Z", "70.0"));

        assertThatThrownBy(() -> userService.deleteBodyMetric(person(), entry.metricId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(userService.getBodyMetric(owner, entry.metricId()).metricId()).isEqualTo(entry.metricId());
    }

    @Test
    void accountDeletedErasesEveryRowIncludingSoftDeletedOnesTheAvatarsAndTheOutbox() {
        CurrentUserPrincipal person = person();
        CurrentUserPrincipal bystander = person();
        userService.getProfile(person);
        BodyMetricView removed = userService.addBodyMetrics(person, weighIn("2026-10-01T08:00:00Z", "70.0"));
        userService.addBodyMetrics(person, weighIn("2026-10-02T08:00:00Z", "69.5"));
        userService.deleteBodyMetric(person, removed.metricId());
        userService.addBodyMetrics(bystander, weighIn("2026-10-02T08:00:00Z", "80.0"));
        assertThat(rows("outbox_events", person.userId())).isPositive();

        accountDeletedEventConsumer.consume(accountDeleted(person.userId()));
        // A redelivered event finds nothing left and changes nothing.
        accountDeletedEventConsumer.consume(accountDeleted(person.userId()));

        assertThat(rows("body_metric_entries", person.userId())).isZero();
        assertThat(rows("user_profiles", person.userId())).isZero();
        assertThat(rows("outbox_events", person.userId())).isZero();
        assertThat(rows("body_metric_entries", bystander.userId())).isEqualTo(1);
        assertThat(rows("outbox_events", bystander.userId())).isPositive();
        verify(mediaStorageService, org.mockito.Mockito.times(2)).deleteAll("avatars/" + person.userId() + "/");
        assertThat(outboxEventRepository.findAll()).noneMatch(event -> person.userId().equals(event.getUserId()));
    }

    private int rows(String table, UUID userId) {
        String sql = switch (table) {
            case "body_metric_entries" -> "select count(*) from body_metric_entries where user_id = ?";
            case "user_profiles" -> "select count(*) from user_profiles where user_id = ?";
            case "outbox_events" -> "select count(*) from outbox_events where user_id = ?";
            default -> throw new IllegalArgumentException(table);
        };
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId);
        return count == null ? 0 : count;
    }

    private static FitSyncDomainEvent<AccountDeletedPayload> accountDeleted(UUID userId) {
        Instant now = Instant.now();
        return new FitSyncDomainEvent<>(UUID.randomUUID(), "AccountDeleted", 1, now, "auth-service", userId,
                new AccountDeletedPayload(now, "GRACE_PERIOD_ENDED"));
    }

    private static CurrentUserPrincipal person() {
        UUID userId = UUID.randomUUID();
        return new CurrentUserPrincipal(userId, userId + "@test.dev", "Person", "USER");
    }

    private static CreateBodyMetricRequest weighIn(String measuredAt, String weightKg) {
        return new CreateBodyMetricRequest(new BigDecimal(weightKg), null, null, null, null, null, null, null,
                Instant.parse(measuredAt), MetricSource.MANUAL, null);
    }
}
