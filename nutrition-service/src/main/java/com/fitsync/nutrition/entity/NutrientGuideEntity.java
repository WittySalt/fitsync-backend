package com.fitsync.nutrition.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The plain-English guide of one vitamin or mineral. tips holds a JSON array of short strings. */
@Entity
@Table(name = "nutrient_guides")
public class NutrientGuideEntity {

    @Id
    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "what_it_does", nullable = false)
    private String whatItDoes;

    @Column(name = "too_little_too_much", nullable = false)
    private String tooLittleTooMuch;

    @Column(name = "who_needs_more", nullable = false)
    private String whoNeedsMore;

    @Column(name = "tips", nullable = false)
    private String tips;

    @Column(name = "source_label", nullable = false, length = 120)
    private String sourceLabel;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getWhatItDoes() {
        return whatItDoes;
    }

    public void setWhatItDoes(String whatItDoes) {
        this.whatItDoes = whatItDoes;
    }

    public String getTooLittleTooMuch() {
        return tooLittleTooMuch;
    }

    public void setTooLittleTooMuch(String tooLittleTooMuch) {
        this.tooLittleTooMuch = tooLittleTooMuch;
    }

    public String getWhoNeedsMore() {
        return whoNeedsMore;
    }

    public void setWhoNeedsMore(String whoNeedsMore) {
        this.whoNeedsMore = whoNeedsMore;
    }

    public String getTips() {
        return tips;
    }

    public void setTips(String tips) {
        this.tips = tips;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
