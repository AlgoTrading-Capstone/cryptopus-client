package com.cryptopus.auth;

import java.time.Instant;

/**
 * Thread-safe, in-memory only holder for authentication state.
 *
 * <p>Holds the short-lived login {@code temporary_session_id}, the access + refresh
 * tokens after OTP verification, and associated metadata. Tokens are <b>never</b>
 * written to disk. Call {@link #clear()} on logout or app shutdown.</p>
 */
public final class SessionManager {

    private static final SessionManager INSTANCE = new SessionManager();

    public static SessionManager get() {
        return INSTANCE;
    }

    private SessionManager() {
    }

    private String temporarySessionId;
    private String accessToken;
    private String refreshToken;
    private String userId;
    private Instant accessTokenExpiresAt;

    // --- Temporary login session (between /login and /verify-otp) ---
    public synchronized void setTemporarySessionId(String id) { this.temporarySessionId = id; }
    public synchronized String getTemporarySessionId()         { return temporarySessionId; }

    // --- Authenticated session ---
    public synchronized void setSession(String accessToken,
                                        String refreshToken,
                                        String userId,
                                        long expiresInSeconds) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.accessTokenExpiresAt = Instant.now().plusSeconds(expiresInSeconds);
        // Temp session is consumed once OTP succeeds.
        this.temporarySessionId = null;
    }

    public synchronized void updateAccessToken(String accessToken, long expiresInSeconds) {
        this.accessToken = accessToken;
        this.accessTokenExpiresAt = Instant.now().plusSeconds(expiresInSeconds);
    }

    public synchronized String getAccessToken()           { return accessToken; }
    public synchronized String getRefreshToken()          { return refreshToken; }
    public synchronized String getUserId()                { return userId; }
    public synchronized Instant getAccessTokenExpiresAt() { return accessTokenExpiresAt; }

    public synchronized boolean isAuthenticated() {
        return accessToken != null && !accessToken.isBlank();
    }

    /** Wipe every piece of session state. Called on logout and on app shutdown. */
    public synchronized void clear() {
        this.temporarySessionId = null;
        this.accessToken = null;
        this.refreshToken = null;
        this.userId = null;
        this.accessTokenExpiresAt = null;
    }
}
