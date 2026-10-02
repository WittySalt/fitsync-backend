package com.fitsync.nutrition.client;

import java.util.UUID;

/**
 * Reads the facts about a person that user-service owns and sends as events. The method never throws: a fact
 * that is not known is returned as unknown, so that a nutrition request never fails because of it.
 */
public interface PersonLookup {

    PersonFacts facts(UUID userId);
}
