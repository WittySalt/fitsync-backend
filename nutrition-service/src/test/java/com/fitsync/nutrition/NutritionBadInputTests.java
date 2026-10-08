package com.fitsync.nutrition;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fitsync.nutrition.dto.CopyDayRequest;
import com.fitsync.nutrition.security.CurrentUserPrincipal;
import com.fitsync.nutrition.service.NutritionService;
import com.fitsync.web.error.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A meal range that is half given or backwards, and a timezone that is not a zone, are the client's mistake: the
 * service says so with the same exception as its other checks, and the client gets a 400 with the BAD_REQUEST code.
 */
class NutritionBadInputTests extends DiaryApiTestBase {

    @Autowired
    private NutritionService nutritionService;

    private final CurrentUserPrincipal principal =
            new CurrentUserPrincipal(UUID.randomUUID(), "bad-input@example.com", "Bad Input", "USER");

    @Test
    void aHalfGivenOrBackwardsMealRangeIsABadRequest() throws Exception {
        String token = tokenFor("bad-range@example.com");

        mockMvc.perform(get(BASE + "/meals").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-16T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("from and to must be provided together"));
        mockMvc.perform(get(BASE + "/meals").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-17T00:00:00Z")
                        .param("to", "2026-06-16T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("to must not be before from"));

        assertThatThrownBy(() -> nutritionService.listMeals(principal, Instant.parse("2026-06-16T00:00:00Z"), null, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void anUnknownTimezoneIsABadRequest() {
        assertThatThrownBy(() -> nutritionService.copyDay(principal, LocalDate.parse("2026-06-11"),
                new CopyDayRequest(LocalDate.parse("2026-06-12")), "Mars/Olympus"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid timezone");
    }
}
