package com.fitsync.user.repository.code;

import java.util.Optional;
import java.util.UUID;

import com.fitsync.user.entity.code.CodeSetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeSetRepository extends JpaRepository<CodeSetEntity, UUID> {

    Optional<CodeSetEntity> findByCodeSetKey(String codeSetKey);
}
