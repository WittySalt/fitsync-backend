package com.fitsync.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressResolverTests {

    /** CloudFront then the ALB: [client-written..., viewer, CloudFront edge] in the header, the ALB as the peer. */
    private final ClientAddressResolver behindCloudFrontAndAlb = new ClientAddressResolver(2);

    @Test
    void readsTheAddressCloudFrontSawBehindCloudFrontAndAlb() {
        MockHttpServletRequest request = request("10.0.1.20", "203.0.113.7, 130.176.10.5");

        assertThat(behindCloudFrontAndAlb.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void ignoresAddressesTheClientPrependedToForwardedFor() {
        MockHttpServletRequest request = request("10.0.1.20", "1.2.3.4, 5.6.7.8, 203.0.113.7, 130.176.10.5");

        assertThat(behindCloudFrontAndAlb.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void aSpoofedHeaderStillLandsOnTheRealClient() {
        // Two callers behind CloudFront claiming the same address are still told apart.
        MockHttpServletRequest first = request("10.0.1.20", "9.9.9.9, 198.51.100.1, 130.176.10.5");
        MockHttpServletRequest second = request("10.0.1.20", "9.9.9.9, 198.51.100.2, 130.176.10.5");

        assertThat(behindCloudFrontAndAlb.resolve(first)).isEqualTo("198.51.100.1");
        assertThat(behindCloudFrontAndAlb.resolve(second)).isEqualTo("198.51.100.2");
    }

    @Test
    void readsEveryForwardedForHeaderInOrder() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.1.20");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 130.176.10.5");

        assertThat(behindCloudFrontAndAlb.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void withoutTrustedProxiesTheHeaderIsIgnored() {
        ClientAddressResolver direct = new ClientAddressResolver(0);
        MockHttpServletRequest request = request("192.0.2.10", "1.2.3.4");

        assertThat(direct.resolve(request)).isEqualTo("192.0.2.10");
    }

    @Test
    void aShortChainFallsBackToItsLeftMostEntry() {
        MockHttpServletRequest request = request("127.0.0.1", null);

        assertThat(behindCloudFrontAndAlb.resolve(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void anEntryThatIsNotAnAddressIsNeverUsedAsAKey() {
        MockHttpServletRequest request = request("10.0.1.20", "evil\r\nkey, 130.176.10.5");

        assertThat(behindCloudFrontAndAlb.resolve(request)).isEqualTo(ClientAddressResolver.UNKNOWN);
    }

    @Test
    void acceptsIpv6Addresses() {
        MockHttpServletRequest request = request("10.0.1.20", "2001:db8::1, 130.176.10.5");

        assertThat(behindCloudFrontAndAlb.resolve(request)).isEqualTo("2001:db8::1");
    }

    @Test
    void recognisesOnlyIpLiterals() {
        assertThat(ClientAddressResolver.isIpLiteral("203.0.113.7")).isTrue();
        assertThat(ClientAddressResolver.isIpLiteral("::1")).isTrue();
        assertThat(ClientAddressResolver.isIpLiteral("example.com")).isFalse();
        assertThat(ClientAddressResolver.isIpLiteral("1234")).isFalse();
        assertThat(ClientAddressResolver.isIpLiteral("a:b:c; DROP")).isFalse();
    }

    private static MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }
}
