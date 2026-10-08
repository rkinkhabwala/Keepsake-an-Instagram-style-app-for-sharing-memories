package com.keepsake.auth.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfiguration {

    @Bean(destroyMethod = "shutdown")
    RedisClient rateLimitRedisClient(RedisProperties properties) {
        RedisURI redisUri = RedisURI.Builder.redis(properties.getHost(), properties.getPort()).build();
        return RedisClient.create(redisUri);
    }

    @Bean
    ProxyManager<byte[]> rateLimitProxyManager(RedisClient rateLimitRedisClient) {
        return Bucket4jLettuce.casBasedBuilder(rateLimitRedisClient).build();
    }
}
