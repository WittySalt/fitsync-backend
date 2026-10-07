package com.fitsync.user.repository;

import java.util.UUID;

import com.fitsync.user.entity.UserGuideStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGuideStateRepository extends JpaRepository<UserGuideStateEntity, UUID> {
}
