package com.fitsync.nutrition.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A daily amount for one nutrient, for one sex and age band. The kind says what the amount is: a
 * TARGET to reach, a LIMIT not to pass, or the tolerable UPPER intake.
 */
@Entity
@Table(name = "nutrient_daily_references")
public class NutrientDailyReferenceEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Column(name = "nutrient_code", nullable = false, length = 40)
    private String nutrientCode;

    @Column(name = "sex", nullable = false, length = 10)
    private String sex;

    @Column(name = "age_from", nullable = false)
    private int ageFrom;

    @Column(name = "age_to", nullable = false)
    private int ageTo;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    @Column(name = "source", nullable = false, length = 120)
    private String source;

    public UUID getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(UUID referenceId) {
        this.referenceId = referenceId;
    }

    public String getNutrientCode() {
        return nutrientCode;
    }

    public void setNutrientCode(String nutrientCode) {
        this.nutrientCode = nutrientCode;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public int getAgeFrom() {
        return ageFrom;
    }

    public void setAgeFrom(int ageFrom) {
        this.ageFrom = ageFrom;
    }

    public int getAgeTo() {
        return ageTo;
    }

    public void setAgeTo(int ageTo) {
        this.ageTo = ageTo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
