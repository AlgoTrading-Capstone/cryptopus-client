package com.cryptopus.auth.dto;

/**
 * POST /api/auth/register body.
 *
 * <p>Field names are serialized as snake_case via the shared Jackson
 * {@code PropertyNamingStrategies.SNAKE_CASE} configuration.</p>
 */
public record RegisterRequest(
        String firstName,
        String lastName,
        String dob,
        String email,
        String phoneNumber,
        String address,
        String city,
        String country,
        String postalCode,
        String password
) {
}
