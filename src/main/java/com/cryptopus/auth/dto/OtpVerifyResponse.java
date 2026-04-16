package com.cryptopus.auth.dto;

/** {@code data} block returned by POST /api/auth/verify-otp. */
public class OtpVerifyResponse {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private String userId;

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String v) { this.accessToken = v; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String v) { this.refreshToken = v; }

    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long v) { this.expiresIn = v; }

    public String getUserId() { return userId; }
    public void setUserId(String v) { this.userId = v; }
}
