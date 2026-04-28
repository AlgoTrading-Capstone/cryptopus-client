package com.cryptopus.auth.dto;

/**
 * {@code data} block returned by POST /api/auth/login.
 *
 * <p>The shape is identical for all three response cases; the client routes on
 * the boolean flags:</p>
 * <ul>
 *   <li>{@code email_verified=false} → user must finish Signup Step 2.</li>
 *   <li>{@code email_verified=true, otp_verified=false} → user must finish Signup Step 3.</li>
 *   <li>{@code email_verified=true, otp_verified=true} → proceed to login OTP verification
 *       using {@code temporary_session_id}.</li>
 * </ul>
 */
public class LoginResponse {
    private boolean emailVerified;
    private boolean otpVerified;
    private String temporarySessionId;
    private String message;

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean v) { this.emailVerified = v; }

    public boolean isOtpVerified() { return otpVerified; }
    public void setOtpVerified(boolean v) { this.otpVerified = v; }

    public String getTemporarySessionId() { return temporarySessionId; }
    public void setTemporarySessionId(String v) { this.temporarySessionId = v; }

    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }
}
