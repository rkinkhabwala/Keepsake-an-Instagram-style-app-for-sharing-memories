package com.keepsake.common.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void copiesSubmittingContextAndRestoresWorkerContext() {
        MdcTaskDecorator decorator = new MdcTaskDecorator();
        AtomicReference<String> observedRequestId = new AtomicReference<>();
        MDC.put("requestId", "request-123");

        Runnable decorated = decorator.decorate(() -> observedRequestId.set(MDC.get("requestId")));
        MDC.clear();
        MDC.put("worker", "worker-context");
        decorated.run();

        assertEquals("request-123", observedRequestId.get());
        assertEquals("worker-context", MDC.get("worker"));
        assertNull(MDC.get("requestId"));
    }
}
