package com.cryptopus.auth.dto;

/** {@code data} block returned by POST /api/auth/login. */
public class LoginResponse {
    private boolean otpRequired;
    private String temporarySessionId;
    private String message;

    public boolean isOtpRequired() { return otpRequired; }
    public void setOtpRequired(boolean v) { this.otpRequired = v; }

    public String getTemporarySessionId() { return temporarySessionId; }
    public void setTemporarySessionId(String v) { this.temporarySessionId = v; }

    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }
}
