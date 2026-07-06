/**
 * Service-contract clients for synchronous point-in-time reads (e.g. user-service for
 * age/sex to refine a TDEE estimate). Calls go through HTTP service contracts, never
 * another service's repository, and never a distributed transaction.
 */
package com.fitsync.recommendation.client;
