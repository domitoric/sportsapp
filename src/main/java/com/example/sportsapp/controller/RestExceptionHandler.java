package com.example.sportsapp.controller;

import jakarta.persistence.EntityNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts backend exceptions into predictable JSON error responses.
 */
@RestControllerAdvice
public class RestExceptionHandler {

    /**
     * Maps missing-entity failures to a stable 404 JSON payload.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(EntityNotFoundException exception) {
        return Map.of("error", exception.getMessage());
    }

    /**
     * Maps validation and business-rule failures to a stable 400 JSON payload.
     */
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(Exception exception) {
        return Map.of("error", exception.getMessage());
    }
}

