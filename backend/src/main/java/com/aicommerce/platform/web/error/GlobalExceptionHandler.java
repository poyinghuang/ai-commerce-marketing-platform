package com.aicommerce.platform.web.error;

import java.time.Instant;
import java.util.List;

import com.aicommerce.platform.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<FieldErrorDetail> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        ApiError error = error(
                "VALIDATION_ERROR",
                "Request validation failed",
                request,
                fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure", exception);
        ApiError error = error(
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request,
                null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNotFound(HttpServletRequest request) {
        ApiError error = error(
                "NOT_FOUND",
                "Resource not found",
                request,
                null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    private ApiError error(
            String code,
            String message,
            HttpServletRequest request,
            List<FieldErrorDetail> fieldErrors) {
        return new ApiError(
                code,
                message,
                (String) request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE),
                Instant.now(),
                request.getRequestURI(),
                fieldErrors);
    }
}
