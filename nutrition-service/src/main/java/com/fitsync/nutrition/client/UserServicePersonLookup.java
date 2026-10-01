package com.fitsync.nutrition.client;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fitsync.nutrition.config.UserServiceProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Reads the profile, the latest body metrics and the preferences from user-service over HTTP, forwarding the
 * bearer token of the request being served. The data is the person's own and is asked for fresh each time,
 * the same way the coach does it, so nutrition-service keeps no second copy of someone's sex, age or weight.
 *
 * <p>Every failure, whether a timeout, an error status, a missing token or a body that is not what was
 * expected, is logged without the token and answered with "unknown".
 */
@Component
public class UserServicePersonLookup implements PersonLookup {

    private static final Logger LOG = LoggerFactory.getLogger(UserServicePersonLookup.class);

    private static final String PROFILE_PATH = "/api/v1/users/me/profile";
    private static final String BODY_METRICS_PATH = "/api/v1/users/me/body-metrics/latest";
    private static final String PREFERENCES_PATH = "/api/v1/users/me/preferences";
    private static final String MALE = "MALE";
    private static final String FEMALE = "FEMALE";
    // user-service answers with the numeric code of a dietary pattern. The names and the old lower-case codes are
    // accepted as well, so that a change in how it writes the value does not silently drop the focus nutrients.
    private static final Map<String, String> DIETARY_PATTERNS = Map.ofEntries(
            Map.entry("300001", "NONE"), Map.entry("300002", "HALAL"), Map.entry("300003", "VEGETARIAN"),
            Map.entry("300004", "VEGAN"), Map.entry("300005", "KETO"), Map.entry("300006", "LOW_CARB"),
            Map.entry("NONE", "NONE"), Map.entry("HALAL", "HALAL"), Map.entry("VEGETARIAN", "VEGETARIAN"),
            Map.entry("VEGAN", "VEGAN"), Map.entry("KETO", "KETO"), Map.entry("LOW_CARB", "LOW_CARB"),
            Map.entry("none", "NONE"), Map.entry("halal", "HALAL"), Map.entry("vegetarian", "VEGETARIAN"),
            Map.entry("vegan", "VEGAN"), Map.entry("keto", "KETO"), Map.entry("low_carb", "LOW_CARB"));

    private final boolean enabled;
    private final String baseUrl;
    private final RestClient restClient;
    private final Clock clock;
    // A reader is immutable, which a shared ObjectMapper is not.
    private final ObjectReader treeReader;

    public UserServicePersonLookup(UserServiceProperties properties, Clock clock) {
        this.enabled = properties.enabled();
        this.baseUrl = properties.baseUrl();
        this.clock = clock;
        this.treeReader = new ObjectMapper().reader();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        factory.setReadTimeout((int) properties.readTimeout().toMillis());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public PersonBasics basics() {
        JsonNode profile = read(PROFILE_PATH);
        if (profile == null) {
            return PersonBasics.unknown();
        }
        return new PersonBasics(sexOf(profile.path("gender")), ageOf(profile.path("birthDate")));
    }

    @Override
    public BigDecimal latestWeightKg() {
        JsonNode metrics = read(BODY_METRICS_PATH);
        if (metrics == null || !metrics.path("weightKg").isNumber()) {
            return null;
        }
        BigDecimal weight = metrics.path("weightKg").decimalValue();
        return weight.signum() > 0 ? weight : null;
    }

    @Override
    public String dietaryPattern() {
        JsonNode preferences = read(PREFERENCES_PATH);
        if (preferences == null) {
            return null;
        }
        JsonNode pattern = preferences.path("nutrition").path("dietaryPattern");
        if (!pattern.isTextual()) {
            return null;
        }
        // Only the patterns user-service defines are passed on, so no free text travels further.
        return DIETARY_PATTERNS.get(pattern.asText());
    }

    private String sexOf(JsonNode gender) {
        if (!gender.isTextual()) {
            return null;
        }
        String value = gender.asText();
        if (MALE.equals(value)) {
            return MALE;
        }
        return FEMALE.equals(value) ? FEMALE : null;
    }

    private Integer ageOf(JsonNode birthDate) {
        if (!birthDate.isTextual()) {
            return null;
        }
        try {
            LocalDate born = LocalDate.parse(birthDate.asText());
            int years = Period.between(born, LocalDate.now(clock)).getYears();
            return years >= 0 && years <= 130 ? years : null;
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /** One GET with the caller's token; null on any failure. */
    private JsonNode read(String path) {
        String authorization = currentAuthorization();
        if (!enabled || authorization == null) {
            return null;
        }
        try {
            String body = restClient.get()
                    .uri(URI.create(baseUrl + path))
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(String.class);
            return body == null || body.isBlank() ? null : treeReader.readTree(body);
        } catch (JsonProcessingException | RuntimeException ex) {
            // The path is a constant and the token is never logged.
            LOG.warn("event=person_lookup_unavailable path={} reason={}",
                    sanitizeForLog(path), sanitizeForLog(ex.getClass().getSimpleName()));
            return null;
        }
    }

    private String sanitizeForLog(String value) {
        return value.replace("\r", "_").replace("\n", "_");
    }

    private String currentAuthorization() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.startsWith("Bearer ") ? header : null;
    }
}
