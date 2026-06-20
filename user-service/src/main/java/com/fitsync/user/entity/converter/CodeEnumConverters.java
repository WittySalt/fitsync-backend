package com.fitsync.user.entity.converter;

import com.fitsync.persistence.code.CodeEnumAttributeConverter;
import com.fitsync.persistence.code.CodeEnum;
import com.fitsync.user.domain.CalorieTrackingMode;
import com.fitsync.user.domain.DietaryPreference;
import com.fitsync.user.domain.FitnessLevel;
import com.fitsync.user.domain.IntensityPreference;
import com.fitsync.user.domain.LanguagePreference;
import com.fitsync.user.domain.MacroPreference;
import com.fitsync.user.domain.PrivacyLevel;
import com.fitsync.user.domain.TrainingEquipment;
import com.fitsync.user.domain.TrainingFocusArea;
import com.fitsync.user.domain.TrainingLocation;
import com.fitsync.user.domain.UnitSystem;
import jakarta.persistence.Converter;

public final class CodeEnumConverters {

    private CodeEnumConverters() {
    }

    abstract static class BaseConverter<E extends Enum<E> & CodeEnum>
            extends CodeEnumAttributeConverter<E> {
        BaseConverter(Class<E> enumType) {
            super(enumType);
        }
    }

    @Converter(autoApply = true)
    public static class UnitSystemConverter extends BaseConverter<UnitSystem> {
        public UnitSystemConverter() { super(UnitSystem.class); }
    }

    @Converter(autoApply = true)
    public static class DietaryPreferenceConverter extends BaseConverter<DietaryPreference> {
        public DietaryPreferenceConverter() { super(DietaryPreference.class); }
    }

    @Converter(autoApply = true)
    public static class LanguagePreferenceConverter extends BaseConverter<LanguagePreference> {
        public LanguagePreferenceConverter() { super(LanguagePreference.class); }
    }

    @Converter(autoApply = true)
    public static class PrivacyLevelConverter extends BaseConverter<PrivacyLevel> {
        public PrivacyLevelConverter() { super(PrivacyLevel.class); }
    }

    @Converter(autoApply = true)
    public static class FitnessLevelConverter extends BaseConverter<FitnessLevel> {
        public FitnessLevelConverter() { super(FitnessLevel.class); }
    }

    @Converter(autoApply = true)
    public static class TrainingLocationConverter extends BaseConverter<TrainingLocation> {
        public TrainingLocationConverter() { super(TrainingLocation.class); }
    }

    @Converter(autoApply = true)
    public static class IntensityPreferenceConverter extends BaseConverter<IntensityPreference> {
        public IntensityPreferenceConverter() { super(IntensityPreference.class); }
    }

    @Converter(autoApply = true)
    public static class TrainingEquipmentConverter extends BaseConverter<TrainingEquipment> {
        public TrainingEquipmentConverter() { super(TrainingEquipment.class); }
    }

    @Converter(autoApply = true)
    public static class TrainingFocusAreaConverter extends BaseConverter<TrainingFocusArea> {
        public TrainingFocusAreaConverter() { super(TrainingFocusArea.class); }
    }

    @Converter(autoApply = true)
    public static class CalorieTrackingModeConverter extends BaseConverter<CalorieTrackingMode> {
        public CalorieTrackingModeConverter() { super(CalorieTrackingMode.class); }
    }

    @Converter(autoApply = true)
    public static class MacroPreferenceConverter extends BaseConverter<MacroPreference> {
        public MacroPreferenceConverter() { super(MacroPreference.class); }
    }
}
