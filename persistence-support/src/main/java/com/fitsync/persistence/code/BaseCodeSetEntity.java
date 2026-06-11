package com.fitsync.persistence.code;

import java.util.UUID;

import com.fitsync.persistence.entity.BaseEffectiveEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class BaseCodeSetEntity extends BaseEffectiveEntity {

    @Id
    @Column(name = "code_set_id", nullable = false)
    private UUID codeSetId;

    @Column(name = "code_set_key", nullable = false, length = 80)
    private String codeSetKey;

    @Column(name = "enum_class_name", nullable = false, length = 180)
    private String enumClassName;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "description", length = 500)
    private String description;

    public UUID getCodeSetId() {
        return codeSetId;
    }

    public void setCodeSetId(UUID codeSetId) {
        this.codeSetId = codeSetId;
    }

    public String getCodeSetKey() {
        return codeSetKey;
    }

    public void setCodeSetKey(String codeSetKey) {
        this.codeSetKey = codeSetKey;
    }

    public String getEnumClassName() {
        return enumClassName;
    }

    public void setEnumClassName(String enumClassName) {
        this.enumClassName = enumClassName;
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
}
