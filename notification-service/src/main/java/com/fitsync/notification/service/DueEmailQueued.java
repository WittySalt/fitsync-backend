package com.fitsync.notification.service;

import java.util.UUID;

/** Published by {@link EmailQueue} when an email is queued with a time that has already come. */
public record DueEmailQueued(UUID emailId) {
}
