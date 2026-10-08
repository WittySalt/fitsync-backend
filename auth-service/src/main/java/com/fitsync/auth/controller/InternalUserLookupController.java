package com.fitsync.auth.controller;

import com.fitsync.auth.dto.CurrentUserResponse;
import com.fitsync.auth.dto.UserLookupRequest;
import com.fitsync.auth.entity.AppUser;
import com.fitsync.auth.exception.NotFoundException;
import com.fitsync.auth.repository.AppUserRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Looks a single account up by email for the admin console.
 *
 * <p>Kept separate from {@link InternalOutboxController} because it reads user data rather than
 * operating the outbox relay, and the two have nothing else in common.
 *
 * <p>The address travels in the body of a POST. In a query string it would be written into the
 * access log of every hop between the console and this service.
 */
@RestController
@RequestMapping("/api/v1/internal/users")
public class InternalUserLookupController {

    private static final Logger LOG = LoggerFactory.getLogger(InternalUserLookupController.class);

    private final AppUserRepository appUserRepository;

    public InternalUserLookupController(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @PostMapping("/lookup")
    public ResponseEntity<CurrentUserResponse> findByEmail(@Valid @RequestBody UserLookupRequest request) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(request.email()).orElse(null);
        // The address itself never reaches the log; only whether the lookup found an account.
        LOG.info("Admin user lookup by email performed, matched={}", user != null);

        if (user == null) {
            throw new NotFoundException("No user found for the given email");
        }

        return ResponseEntity.ok(new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.getAuthProvider().name()));
    }
}
