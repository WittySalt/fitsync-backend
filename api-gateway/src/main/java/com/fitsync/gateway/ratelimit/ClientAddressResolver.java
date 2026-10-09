package com.fitsync.gateway.ratelimit;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Reads the client address that an unauthenticated request is counted under.
 *
 * <p>The rule: take every {@code X-Forwarded-For} entry in order, append the address the connection came from,
 * and count {@code trustedProxyHops} entries back from the right. Each proxy in front of the gateway appends the
 * address it received the request from, so the right-most entries are written by the platform and everything to
 * their left was written by the client. In the cloud the chain is CloudFront, then the ALB, so with two trusted
 * hops the list reads {@code [anything the client sent..., client, CloudFront edge, ALB]} and the entry three
 * from the right is the address CloudFront saw. A client can prepend whatever it likes, but it cannot change the
 * entries the proxies append, so it cannot choose its own key.
 *
 * <p>This holds only while the gateway is reachable through those proxies alone (the security groups allow the
 * gateway port from the ALB only, and the prod ALB from CloudFront only). With fewer entries than hops, which is
 * what a direct local call looks like, the left-most entry is used. An entry that is not an IP literal is never
 * used as a key; such requests share one {@code unknown} bucket. Nothing here resolves a host name.
 */
public final class ClientAddressResolver {

    static final String UNKNOWN = "unknown";

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    private final int trustedProxyHops;

    public ClientAddressResolver(int trustedProxyHops) {
        this.trustedProxyHops = Math.max(trustedProxyHops, 0);
    }

    public String resolve(HttpServletRequest request) {
        List<String> chain = new ArrayList<>();
        Enumeration<String> headers = request.getHeaders(FORWARDED_FOR);
        while (headers != null && headers.hasMoreElements()) {
            String header = headers.nextElement();
            if (header == null) {
                continue;
            }
            for (String entry : header.split(",")) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) {
                    chain.add(trimmed);
                }
            }
        }
        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress != null && !remoteAddress.isBlank()) {
            chain.add(remoteAddress.trim());
        }
        if (chain.isEmpty()) {
            return UNKNOWN;
        }
        String candidate = chain.get(Math.max(0, chain.size() - 1 - trustedProxyHops));
        return isIpLiteral(candidate) ? candidate : UNKNOWN;
    }

    static boolean isIpLiteral(String value) {
        if (IPV4.matcher(value).matches()) {
            return true;
        }
        return value.indexOf(':') >= 0 && IPV6.matcher(value).matches();
    }
}
