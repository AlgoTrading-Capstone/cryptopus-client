package com.cryptopus.auth.dto;

/** {@code data} block returned by POST /api/auth/refresh. */
public class RefreshResponse {
    private String accessToken;
    private long expiresIn;

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String v) { this.accessToken = v; }

    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long v) { this.expiresIn = v; }
}
