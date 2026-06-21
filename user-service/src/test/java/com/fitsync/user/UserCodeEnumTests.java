package com.fitsync.user;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.user.domain.TrainingEquipment;
import com.fitsync.user.domain.UnitSystem;
import org.junit.jupiter.api.Test;

class UserCodeEnumTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesEnumCodeValues() throws Exception {
        assertEquals("\"100001\"", objectMapper.writeValueAsString(UnitSystem.METRIC));
        assertEquals("\"230002\"", objectMapper.writeValueAsString(TrainingEquipment.DUMBBELLS));
    }

    @Test
    void deserializesCodesAndLegacyAliases() throws Exception {
        assertEquals(UnitSystem.METRIC, objectMapper.readValue("\"100001\"", UnitSystem.class));
        assertEquals(UnitSystem.METRIC, objectMapper.readValue("\"metric\"", UnitSystem.class));
        assertEquals(TrainingEquipment.FULL_GYM, objectMapper.readValue("\"full_gym\"", TrainingEquipment.class));
    }

    @Test
    void exposesMetadataAndDefensiveLegacyCodes() {
        assertEquals("Metric", UnitSystem.METRIC.getDefaultDisplayName());
        assertEquals("Metric units", UnitSystem.METRIC.getDefaultDescription());

        String[] legacyCodes = UnitSystem.METRIC.getLegacyCodes();
        legacyCodes[0] = "changed";

        assertArrayEquals(new String[] { "metric" }, UnitSystem.METRIC.getLegacyCodes());
    }
}
