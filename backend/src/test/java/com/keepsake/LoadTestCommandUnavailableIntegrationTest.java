package com.keepsake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.keepsake.support.IntegrationTestBase;
import java.util.Arrays;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * The load-test command mints access tokens without passwords, so it must not exist in a normal build, and even
 * in a {@code mvn -Ploadtest} build it must stay inactive unless the {@code loadtest} Spring profile is on.
 */
class LoadTestCommandUnavailableIntegrationTest extends IntegrationTestBase {

    @Autowired ApplicationContext context;

    @Test
    void theDefaultBuildDoesNotContainIt() {
        assumeFalse(Boolean.getBoolean("keepsake.loadtest.compiled"), "built with -Ploadtest");
        assertThatThrownBy(() -> Class.forName("com.keepsake.loadtest.LoadTestCommand"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void noLoadTestBeanRunsWithoutTheLoadtestProfile() {
        assertThat(context.getEnvironment().getActiveProfiles()).doesNotContain("loadtest");
        assertThat(Arrays.stream(context.getBeanDefinitionNames())
                .map(context::getType)
                .filter(Objects::nonNull)
                .filter(type -> type.getPackageName().startsWith("com.keepsake.loadtest")))
                .isEmpty();
    }
}
