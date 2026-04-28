package com.cryptopus.auth.dto;

/**
 * POST /api/auth/setup-otp body.
 *
 * <p>Field names are serialized as snake_case via the shared Jackson
 * {@code PropertyNamingStrategies.SNAKE_CASE} configuration.</p>
 */
public record SetupOtpRequest(String email) {
}
