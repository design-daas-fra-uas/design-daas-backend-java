package de.frauas.design.backend.shared.security.ip;

import de.frauas.design.backend.config.properties.RateLimitingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClientIpResolver")
class ClientIpResolverTest {

    private RateLimitingProperties properties;
    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new RateLimitingProperties();
        properties.setTrustedProxyRanges(List.of("127.0.0.1/32", "10.0.0.0/8", "::1/128"));
        resolver = new ClientIpResolver(properties);
    }

    private String resolve(String remote, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return resolver.resolve(request);
    }

    @Test
    @DisplayName("uses the remote address when there is no header")
    void noHeader() {
        assertThat(resolve("203.0.113.5", null)).isEqualTo("203.0.113.5");
        assertThat(resolve("127.0.0.1", " ")).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("ignores the header from a client that is not a trusted proxy")
    void untrustedRemote() {
        assertThat(resolve("203.0.113.5", "1.2.3.4")).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("takes the right-most address that is not a trusted proxy")
    void rightMostUntrusted() {
        // the client sent "6.6.6.6" itself; nginx appended its real address
        assertThat(resolve("127.0.0.1", "6.6.6.6, 198.51.100.7")).isEqualTo("198.51.100.7");
        // a chain of trusted proxies in front
        assertThat(resolve("127.0.0.1", "198.51.100.7, 10.1.2.3")).isEqualTo("198.51.100.7");
        // empty entries are skipped
        assertThat(resolve("127.0.0.1", "198.51.100.7, ,")).isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("falls back to the first hop when every hop is a trusted proxy")
    void allTrusted() {
        assertThat(resolve("127.0.0.1", "10.0.0.1, 10.0.0.2")).isEqualTo("10.0.0.1");
        assertThat(resolve("127.0.0.1", " ,10.0.0.2")).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("matches IPv6 proxies")
    void ipv6Proxy() {
        assertThat(resolve("::1", "198.51.100.7")).isEqualTo("198.51.100.7");
    }

    @ParameterizedTest(name = "{0} in {1} -> {2}")
    @CsvSource({
        "10.20.30.40, 10.0.0.0/8, true",
        "11.0.0.1, 10.0.0.0/8, false",
        "192.168.1.130, 192.168.1.128/25, true",
        "192.168.1.127, 192.168.1.128/25, false",
        "127.0.0.1, 127.0.0.1, true",
        "127.0.0.1, ::1/128, false",
        "127.0.0.1, 127.0.0.1/33, false",
        "127.0.0.1, 127.0.0.1/-1, false",
        "127.0.0.1, 127.0.0.1/x, false",
    })
    @DisplayName("CIDR matching")
    void cidrMatching(String address, String cidr, boolean trusted) {
        properties.setTrustedProxyRanges(List.of(cidr));
        String expected = trusted ? "198.51.100.7" : address;
        assertThat(resolve(address, "198.51.100.7")).isEqualTo(expected);
    }
}
