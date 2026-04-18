package com.cryptopus.auth.dto;

/** {@code data} block returned by POST /api/auth/register (201). */
public class RegisterResponse {
    private String userId;
    private String email;
    private String createdAt;

    public String getUserId() { return userId; }
    public void setUserId(String v) { this.userId = v; }

    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String v) { this.createdAt = v; }
}
