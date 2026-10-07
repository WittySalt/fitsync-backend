package com.fitsync.user.controller;

import com.fitsync.user.dto.UpsertUserGuideStateRequest;
import com.fitsync.user.dto.UserGuideStateView;
import com.fitsync.user.security.CurrentUserPrincipal;
import com.fitsync.user.service.UserGuideStateService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserGuideController {

    private final UserGuideStateService guideStateService;

    public UserGuideController(UserGuideStateService guideStateService) {
        this.guideStateService = guideStateService;
    }

    @GetMapping("/me/guide")
    public UserGuideStateView guide(Authentication authentication) {
        return guideStateService.get(currentPrincipal(authentication).userId());
    }

    @PutMapping("/me/guide")
    public UserGuideStateView saveGuide(Authentication authentication,
                                        @RequestBody UpsertUserGuideStateRequest request) {
        return guideStateService.upsert(currentPrincipal(authentication).userId(), request.state());
    }

    private CurrentUserPrincipal currentPrincipal(Authentication authentication) {
        return (CurrentUserPrincipal) authentication.getPrincipal();
    }
}
