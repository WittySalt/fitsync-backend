package com.fitsync.nutrition.entity;

import java.util.UUID;

import com.fitsync.persistence.entity.BaseEffectiveEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "food_barcodes")
public class FoodBarcodeEntity extends BaseEffectiveEntity {

    @Id
    @Column(name = "barcode_id", nullable = false)
    private UUID barcodeId;

    @Column(name = "food_id", nullable = false)
    private UUID foodId;

    @Column(name = "barcode", nullable = false, length = 40, unique = true)
    private String barcode;

    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Column(name = "primary_barcode", nullable = false)
    private boolean primaryBarcode;

    public UUID getBarcodeId() {
        return barcodeId;
    }

    public void setBarcodeId(UUID barcodeId) {
        this.barcodeId = barcodeId;
    }

    public UUID getFoodId() {
        return foodId;
    }

    public void setFoodId(UUID foodId) {
        this.foodId = foodId;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public boolean isPrimaryBarcode() {
        return primaryBarcode;
    }

    public void setPrimaryBarcode(boolean primaryBarcode) {
        this.primaryBarcode = primaryBarcode;
    }
}
