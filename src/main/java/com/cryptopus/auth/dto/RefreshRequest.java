package com.cryptopus.auth.dto;

/** POST /api/auth/refresh body. */
public record RefreshRequest(String refreshToken) {
}
