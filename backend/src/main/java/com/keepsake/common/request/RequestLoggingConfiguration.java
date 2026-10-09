package com.keepsake.common.request;

import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class RequestLoggingConfiguration {

    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /** Runs right after the request-id filter, before security and rate limiting read the client address. */
    @Bean
    FilterRegistrationBean<TrustedProxyFilter> trustedProxyFilter(
            @Value("${keepsake.trusted-proxies:}") List<String> trustedProxies,
            Clock clock
    ) {
        FilterRegistrationBean<TrustedProxyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TrustedProxyFilter(trustedProxies, clock));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    @Bean
    TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }

    @Bean(name = "taskExecutor")
    ThreadPoolTaskExecutor taskExecutor(TaskDecorator mdcTaskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("keepsake-async-");
        executor.setTaskDecorator(mdcTaskDecorator);
        return executor;
    }
}
