package com.keepsake.common.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void acceptsSafeClientRequestIdAndClearsMdcAfterRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_NAME, "web-client:123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> assertEquals("web-client:123", MDC.get("requestId")));

        assertEquals("web-client:123", response.getHeader(RequestIdFilter.HEADER_NAME));
        assertNull(MDC.get("requestId"));
    }

    @Test
    void replacesUnsafeRequestIdWithGeneratedId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_NAME, "bad\r\nvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> assertNotNull(MDC.get("requestId")));

        String requestId = response.getHeader(RequestIdFilter.HEADER_NAME);
        assertNotNull(requestId);
        assertEquals(7, java.util.UUID.fromString(requestId).version());
        assertNull(MDC.get("requestId"));
    }
}
