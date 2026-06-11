package com.fitsync.persistence.code;

import java.util.UUID;

import com.fitsync.persistence.entity.BaseEffectiveEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class BaseCodeValueEntity extends BaseEffectiveEntity {

    @Id
    @Column(name = "code_value_id", nullable = false)
    private UUID codeValueId;

    @Column(name = "code_set_key", nullable = false, length = 80)
    private String codeSetKey;

    @Column(name = "enum_name", nullable = false, length = 80)
    private String enumName;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    public UUID getCodeValueId() {
        return codeValueId;
    }

    public void setCodeValueId(UUID codeValueId) {
        this.codeValueId = codeValueId;
    }

    public String getCodeSetKey() {
        return codeSetKey;
    }

    public void setCodeSetKey(String codeSetKey) {
        this.codeSetKey = codeSetKey;
    }

    public String getEnumName() {
        return enumName;
    }

    public void setEnumName(String enumName) {
        this.enumName = enumName;
    }

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }
}
