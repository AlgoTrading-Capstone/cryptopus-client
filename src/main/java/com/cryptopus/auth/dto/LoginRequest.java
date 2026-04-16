package com.cryptopus.auth.dto;

/** POST /api/auth/login body. */
public record LoginRequest(String email, String password) {
}
