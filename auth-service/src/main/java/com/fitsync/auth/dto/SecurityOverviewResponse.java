package com.fitsync.auth.dto;

public record SecurityOverviewResponse(
        String authProvider,
        boolean passwordManagedExternally,
        boolean twoFactorEnabled,
        boolean twoFactorConfigurable,
        String recoveryEmail,
        boolean recoveryEmailUpdatable,
        int sessionCount,
        boolean sessionTrackingAvailable,
        String securityLabel) {
}
