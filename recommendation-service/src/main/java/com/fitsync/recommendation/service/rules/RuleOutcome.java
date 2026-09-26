package com.fitsync.recommendation.service.rules;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The structured result of one rule: the recommendation type plus the computed facts
 * (numbers and status labels only — phrasing is the template/AI tier's job, per the
 * compute-in-code / model-phrases split in README §3). Fact iteration order is
 * preserved so serialized payloads stay stable and readable.
 */
public record RuleOutcome(String type, Map<String, Object> facts) {

    public RuleOutcome {
        facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts));
    }
}
