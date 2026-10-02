package com.fitsync.nutrition.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import com.fitsync.nutrition.repository.NutrientRichestRepository.IngredientTextRow;
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
    private static final int MAX_NAME_LENGTH = 32;
    private static final Set<String> NOISE_QUALIFIERS = Set.of("raw", "cooked", "boiled", "fresh", "dried",
            "all varieties", "without salt", "with salt", "mature seeds", "year round average", "unprepared");
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

    /**
     * Up to six catalogue foods, one per name, by amount per 100 g. Foods that an imported recipe uses carry an
     * everyday name (taken from the recipe line), and only those are listed when at least six of them have the
     * nutrient. Otherwise the other foods fill the list under a shortened catalogue name. Callers pass a known code.
     */
    @Transactional(readOnly = true)
    public List<NutrientRichFoodView> richest(String code) {
        Instant now = clock.instant();
        CachedRichest cached = richestCache.get(code);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.foods();
        }
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        PageRequest page = PageRequest.of(0, RICHEST_CANDIDATES);
        List<RichFoodRow> recipeRows = richestRepository.findRichestInRecipes(code, today, page);
        Map<UUID, String> friendly = friendlyNames(recipeRows);
        List<NutrientRichFoodView> foods = pick(recipeRows, friendly);
        if (foods.size() < RICHEST_LIMIT) {
            List<RichFoodRow> rows = richestRepository.findRichest(code, today, page);
            Map<UUID, String> allFriendly = new HashMap<>(friendly);
            allFriendly.putAll(friendlyNames(rows));
            foods = pick(rows, allFriendly);
        }
        List<NutrientRichFoodView> result = List.copyOf(foods);
        if (!richestTtl.isZero() && !richestTtl.isNegative()) {
            richestCache.put(code, new CachedRichest(now.plus(richestTtl), result));
        }
        return result;
    }

    /** The first six rows with distinct names, a row with an everyday name under that name, else a short one. */
    private static List<NutrientRichFoodView> pick(List<RichFoodRow> rows, Map<UUID, String> friendly) {
        Set<String> seenNames = new HashSet<>();
        List<NutrientRichFoodView> foods = new ArrayList<>();
        for (RichFoodRow row : rows) {
            if (foods.size() >= RICHEST_LIMIT) {
                break;
            }
            String name = friendly.get(row.getFoodId());
            if (name == null) {
                name = shorten(row.getName());
            }
            if (seenNames.add(name.toLowerCase(Locale.ROOT))) {
                foods.add(new NutrientRichFoodView(
                        row.getFoodId(), name, row.getFoodGroup(), row.getCategoryCode(), row.getAmount()));
            }
        }
        return foods;
    }

    /** The most common everyday name of each food in the lines of imported recipes, shorter first on a tie. */
    private Map<UUID, String> friendlyNames(List<RichFoodRow> rows) {
        List<UUID> ids = rows.stream().map(RichFoodRow::getFoodId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<String, Integer>> counts = new HashMap<>();
        for (IngredientTextRow row : richestRepository.findIngredientTexts(ids)) {
            String name = RecipeStepAnalyzer.ingredientName(row.getText()).strip();
            if (!name.isEmpty() && name.length() <= MAX_NAME_LENGTH && name.matches("[\\p{L} '()-]+")) {
                counts.computeIfAbsent(row.getFoodId(), key -> new HashMap<>()).merge(name.toLowerCase(Locale.ROOT), 1, Integer::sum);
            }
        }
        Map<UUID, String> result = new HashMap<>();
        counts.forEach((id, byName) -> byName.entrySet().stream()
                .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(entry -> -entry.getKey().length())
                        .thenComparing(Map.Entry::getKey))
                .ifPresent(best -> result.put(id, capitalise(best.getKey()))));
        return result;
    }

    /**
     * A shorter name for a catalogue name such as "Mushrooms, brown, italian, or crimini, exposed to ultraviolet
     * light, raw": the part before the first comma, the first qualifier that says something, and "(UV)" when the
     * food was exposed to ultraviolet light, at most 32 characters.
     */
    static String shorten(String catalogueName) {
        if (catalogueName == null || catalogueName.isBlank()) {
            return "";
        }
        String[] parts = catalogueName.split(",");
        String head = parts[0].strip();
        String qualifier = null;
        boolean uv = false;
        for (int index = 1; index < parts.length; index++) {
            String part = parts[index].strip();
            String lower = part.toLowerCase(Locale.ROOT);
            if (lower.contains("ultraviolet")) {
                uv = true;
            } else if (qualifier == null && !part.isEmpty() && !NOISE_QUALIFIERS.contains(lower) && !lower.startsWith("or ")) {
                qualifier = part;
            }
        }
        String text = withNote(head, qualifier == null ? null : qualifier.toLowerCase(Locale.ROOT), uv);
        if (text.length() > MAX_NAME_LENGTH) {
            text = withNote(head, null, uv);
        }
        if (text.length() > MAX_NAME_LENGTH) {
            int cut = text.lastIndexOf(' ', MAX_NAME_LENGTH);
            text = text.substring(0, cut > 0 ? cut : MAX_NAME_LENGTH).strip();
        }
        return capitalise(text);
    }

    private static String withNote(String head, String qualifier, boolean uv) {
        if (qualifier == null && !uv) {
            return head;
        }
        String note = qualifier == null ? "UV" : uv ? qualifier + ", UV" : qualifier;
        return head + " (" + note + ")";
    }

    private static String capitalise(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    /**
     * One row per distinct age band of the target (or limit) rows, with the female, male and upper amounts, and the
     * caller's band marked. An upper limit row can cover several bands (19 and over) or be stored per band, so the
     * one that applies to a band is the row whose age range holds the band's first age.
     */
    @Transactional(readOnly = true)
    public List<NutrientAmountRowView> amounts(String code, PersonBasics basics) {
        int age = basics == null || basics.age() == null
                ? DEFAULT_ADULT_AGE
                : Math.min(Math.max(basics.age(), MIN_ADULT_AGE), OPEN_ENDED_AGE);
        List<NutrientDailyReferenceEntity> rows = referenceRepository.findByNutrientCodeOrderByAgeFromAscSexAsc(code);
        Map<String, List<NutrientDailyReferenceEntity>> bands = new LinkedHashMap<>();
        for (NutrientDailyReferenceEntity row : rows) {
            if (isTargetOrLimit(row)) {
                bands.computeIfAbsent(row.getAgeFrom() + "-" + row.getAgeTo(), ignored -> new ArrayList<>()).add(row);
            }
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
                    upperFor(rows, first.getAgeFrom()),
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

    /** The upper limit row that covers the age, the ANY row when the sexes differ. */
    private static BigDecimal upperFor(List<NutrientDailyReferenceEntity> rows, int age) {
        BigDecimal found = null;
        for (NutrientDailyReferenceEntity row : rows) {
            if ("UPPER".equals(row.getKind()) && row.getAgeFrom() <= age && age <= row.getAgeTo()
                    && (found == null || ANY.equals(row.getSex()))) {
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
