package com.keepsake.common.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TrustedProxyFilterTest {

    private static final String PROXY = "10.0.0.5";

    @Test
    void usesForwardedClientOnlyFromTrustedProxy() throws Exception {
        TrustedProxyFilter filter = new TrustedProxyFilter(List.of(PROXY), Clock.systemUTC());

        assertThat(clientSeenBehind(filter, PROXY, "198.51.100.7")).isEqualTo("198.51.100.7");
        assertThat(clientSeenBehind(filter, "203.0.113.9", "198.51.100.7")).isEqualTo("203.0.113.9");
    }

    @Test
    void takesTheAddressTheProxyAppendedAndRejectsNonAddresses() throws Exception {
        TrustedProxyFilter filter = new TrustedProxyFilter(List.of(PROXY), Clock.systemUTC());

        assertThat(clientSeenBehind(filter, PROXY, "1.2.3.4, 198.51.100.7")).isEqualTo("198.51.100.7");
        assertThat(clientSeenBehind(filter, PROXY, "2001:db8::1")).isEqualTo("2001:db8::1");
        assertThat(clientSeenBehind(filter, PROXY, "evil.example.com")).isEqualTo(PROXY);
        assertThat(clientSeenBehind(filter, PROXY, null)).isEqualTo(PROXY);
    }

    @Test
    void trustsNobodyWhenNoProxyIsConfigured() throws Exception {
        TrustedProxyFilter filter = new TrustedProxyFilter(List.of(), Clock.systemUTC());

        assertThat(clientSeenBehind(filter, "127.0.0.1", "198.51.100.7")).isEqualTo("127.0.0.1");
    }

    @Test
    void resolvesProxyHostNames() throws Exception {
        TrustedProxyFilter filter = new TrustedProxyFilter(List.of("localhost", "no-such-host.invalid"), Clock.systemUTC());

        assertThat(clientSeenBehind(filter, "127.0.0.1", "198.51.100.7")).isEqualTo("198.51.100.7");
    }

    private static String clientSeenBehind(TrustedProxyFilter filter, String remoteAddr, String forwardedFor)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader(TrustedProxyFilter.FORWARDED_FOR, forwardedFor);
        }
        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(),
                (wrapped, response) -> seen.set(((HttpServletRequest) wrapped).getRemoteAddr()));
        return seen.get();
    }
}
