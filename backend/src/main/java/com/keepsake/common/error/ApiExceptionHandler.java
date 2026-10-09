package com.keepsake.common.error;

import com.keepsake.common.pagination.InvalidCursorException;
import com.keepsake.common.pagination.InvalidRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleInvalidArguments(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = ProblemResponses.create(
                HttpStatus.BAD_REQUEST, "Request validation failed", request);
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemResponses.create(
                HttpStatus.BAD_REQUEST, "Request validation failed", request);
        problem.setProperty("errors", exception.getConstraintViolations().stream()
                .collect(LinkedHashMap::new,
                        (errors, violation) -> errors.put(violation.getPropertyPath().toString(),
                                violation.getMessage()),
                        Map::putAll));
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadableBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().body(
                ProblemResponses.create(HttpStatus.BAD_REQUEST, "Request body is invalid", request));
    }

    @ExceptionHandler(InvalidCursorException.class)
    ResponseEntity<ProblemDetail> handleInvalidCursor(
            InvalidCursorException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().body(
                ProblemResponses.create(HttpStatus.BAD_REQUEST, exception.getMessage(), request));
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ProblemDetail> handleInvalidRequest(
            InvalidRequestException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().body(
                ProblemResponses.create(HttpStatus.BAD_REQUEST, exception.getMessage(), request));
    }

    @ExceptionHandler(UnauthenticatedException.class)
    ResponseEntity<ProblemDetail> handleUnauthenticated(
            UnauthenticatedException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ProblemResponses.create(HttpStatus.UNAUTHORIZED, exception.getMessage(), request));
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ProblemDetail> handleForbidden(
            ForbiddenException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ProblemResponses.create(HttpStatus.FORBIDDEN, exception.getMessage(), request));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(
            NotFoundException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ProblemResponses.create(HttpStatus.NOT_FOUND, exception.getMessage(), request));
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> handleConflict(
            ConflictException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ProblemResponses.create(HttpStatus.CONFLICT, exception.getMessage(), request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataConflict(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ProblemResponses.create(HttpStatus.CONFLICT, "A record conflicts with existing data", request));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> handleRateLimit(
            RateLimitExceededException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemResponses.create(
                HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), request);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(exception.getRetryAfterSeconds()))
                .body(problem);
    }
}
