package com.fitsync.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.fitsync.user.dto.ProgressInsightPoint;
import com.fitsync.user.dto.ProgressInsightResponse;
import com.fitsync.user.security.CurrentUserPrincipal;
import com.fitsync.user.security.JwtService;
import com.fitsync.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserController.class)
@Import(com.fitsync.web.config.CommonWebErrorConfiguration.class)
@ActiveProfiles("test")
class UserControllerProgressInsightTests {

    private static final String PATH = "/api/v1/users/me/insights/progress";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    // The application class enables JPA auditing, which a web slice cannot satisfy without a mapping context.
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void returnsTheProgressReadingWithTheAgreedFieldNames() throws Exception {
        ProgressInsightResponse response = new ProgressInsightResponse(
                "Asia/Singapore",
                LocalDate.parse("2026-09-30"),
                new BigDecimal("71.4"),
                LocalDate.parse("2026-09-29"),
                9,
                new BigDecimal("-0.42"),
                28,
                new BigDecimal("68.0"),
                LocalDate.parse("2026-12-09"),
                LocalDate.parse("2026-11-24"),
                List.of(new ProgressInsightPoint(LocalDate.parse("2026-08-05"), new BigDecimal("74.1"))));
        when(userService.getProgressInsight(any(), eq(ZoneId.of("Asia/Singapore")))).thenReturn(response);

        mockMvc.perform(get(PATH).param("timezone", "Asia/Singapore").with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Asia/Singapore"))
                .andExpect(jsonPath("$.referenceDate").value("2026-09-30"))
                .andExpect(jsonPath("$.weightKg").value(71.4))
                .andExpect(jsonPath("$.measuredOn").value("2026-09-29"))
                .andExpect(jsonPath("$.entries").value(9))
                .andExpect(jsonPath("$.weeklyRateKg").value(-0.42))
                .andExpect(jsonPath("$.rateWindowDays").value(28))
                .andExpect(jsonPath("$.goalWeightKg").value(68.0))
                .andExpect(jsonPath("$.goalDate").value("2026-12-09"))
                .andExpect(jsonPath("$.projectedDate").value("2026-11-24"))
                .andExpect(jsonPath("$.series[0].date").value("2026-08-05"))
                .andExpect(jsonPath("$.series[0].weightKg").value(74.1));
    }

    @Test
    void missingTimezoneDefaultsToUtc() throws Exception {
        when(userService.getProgressInsight(any(), eq(ZoneId.of("UTC")))).thenReturn(
                new ProgressInsightResponse("UTC", LocalDate.parse("2026-09-30"),
                        null, null, 0, null, null, null, null, null, List.of()));

        mockMvc.perform(get(PATH).with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("UTC"))
                .andExpect(jsonPath("$.weightKg").doesNotExist())
                .andExpect(jsonPath("$.series").isEmpty());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnInvalidTimezone() throws Exception {
        mockMvc.perform(get(PATH).param("timezone", "Mars/Olympus").with(authentication(authToken())))
                .andExpect(status().isBadRequest());

        verify(userService, never()).getProgressInsight(any(), any());
    }

    private UsernamePasswordAuthenticationToken authToken() {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                UUID.fromString("11111111-1111-1111-1111-111111111111"), "alice@example.com", "Alice", "USER");
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_" + principal.role()));
    }
}
