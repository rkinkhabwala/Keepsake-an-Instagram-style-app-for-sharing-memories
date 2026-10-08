package com.keepsake.auth.service;

import com.keepsake.common.error.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private final ProxyManager<byte[]> proxyManager;

    public RateLimitService(ProxyManager<byte[]> proxyManager) {
        this.proxyManager = proxyManager;
    }

    public void checkSignup(String ipAddress) {
        enforce("signup", ipAddress, 3, Duration.ofHours(1));
    }

    public void checkLogin(String ipAddress) {
        enforce("login", ipAddress, 5, Duration.ofMinutes(1));
    }

    public void checkMemoryCreate(UUID userId) {
        enforce("memory-create", userId.toString(), 30, Duration.ofHours(1));
    }

    public void checkComment(UUID userId) {
        enforce("comment", userId.toString(), 60, Duration.ofHours(1));
    }

    private void enforce(String action, String subject, long capacity, Duration window) {
        byte[] key = ("keepsake:rate:" + action + ":" + sha256(subject)).getBytes(StandardCharsets.UTF_8);
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.intervally(capacity, window)))
                .build();
        BucketProxy bucket = proxyManager.builder().build(key, configuration);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long retryAfterSeconds = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
            throw new RateLimitExceededException(retryAfterSeconds);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 algorithm is unavailable", exception);
        }
    }
}
