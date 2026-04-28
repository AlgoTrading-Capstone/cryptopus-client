package com.cryptopus.auth.dto;

/** {@code data} block returned by POST /api/auth/resend-verification-email. */
public class ResendVerificationResponse {
    private String message;
    private long expiresInSeconds;
    private long cooldownSeconds;

    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }

    public long getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(long v) { this.expiresInSeconds = v; }

    public long getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(long v) { this.cooldownSeconds = v; }
}
