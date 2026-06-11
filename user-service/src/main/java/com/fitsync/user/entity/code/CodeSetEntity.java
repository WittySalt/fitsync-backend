package com.fitsync.user.entity.code;

import com.fitsync.persistence.code.BaseCodeSetEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "code_sets")
public class CodeSetEntity extends BaseCodeSetEntity {
}
