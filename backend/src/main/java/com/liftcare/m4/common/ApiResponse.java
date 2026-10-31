package com.liftcare.m4.common;

import java.time.Instant;

/** Team-wide response envelope: { success, data, error, timestamp }. */
public record ApiResponse<T>(boolean success, T data, ApiError error, Instant timestamp) {
    public record ApiError(String code, String message) {}
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(true, data, null, Instant.now()); }
    public static ApiResponse<Void> fail(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message), Instant.now());
    }
}
