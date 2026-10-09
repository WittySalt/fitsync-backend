package com.fitsync.user.controller;

import com.fitsync.user.dto.CoachHealthContextView;
import com.fitsync.user.security.CurrentUserPrincipal;
import com.fitsync.user.service.CoachHealthContextService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The consent-gated view of the readiness questionnaire that recommendation-service reads with the user's own
 * token when it drafts a plan. The full questionnaire stays at {@code /me/health-readiness} for the user's own
 * screens, which must keep showing the answers whether or not the coach may use them.
 */
@RestController
@RequestMapping("/api/v1/users")
public class CoachHealthContextController {

    private final CoachHealthContextService coachHealthContextService;

    public CoachHealthContextController(CoachHealthContextService coachHealthContextService) {
        this.coachHealthContextService = coachHealthContextService;
    }

    @GetMapping("/me/health-readiness/coach-context")
    public CoachHealthContextView coachContext(Authentication authentication) {
        return coachHealthContextService.forCoach(((CurrentUserPrincipal) authentication.getPrincipal()).userId());
    }
}
