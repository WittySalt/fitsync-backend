package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fitsync.persistence.code.CodeEnum;

public enum TrainingEquipment implements UserCodeEnum {
    NONE("230001", "None", "No dedicated training equipment", "none"),
    DUMBBELLS("230002", "Dumbbells", "Dumbbells available", "dumbbells"),
    BARBELL("230003", "Barbell", "Barbell available", "barbell"),
    BANDS("230004", "Bands", "Resistance bands available", "bands"),
    PULL_UP_BAR("230005", "Pull-up bar", "Pull-up bar available", "pull_up_bar"),
    KETTLEBELL("230006", "Kettlebell", "Kettlebell available", "kettlebell"),
    MACHINES("230007", "Machines", "Selectorized or cable machines available", "machines"),
    FULL_GYM("230008", "Full gym", "Full commercial gym access", "full_gym");

    private final CodeEnumMetadata metadata;

    TrainingEquipment(String code, String displayName, String description, String... legacyCodes) {
        this.metadata = new CodeEnumMetadata(code, displayName, description, legacyCodes);
    }

    @Override
    public CodeEnumMetadata metadata() {
        return metadata;
    }

    @JsonCreator
    public static TrainingEquipment fromCode(String value) {
        return CodeEnum.fromCode(TrainingEquipment.class, value);
    }
}
