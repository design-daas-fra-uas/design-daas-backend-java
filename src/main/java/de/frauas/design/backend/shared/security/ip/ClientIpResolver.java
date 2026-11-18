package de.frauas.design.backend.shared.security.ip;

import de.frauas.design.backend.config.properties.RateLimitingProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * Resolves the client IP address for rate-limiting purposes.
 *
 * <p>{@code X-Forwarded-For} is only trusted when the direct connection
 * originates from a configured trusted reverse proxy. Each proxy appends the
 * address it received the request from, so the header is read from right to
 * left and the first address that is not a trusted proxy is the client. The
 * left-most entries are written by the client itself and are never trusted
 * on their own: taking the first entry would let any client pick its own IP
 * and bypass the rate limit.</p>
 */
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final RateLimitingProperties properties;

    /**
     * Resolves the originating client IP address.
     *
     * @param request current HTTP request
     * @return resolved client IP address
     */
    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);

        if (forwardedFor == null || forwardedFor.isBlank() || !isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }
        return clientFromForwardedChain(forwardedFor.split(","), remoteAddress);
    }

    /** Walks the {@code X-Forwarded-For} hops from right to left and returns the first untrusted one. */
    private String clientFromForwardedChain(String[] hops, String remoteAddress) {
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!hop.isEmpty() && !isTrustedProxy(hop)) {
                return hop;
            }
        }
        // Every hop is a trusted proxy: the request started on one of them
        return hops[0].trim().isEmpty() ? remoteAddress : hops[0].trim();
    }

    private boolean isTrustedProxy(String remoteAddress) {
        return properties.getTrustedProxyRanges().stream().anyMatch(cidr -> matchesCidr(remoteAddress, cidr));
    }

    private boolean matchesCidr(String address, String cidr) {
        try {
            String[] parts = cidr.split("/", 2);
            byte[] networkBytes = InetAddress.getByName(parts[0]).getAddress();
            byte[] candidateBytes = InetAddress.getByName(address).getAddress();
            if (networkBytes.length != candidateBytes.length) {
                return false;
            }
            int maxPrefix = networkBytes.length * 8;
            int prefixLength = parts.length == 2 ? Integer.parseInt(parts[1]) : maxPrefix;
            return prefixLength >= 0 && prefixLength <= maxPrefix
                    && prefixMatches(networkBytes, candidateBytes, prefixLength);
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Compares the first {@code prefixLength} bits of two addresses of equal length. */
    private static boolean prefixMatches(byte[] networkBytes, byte[] candidateBytes, int prefixLength) {
        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (networkBytes[i] != candidateBytes[i]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainingBits);
        return (networkBytes[fullBytes] & mask) == (candidateBytes[fullBytes] & mask);
    }
}
