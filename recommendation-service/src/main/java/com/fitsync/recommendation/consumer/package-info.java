/**
 * Thin Kafka consumers (RC-8), group {@code recommendation-consumer-group}. Consumers
 * delegate to handlers to update the read model and mark context stale; they never
 * trigger AI generation per event.
 */
package com.fitsync.recommendation.consumer;
