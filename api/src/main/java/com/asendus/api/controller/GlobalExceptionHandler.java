package com.asendus.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Every error answers as { "error": "..." }. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        return error(HttpStatus.BAD_REQUEST, "Vigane päring");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "Vigane päring");
    }

    // Bad path variables and query params (a date that isn't a date, period=abc, /substitutions/abc).
    // MethodArgumentTypeMismatchException is a TypeMismatchException.
    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(TypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "Vigane päring");
    }

    // A unique constraint lost a race: a double-tapped absence, or two activations at once
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, "Andmed muutusid samal ajal, proovi uuesti");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getReason() != null ? ex.getReason() : ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        // Spring's own errors (404 no route, 405 wrong method, ...) keep their status
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return error(status, estonianReason(status));
        }

        log.error("Unexpected error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Serveri viga");
    }

    private static String estonianReason(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Ei leitud";
            case METHOD_NOT_ALLOWED -> "Vigane päring";
            case PAYLOAD_TOO_LARGE -> "Fail on liiga suur";
            default -> status.is4xxClientError() ? "Vigane päring" : "Serveri viga";
        };
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
