package com.fitsync.user.repository;

import java.util.UUID;

import com.fitsync.user.entity.UserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, UUID> {
}
