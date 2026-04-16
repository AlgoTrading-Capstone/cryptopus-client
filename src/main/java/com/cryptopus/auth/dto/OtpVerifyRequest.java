package com.cryptopus.auth.dto;

/** POST /api/auth/verify-otp body. */
public record OtpVerifyRequest(String temporarySessionId, String otpCode) {
}
