package com.fitsync.analytics.service;

import com.fitsync.analytics.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsCacheInvalidationService {

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.DASHBOARD_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.BODY_TRENDS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.WORKOUT_SUMMARY_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.WORKOUT_SERIES_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.NUTRITION_SUMMARY_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.NUTRITION_SERIES_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.COMPARE_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.GOAL_PROGRESS_CACHE, allEntries = true)
    })
    public void evictAllForUser(java.util.UUID userId) {
        // Phase 1 keeps invalidation coarse-grained so event-driven writes cannot leave stale aggregates behind.
    }
}
