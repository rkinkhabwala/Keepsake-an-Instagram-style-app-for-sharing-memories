package com.keepsake.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ProblemResponses {

    private ProblemResponses() {
    }

    public static ProblemDetail create(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        String requestId = MDC.get("requestId");
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }
}
