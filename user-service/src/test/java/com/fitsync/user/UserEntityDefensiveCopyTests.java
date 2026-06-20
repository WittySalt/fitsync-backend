package com.fitsync.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import com.fitsync.user.domain.TrainingEquipment;
import com.fitsync.user.domain.TrainingFocusArea;
import com.fitsync.user.entity.UserHealthReadinessEntity;
import com.fitsync.user.entity.UserTrainingPreferencesEntity;
import org.junit.jupiter.api.Test;

class UserEntityDefensiveCopyTests {

    @Test
    void trainingPreferencesGetterReturnsDefensiveCopies() {
        UserTrainingPreferencesEntity entity = new UserTrainingPreferencesEntity();
        entity.setEquipmentInventory(new ArrayList<>(List.of(TrainingEquipment.DUMBBELLS)));
        entity.setFocusAreas(new ArrayList<>(List.of(TrainingFocusArea.CORE)));

        List<TrainingEquipment> equipmentInventory = entity.getEquipmentInventory();
        List<TrainingFocusArea> focusAreas = entity.getFocusAreas();

        assertThrows(UnsupportedOperationException.class,
                () -> equipmentInventory.add(TrainingEquipment.BANDS));
        assertThrows(UnsupportedOperationException.class,
                () -> focusAreas.add(TrainingFocusArea.MOBILITY));
        assertEquals(List.of(TrainingEquipment.DUMBBELLS), entity.getEquipmentInventory());
        assertEquals(List.of(TrainingFocusArea.CORE), entity.getFocusAreas());
    }

    @Test
    void healthReadinessGetterReturnsDefensiveCopy() {
        UserHealthReadinessEntity entity = new UserHealthReadinessEntity();
        entity.setInjuryAreas(new ArrayList<>(List.of("LEFT_KNEE")));

        List<String> injuryAreas = entity.getInjuryAreas();

        assertThrows(UnsupportedOperationException.class, () -> injuryAreas.add("LOWER_BACK"));
        assertEquals(List.of("LEFT_KNEE"), entity.getInjuryAreas());
    }
}
