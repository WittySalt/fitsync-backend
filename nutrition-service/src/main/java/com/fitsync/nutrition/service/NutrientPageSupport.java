package com.fitsync.nutrition.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fitsync.nutrition.client.PersonBasics;
import com.fitsync.nutrition.dto.NutrientAmountRowView;
import com.fitsync.nutrition.dto.NutrientGuideView;
import com.fitsync.nutrition.dto.NutrientRichFoodView;
import com.fitsync.nutrition.entity.NutrientDailyReferenceEntity;
import com.fitsync.nutrition.repository.NutrientDailyReferenceRepository;
import com.fitsync.nutrition.repository.NutrientGuideRepository;
import com.fitsync.nutrition.repository.NutrientRichestRepository;
import com.fitsync.nutrition.repository.NutrientRichestRepository.RichFoodRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The parts of a nutrient's page that do not come from the caller's meals: the amounts of every age band, the
 * richest catalogue foods and the guide. The richest foods are the same for every user, so they are kept in memory
 * per nutrient code for a while (this service has no Redis).
 */
@Component
public class NutrientPageSupport {

    static final int RICHEST_LIMIT = 6;
    // Several catalogue foods share a display name, so more rows than the limit are read before the names are merged.
    private static final int RICHEST_CANDIDATES = 200;
    private static final int DEFAULT_ADULT_AGE = 35;
    private static final int MIN_ADULT_AGE = 19;
    private static final int OPEN_ENDED_AGE = 120;
    private static final String ANY = "ANY";
    private static final String FEMALE = "FEMALE";
    private static final String MALE = "MALE";
    private static final ObjectReader TIPS_READER = new ObjectMapper().readerFor(String[].class);

    private final NutrientRichestRepository richestRepository;
    private final NutrientDailyReferenceRepository referenceRepository;
    private final NutrientGuideRepository guideRepository;
    private final Clock clock;
    private final Duration richestTtl;
    private final Map<String, CachedRichest> richestCache = new ConcurrentHashMap<>();

    public NutrientPageSupport(NutrientRichestRepository richestRepository,
                               NutrientDailyReferenceRepository referenceRepository,
                               NutrientGuideRepository guideRepository,
                               Clock clock,
                               @Value("${fitsync.nutrition.richest-cache-ttl:PT10M}") Duration richestTtl) {
        this.richestRepository = richestRepository;
        this.referenceRepository = referenceRepository;
        this.guideRepository = guideRepository;
        this.clock = clock;
        this.richestTtl = richestTtl;
    }

    /** Up to six catalogue foods, one per display name, by amount per 100 g. Callers pass a known nutrient code. */
    @Transactional(readOnly = true)
    public List<NutrientRichFoodView> richest(String code) {
        Instant now = clock.instant();
        CachedRichest cached = richestCache.get(code);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.foods();
        }
        List<RichFoodRow> rows = richestRepository.findRichest(
                code, LocalDate.ofInstant(now, ZoneOffset.UTC), PageRequest.of(0, RICHEST_CANDIDATES));
        Set<String> seenNames = new HashSet<>();
        List<NutrientRichFoodView> foods = new ArrayList<>();
        for (RichFoodRow row : rows) {
            if (foods.size() >= RICHEST_LIMIT) {
                break;
            }
            String nameKey = row.getName() == null ? "" : row.getName().trim().toLowerCase(Locale.ROOT);
            if (seenNames.add(nameKey)) {
                foods.add(new NutrientRichFoodView(
                        row.getFoodId(), row.getName(), row.getFoodGroup(), row.getCategoryCode(), row.getAmount()));
            }
        }
        List<NutrientRichFoodView> result = List.copyOf(foods);
        if (!richestTtl.isZero() && !richestTtl.isNegative()) {
            richestCache.put(code, new CachedRichest(now.plus(richestTtl), result));
        }
        return result;
    }

    /** Every age band of the reference table with the female, male and upper amounts, and the caller's band marked. */
    @Transactional(readOnly = true)
    public List<NutrientAmountRowView> amounts(String code, PersonBasics basics) {
        int age = basics == null || basics.age() == null
                ? DEFAULT_ADULT_AGE
                : Math.min(Math.max(basics.age(), MIN_ADULT_AGE), OPEN_ENDED_AGE);
        Map<Integer, List<NutrientDailyReferenceEntity>> bands = new LinkedHashMap<>();
        for (NutrientDailyReferenceEntity row : referenceRepository.findByNutrientCodeOrderByAgeFromAscSexAsc(code)) {
            bands.computeIfAbsent(row.getAgeFrom(), ignored -> new ArrayList<>()).add(row);
        }
        List<NutrientAmountRowView> result = new ArrayList<>();
        for (List<NutrientDailyReferenceEntity> band : bands.values()) {
            NutrientDailyReferenceEntity first = band.get(0);
            boolean openEnded = first.getAgeTo() >= OPEN_ENDED_AGE;
            result.add(new NutrientAmountRowView(
                    openEnded ? first.getAgeFrom() + "+" : first.getAgeFrom() + "-" + first.getAgeTo(),
                    openEnded ? first.getAgeFrom() + " and over" : first.getAgeFrom() + " to " + first.getAgeTo(),
                    amountFor(band, FEMALE),
                    amountFor(band, MALE),
                    upperOf(band),
                    first.getAgeFrom() <= age && age <= first.getAgeTo()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public NutrientGuideView guide(String code) {
        return guideRepository.findById(code)
                .map(guide -> new NutrientGuideView(
                        guide.getWhatItDoes(), guide.getTooLittleTooMuch(), guide.getWhoNeedsMore(),
                        parseTips(guide.getTips()), guide.getSourceLabel()))
                .orElse(null);
    }

    private static List<String> parseTips(String json) {
        try {
            return List.of(TIPS_READER.<String[]>readValue(json));
        } catch (IOException ex) {
            return List.of();
        }
    }

    /** The target (or limit) of the sex, else the ANY row for the band. */
    private static BigDecimal amountFor(List<NutrientDailyReferenceEntity> band, String sex) {
        BigDecimal any = null;
        for (NutrientDailyReferenceEntity row : band) {
            if (!isTargetOrLimit(row)) {
                continue;
            }
            if (sex.equals(row.getSex())) {
                return row.getAmount();
            }
            if (ANY.equals(row.getSex())) {
                any = row.getAmount();
            }
        }
        return any;
    }

    private static BigDecimal upperOf(List<NutrientDailyReferenceEntity> band) {
        BigDecimal found = null;
        for (NutrientDailyReferenceEntity row : band) {
            if ("UPPER".equals(row.getKind()) && (found == null || ANY.equals(row.getSex()))) {
                found = row.getAmount();
            }
        }
        return found;
    }

    private static boolean isTargetOrLimit(NutrientDailyReferenceEntity row) {
        return "TARGET".equals(row.getKind()) || "LIMIT".equals(row.getKind());
    }

    private record CachedRichest(Instant expiresAt, List<NutrientRichFoodView> foods) {
    }
}
