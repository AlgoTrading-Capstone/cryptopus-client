package com.cryptopus.auth.dto;

/** {@code data} block returned by POST /api/auth/verify-email (200). */
public class VerifyEmailResponse {
    private boolean emailVerified;
    private boolean otpSetupRequired;
    private String message;

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean v) { this.emailVerified = v; }

    public boolean isOtpSetupRequired() { return otpSetupRequired; }
    public void setOtpSetupRequired(boolean v) { this.otpSetupRequired = v; }

    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }
}
