package com.liftcare.m4.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> api(ApiException e) {
        return ResponseEntity.status(e.status).body(ApiResponse.fail(e.code, e.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream().map(f -> f.getField() + " " + f.getDefaultMessage()).findFirst().orElse("Invalid request");
        return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", msg));
    }
    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiResponse<Void>> noAuth(MissingRequestHeaderException e) {
        return ResponseEntity.status(401).body(ApiResponse.fail("UNAUTHENTICATED", "Missing header " + e.getHeaderName()));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> other(Exception e) {
        log.error("Unhandled", e);
        return ResponseEntity.status(500).body(ApiResponse.fail("INTERNAL_ERROR", "Something went wrong on the server"));
    }
}
