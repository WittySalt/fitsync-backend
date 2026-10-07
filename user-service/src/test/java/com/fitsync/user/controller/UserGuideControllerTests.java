package com.fitsync.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.user.entity.UserGuideStateEntity;
import com.fitsync.user.repository.UserGuideStateRepository;
import com.fitsync.user.security.CurrentUserPrincipal;
import com.fitsync.user.security.JwtService;
import com.fitsync.user.service.UserGuideStateService;
import com.fitsync.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserGuideController.class)
@Import({com.fitsync.web.config.CommonWebErrorConfiguration.class, UserGuideStateService.class})
@ActiveProfiles("test")
class UserGuideControllerTests {

    private static final String PATH = "/api/v1/users/me/guide";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserGuideStateRepository repository;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    // The application class enables JPA auditing, which a web slice cannot satisfy without a mapping context.
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void getWithoutARowReturnsNullState() throws Exception {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get(PATH).with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value((Object) null))
                .andExpect(jsonPath("$.updatedAt").value((Object) null));
    }

    @Test
    void putStoresTheObjectAndGetReturnsIt() throws Exception {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());
        when(repository.save(any(UserGuideStateEntity.class))).thenAnswer(call -> call.getArgument(0));

        mockMvc.perform(put(PATH).with(csrf()).with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":{\"tourMap\":{\"workout\":\"done\"},\"counters\":{\"library\":2}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.tourMap.workout").value("done"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        when(repository.findById(USER_ID)).thenReturn(Optional.of(new UserGuideStateEntity(
                USER_ID, "{\"tourMap\":{\"workout\":\"done\"}}", Instant.parse("2026-10-07T01:00:00Z"))));

        mockMvc.perform(get(PATH).with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.tourMap.workout").value("done"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-07T01:00:00Z"));
    }

    @Test
    void putRejectsAnArrayState() throws Exception {
        mockMvc.perform(put(PATH).with(csrf()).with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":[1,2]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putRejectsAStringState() throws Exception {
        mockMvc.perform(put(PATH).with(csrf()).with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"hello\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putRejectsAMissingState() throws Exception {
        mockMvc.perform(put(PATH).with(csrf()).with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putRejectsAStateOverEightKilobytes() throws Exception {
        String big = "x".repeat(8 * 1024 + 1);

        mockMvc.perform(put(PATH).with(csrf()).with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":{\"note\":\"" + big + "\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestsWithoutAuthenticationAreRejected() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"state\":{}}"))
                .andExpect(status().isUnauthorized());
    }

    private UsernamePasswordAuthenticationToken authToken() {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(USER_ID, "alice@example.com", "Alice", "USER");
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_" + principal.role()));
    }
}
