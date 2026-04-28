package com.cryptopus.auth.dto;

/** Request body for POST /api/auth/resend-verification-email. */
public class ResendVerificationRequest {
    private final String email;

    public ResendVerificationRequest(String email) {
        this.email = email;
    }

    public String getEmail() { return email; }
}
