package com.cryptopus.config;

import java.time.Duration;

/**
 * Centralized API configuration: base URL, timeouts and endpoint paths.
 * Keeping these as constants in a single place makes it trivial to add new
 * endpoints or swap environments later.
 */
public final class ApiConfig {

    private ApiConfig() {
    }

    public static final String BASE_URL = "http://127.0.0.1:8000";

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    // --- Auth endpoints ---
    public static final String AUTH_LOGIN      = "/api/auth/login";
    public static final String AUTH_VERIFY_OTP = "/api/auth/verify-otp";
    public static final String AUTH_REFRESH    = "/api/auth/refresh";
}
