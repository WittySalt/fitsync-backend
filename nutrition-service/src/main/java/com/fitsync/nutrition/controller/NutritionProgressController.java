package com.fitsync.nutrition.controller;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import com.fitsync.nutrition.dto.NutritionProgressFoodView;
import com.fitsync.nutrition.security.CurrentUserPrincipal;
import com.fitsync.nutrition.service.NutritionProgressService;
import com.fitsync.web.timezone.RequestTimezone;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The nutrition half of the Progress Food page (progress-redesign contract section 3.1). The caller comes from
 * the token only; the zone is the X-Timezone header, else UTC.
 */
@RestController
@RequestMapping("/api/v1/nutrition/progress")
public class NutritionProgressController {

    private final NutritionProgressService nutritionProgressService;

    public NutritionProgressController(NutritionProgressService nutritionProgressService) {
        this.nutritionProgressService = nutritionProgressService;
    }

    @GetMapping("/food")
    public NutritionProgressFoodView food(Authentication authentication,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestTimezone Optional<ZoneId> zone) {
        return nutritionProgressService.getFood((CurrentUserPrincipal) authentication.getPrincipal(), from, to,
                zone.map(ZoneId::getId).orElse(null));
    }
}
