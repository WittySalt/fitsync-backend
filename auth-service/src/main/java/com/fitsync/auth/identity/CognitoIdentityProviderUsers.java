package com.fitsync.auth.identity;

import java.net.URI;
import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

/**
 * Deletes Cognito users with {@code AdminDeleteUser}. The account keeps the token's {@code sub} as its external
 * subject, and Cognito accepts the {@code sub} of a native pool user as the user name.
 */
public class CognitoIdentityProviderUsers implements IdentityProviderUsers {

    /** {@code <region>_<id>}, e.g. {@code ap-southeast-1_myVyuOmPD}. */
    private static final Pattern USER_POOL_ID = Pattern.compile("[a-z0-9-]+_[0-9A-Za-z]+");

    private final ObjectProvider<CognitoIdentityProviderClient> clientProvider;
    private final String userPoolId;

    public CognitoIdentityProviderUsers(ObjectProvider<CognitoIdentityProviderClient> clientProvider,
                                        String userPoolId) {
        this.clientProvider = clientProvider;
        this.userPoolId = userPoolId;
    }

    @Override
    public boolean delete(String externalSubject) {
        try {
            clientProvider.getObject().adminDeleteUser(AdminDeleteUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(externalSubject)
                    .build());
        } catch (UserNotFoundException ex) {
            // Already gone, for instance deleted by an earlier run that failed before erasing the account row.
        }
        return true;
    }

    /**
     * The user pool id from the issuer URI the service already validates tokens against,
     * {@code https://cognito-idp.<region>.amazonaws.com/<userPoolId>}.
     */
    public static String userPoolIdFromIssuer(String issuerUri) {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalStateException("security.cognito.issuer-uri is required to delete Cognito users");
        }
        String path = URI.create(issuerUri.trim()).getPath();
        String candidate = path == null ? "" : path.replaceAll("^/+|/+$", "");
        if (!USER_POOL_ID.matcher(candidate).matches()) {
            throw new IllegalStateException("security.cognito.issuer-uri does not end with a Cognito user pool id");
        }
        return candidate;
    }

    /** The region is the part of the user pool id before the underscore. */
    public static String regionOf(String userPoolId) {
        return userPoolId.substring(0, userPoolId.indexOf('_'));
    }
}
