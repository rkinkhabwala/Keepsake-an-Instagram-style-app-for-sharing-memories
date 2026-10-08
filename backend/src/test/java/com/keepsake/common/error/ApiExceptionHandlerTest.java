package com.keepsake.common.error;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.keepsake.common.pagination.InvalidCursorException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void producesProblemDetailsWithRequestIdAndInstance() {
        MDC.put("requestId", "req-42");
        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/feed");

        ResponseEntity<ProblemDetail> response =
                handler.handleInvalidCursor(new InvalidCursorException(), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("cursor must be a UUID", response.getBody().getDetail());
        assertEquals("/api/feed", response.getBody().getInstance().toString());
        assertEquals("req-42", response.getBody().getProperties().get("requestId"));
    }
}
