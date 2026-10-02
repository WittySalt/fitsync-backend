package com.fitsync.nutrition.entity;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One tracked nutrient: its code, unit, the USDA number the seed maps on, and whether the Diary shows it. */
@Entity
@Table(name = "nutrients")
public class NutrientEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "usda_nutrient_number")
    private Integer usdaNutrientNumber;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "shown_in_diary", nullable = false)
    private boolean shownInDiary;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public Integer getUsdaNutrientNumber() {
        return usdaNutrientNumber;
    }

    public void setUsdaNutrientNumber(Integer usdaNutrientNumber) {
        this.usdaNutrientNumber = usdaNutrientNumber;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isShownInDiary() {
        return shownInDiary;
    }

    public void setShownInDiary(boolean shownInDiary) {
        this.shownInDiary = shownInDiary;
    }
}
