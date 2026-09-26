package com.fitsync.recommendation.coach.repository;

import java.util.List;
import java.util.UUID;

import com.fitsync.recommendation.coach.entity.ToolInvocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ToolInvocationRepository extends JpaRepository<ToolInvocationEntity, UUID> {

    List<ToolInvocationEntity> findByTurnIdOrderBySeqAsc(UUID turnId);

    List<ToolInvocationEntity> findByTurnIdInOrderBySeqAsc(List<UUID> turnIds);
}
