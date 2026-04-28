package com.cryptopus.auth.dto;

/** {@code data} block returned by POST /api/auth/verify-otp-setup (200). */
public class VerifyOtpSetupResponse {
    private boolean otpEnabled;
    private String message;

    public boolean isOtpEnabled() { return otpEnabled; }
    public void setOtpEnabled(boolean v) { this.otpEnabled = v; }

    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }
}
