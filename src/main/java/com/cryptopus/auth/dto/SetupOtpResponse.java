package com.cryptopus.auth.dto;

/** {@code data} block returned by POST /api/auth/setup-otp (200). */
public class SetupOtpResponse {
    private String otpSecret;
    private String qrCodeUrl;
    private String message;

    public String getOtpSecret() { return otpSecret; }
    public void setOtpSecret(String v) { this.otpSecret = v; }

    public String getQrCodeUrl() { return qrCodeUrl; }
    public void setQrCodeUrl(String v) { this.qrCodeUrl = v; }

    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }
}
