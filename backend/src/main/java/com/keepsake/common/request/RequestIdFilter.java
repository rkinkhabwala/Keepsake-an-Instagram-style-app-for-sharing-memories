package com.keepsake.common.request;

import com.keepsake.common.id.UuidV7;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-ID";
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String suppliedId = request.getHeader(HEADER_NAME);
        String requestId = suppliedId != null && VALID_REQUEST_ID.matcher(suppliedId).matches()
                ? suppliedId
                : UuidV7.generate().toString();

        MDC.put("requestId", requestId);
        response.setHeader(HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
        }
    }
}
