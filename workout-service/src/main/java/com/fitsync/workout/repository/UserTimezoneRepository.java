package com.fitsync.workout.repository;

import java.util.UUID;

import com.fitsync.workout.entity.UserTimezoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTimezoneRepository extends JpaRepository<UserTimezoneEntity, UUID> {
}
