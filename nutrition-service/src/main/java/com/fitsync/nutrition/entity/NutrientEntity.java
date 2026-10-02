package com.fitsync.nutrition.entity;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One tracked nutrient: its code, unit, the USDA number the seed maps on, whether the Diary shows it, and where the all-nutrients table places it (group, symbol, atomic number, order). */
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

    @Column(name = "group_code", length = 20)
    private String groupCode;

    @Column(name = "symbol", length = 4)
    private String symbol;

    @Column(name = "atomic_number")
    private Integer atomicNumber;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

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

    public String getGroupCode() {
        return groupCode;
    }

    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Integer getAtomicNumber() {
        return atomicNumber;
    }

    public void setAtomicNumber(Integer atomicNumber) {
        this.atomicNumber = atomicNumber;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
