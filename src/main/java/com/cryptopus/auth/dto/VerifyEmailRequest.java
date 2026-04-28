package com.cryptopus.auth.dto;

/**
 * POST /api/auth/verify-email body.
 *
 * <p>Field names are serialized as snake_case via the shared Jackson
 * {@code PropertyNamingStrategies.SNAKE_CASE} configuration, so
 * {@code verificationCode} maps to JSON key {@code verification_code}.</p>
 */
public record VerifyEmailRequest(
        String email,
        String verificationCode
) {
}
