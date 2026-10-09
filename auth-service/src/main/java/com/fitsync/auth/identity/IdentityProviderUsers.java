package com.fitsync.auth.identity;

/** The user directory that signs a Cognito account in, seen from the account purge. */
public interface IdentityProviderUsers {

    /**
     * Deletes the identity-provider user with this subject. A user that no longer exists counts as deleted.
     *
     * @return false when deleting identity-provider users is switched off, so nothing was deleted
     * @throws RuntimeException when the provider cannot be reached or refuses the call; the purge tries again later
     */
    boolean delete(String externalSubject);
}
