/**
 * Read-only recommendation API (RC-7): {@code GET /recommendations},
 * {@code GET /{type}}, {@code POST /refresh}. All reads are served from the cache;
 * generation is never inline on the read path.
 */
package com.fitsync.recommendation.controller;
