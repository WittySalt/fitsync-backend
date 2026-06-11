package com.fitsync.user.entity.code;

import com.fitsync.persistence.code.BaseCodeValueEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "code_values")
public class CodeValueEntity extends BaseCodeValueEntity {
}
