package com.fitsync.auth.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

class CognitoIdentityProviderUsersTests {

    private static final String POOL = "ap-southeast-1_myVyuOmPD";

    private final CognitoIdentityProviderClient client = mock(CognitoIdentityProviderClient.class);

    @SuppressWarnings("unchecked")
    private CognitoIdentityProviderUsers users() {
        ObjectProvider<CognitoIdentityProviderClient> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(client);
        return new CognitoIdentityProviderUsers(provider, POOL);
    }

    @Test
    void deletesTheUserBySubjectInThePool() {
        assertThat(users().delete("subject-1")).isTrue();

        ArgumentCaptor<AdminDeleteUserRequest> request = ArgumentCaptor.forClass(AdminDeleteUserRequest.class);
        verify(client).adminDeleteUser(request.capture());
        assertThat(request.getValue().userPoolId()).isEqualTo(POOL);
        assertThat(request.getValue().username()).isEqualTo("subject-1");
    }

    @Test
    void aUserThatIsAlreadyGoneCountsAsDeleted() {
        when(client.adminDeleteUser(any(AdminDeleteUserRequest.class)))
                .thenThrow(UserNotFoundException.builder().message("User does not exist.").build());

        assertThat(users().delete("subject-2")).isTrue();
    }

    @Test
    void anyOtherCognitoErrorIsPassedOnSoThePurgeTriesAgain() {
        when(client.adminDeleteUser(any(AdminDeleteUserRequest.class)))
                .thenThrow(CognitoIdentityProviderException.builder().message("AccessDenied").build());

        assertThatThrownBy(() -> users().delete("subject-3")).isInstanceOf(CognitoIdentityProviderException.class);
    }

    @Test
    void thePoolAndRegionComeFromTheIssuer() {
        String pool = CognitoIdentityProviderUsers.userPoolIdFromIssuer(
                "https://cognito-idp.ap-southeast-1.amazonaws.com/ap-southeast-1_myVyuOmPD");

        assertThat(pool).isEqualTo(POOL);
        assertThat(CognitoIdentityProviderUsers.regionOf(pool)).isEqualTo("ap-southeast-1");
    }

    @Test
    void anIssuerWithoutAPoolIdIsRefused() {
        assertThatThrownBy(() -> CognitoIdentityProviderUsers.userPoolIdFromIssuer("https://example.com/"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CognitoIdentityProviderUsers.userPoolIdFromIssuer(""))
                .isInstanceOf(IllegalStateException.class);
    }
}
