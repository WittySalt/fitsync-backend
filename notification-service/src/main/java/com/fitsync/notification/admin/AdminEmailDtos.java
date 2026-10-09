package com.fitsync.notification.admin;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/** The request and response bodies of the admin send-now endpoints. None carries an address or a body. */
public final class AdminEmailDtos {

    private AdminEmailDtos() {
    }

    public record SendRequest(@NotNull AdminEmailKind kind) {
    }

    /** SENT when SES took it, RETRYING when SES refused and the tick will retry, DROPPED when settings changed. */
    public enum Outcome { SENT, RETRYING, DROPPED }

    public record SendResponse(AdminEmailKind kind, Outcome outcome) {
    }

    public record KindAvailability(AdminEmailKind kind, boolean available, String reason, String message) {

        static KindAvailability of(AdminEmailKind kind, AdminEmailRefusal refusal) {
            return refusal == null
                    ? new KindAvailability(kind, true, null, null)
                    : new KindAvailability(kind, false, refusal.name(), refusal.message());
        }
    }

    public record AvailabilityResponse(List<KindAvailability> kinds) {

        public AvailabilityResponse {
            kinds = List.copyOf(kinds);
        }
    }
}
