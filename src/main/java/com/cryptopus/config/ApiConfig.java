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

    public static final String BASE_URL = "http://localhost:8000";

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    // --- Auth endpoints ---
    public static final String AUTH_LOGIN                     = "/api/auth/login";
    public static final String AUTH_VERIFY_OTP                = "/api/auth/verify-otp";
    public static final String AUTH_REFRESH                   = "/api/auth/refresh";
    public static final String AUTH_REGISTER                  = "/api/auth/register";
    public static final String AUTH_VERIFY_EMAIL              = "/api/auth/verify-email";
    public static final String AUTH_RESEND_VERIFICATION_EMAIL = "/api/auth/resend-verification-email";
    public static final String AUTH_SETUP_OTP                 = "/api/auth/setup-otp";
    public static final String AUTH_VERIFY_OTP_SETUP          = "/api/auth/verify-otp-setup";

    // --- System endpoints ---
    public static final String SYSTEM_HEALTH                  = "/api/health";
    public static final String SYSTEM_VERSION                 = "/api/version";

    // --- Health-probe tuning ---
    // Health probes run out-of-band of ApiClient and need a much tighter SLA
    // than regular business calls.
    public static final Duration HEALTH_CONNECT_TIMEOUT   = Duration.ofSeconds(1);
    public static final Duration HEALTH_REQUEST_TIMEOUT   = Duration.ofSeconds(2);
    public static final Duration HEALTH_POLL_CONNECTED    = Duration.ofSeconds(10);
    public static final Duration HEALTH_POLL_DISCONNECTED = Duration.ofSeconds(2);
}