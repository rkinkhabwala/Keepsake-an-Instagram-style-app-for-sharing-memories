package com.keepsake.common.request;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Uses X-Forwarded-For as the client address only when the request comes from a trusted proxy (in Compose, the
 * web container's nginx, which overwrites the header with the real client address). From anyone else the header
 * is ignored, so a client cannot pick its own address to dodge per-IP rate limits.
 *
 * <p>Trusted proxies are host names or IP literals. Names are resolved lazily and re-resolved every 30 seconds,
 * because Compose assigns container addresses dynamically and the proxy may start after the backend.
 */
public class TrustedProxyFilter extends OncePerRequestFilter {

    static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final Duration RESOLVE_INTERVAL = Duration.ofSeconds(30);
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    private final List<String> trustedProxies;
    private final Clock clock;
    private volatile Set<String> trustedAddresses = Set.of();
    private volatile Instant resolvedAt = Instant.MIN;

    public TrustedProxyFilter(List<String> trustedProxies, Clock clock) {
        this.trustedProxies = trustedProxies.stream().map(String::trim).filter(host -> !host.isEmpty()).toList();
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String clientAddress = forwardedClient(request);
        if (clientAddress == null) {
            filterChain.doFilter(request, response);
            return;
        }
        filterChain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public String getRemoteAddr() {
                return clientAddress;
            }

            @Override
            public String getRemoteHost() {
                return clientAddress;
            }
        }, response);
    }

    /** The client address the proxy reported, or null if the request is not from a trusted proxy. */
    private String forwardedClient(HttpServletRequest request) {
        String header = request.getHeader(FORWARDED_FOR);
        if (header == null || trustedProxies.isEmpty() || !trustedAddresses().contains(request.getRemoteAddr())) {
            return null;
        }
        // The proxy appends the address it saw, so the rightmost entry is the one it vouches for.
        String[] hops = header.split(",");
        String client = hops[hops.length - 1].trim();
        return IP_LITERAL.matcher(client).matches() ? client : null;
    }

    private Set<String> trustedAddresses() {
        Instant now = clock.instant();
        if (Duration.between(resolvedAt, now).compareTo(RESOLVE_INTERVAL) >= 0) {
            Set<String> addresses = new HashSet<>();
            for (String proxy : trustedProxies) {
                try {
                    for (InetAddress address : InetAddress.getAllByName(proxy)) {
                        addresses.add(address.getHostAddress());
                    }
                } catch (UnknownHostException notYetUp) {
                    // Not resolvable yet (the proxy may not have started); try again next interval.
                }
            }
            trustedAddresses = Set.copyOf(addresses);
            resolvedAt = now;
        }
        return trustedAddresses;
    }
}
