package com.cryptopus.auth.dto;

/**
 * POST /api/auth/verify-otp-setup body.
 *
 * <p>Field names are serialized as snake_case via the shared Jackson
 * {@code PropertyNamingStrategies.SNAKE_CASE} configuration, so
 * {@code otpCode} maps to JSON key {@code otp_code}.</p>
 */
public record VerifyOtpSetupRequest(String email, String otpCode) {
}
