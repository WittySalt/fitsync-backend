package com.fitsync.recommendation.handler;

import java.util.UUID;

/**
 * A hook for a package outside the projection to remove what it holds for a deleted account.
 *
 * <p>This interface lives here, not in {@code coach}, precisely so {@link AccountDeletedEventHandler}
 * can depend on it without importing anything from {@code coach} — the projection must never do
 * that, because a consumer that reached into the coach package could end up awaiting a model call
 * inside a Kafka listener. The coach layer is allowed to depend on the projection, so its purge logic
 * implements this interface instead, and Spring wires every implementation into the handler as a
 * plain list.
 */
public interface AccountDataPurger {

    void purgeForUser(UUID userId);
}
