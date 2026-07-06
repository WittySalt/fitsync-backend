/**
 * Tier-2 external AI (RC-6): Claude API client, prompt builders, DTOs and rule/template
 * fallback. Always async, prompt-cached and model-tiered; never on the read path,
 * never invoked per event.
 */
package com.fitsync.recommendation.service.ai;
