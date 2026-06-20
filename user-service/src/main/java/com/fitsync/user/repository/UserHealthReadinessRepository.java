package com.fitsync.user.repository;

import java.util.UUID;

import com.fitsync.user.entity.UserHealthReadinessEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserHealthReadinessRepository extends JpaRepository<UserHealthReadinessEntity, UUID> {
}
