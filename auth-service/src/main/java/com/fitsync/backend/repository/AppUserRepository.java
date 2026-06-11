package com.fitsync.backend.repository;

import java.util.Optional;
import java.util.UUID;

import com.fitsync.backend.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByExternalSubject(String externalSubject);
}
