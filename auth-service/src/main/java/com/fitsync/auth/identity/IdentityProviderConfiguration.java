package com.fitsync.auth.identity;

import com.fitsync.auth.config.CognitoProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

/**
 * Picks how the account purge deletes identity-provider users. Cognito is called only when
 * {@code fitsync.account-deletion.purge.cognito-enabled} is true, so a local run without AWS credentials never
 * tries to reach it. The user pool and its region come from the issuer URI the service already trusts, and the
 * credentials from the default chain, which is the instance role in the cloud.
 */
@Configuration
public class IdentityProviderConfiguration {

    private static final String COGNITO_FLAG = "cognito-enabled";
    private static final String PURGE_PREFIX = "fitsync.account-deletion.purge";

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = PURGE_PREFIX, name = COGNITO_FLAG, havingValue = "true")
    CognitoIdentityProviderClient cognitoIdentityProviderClient(CognitoProperties cognitoProperties) {
        String userPoolId = CognitoIdentityProviderUsers.userPoolIdFromIssuer(cognitoProperties.issuerUri());
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(CognitoIdentityProviderUsers.regionOf(userPoolId)))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = PURGE_PREFIX, name = COGNITO_FLAG, havingValue = "true")
    IdentityProviderUsers cognitoIdentityProviderUsers(ObjectProvider<CognitoIdentityProviderClient> client,
                                                       CognitoProperties cognitoProperties) {
        return new CognitoIdentityProviderUsers(client,
                CognitoIdentityProviderUsers.userPoolIdFromIssuer(cognitoProperties.issuerUri()));
    }

    @Bean
    @ConditionalOnProperty(prefix = PURGE_PREFIX, name = COGNITO_FLAG, havingValue = "false", matchIfMissing = true)
    IdentityProviderUsers disabledIdentityProviderUsers() {
        return externalSubject -> false;
    }
}
