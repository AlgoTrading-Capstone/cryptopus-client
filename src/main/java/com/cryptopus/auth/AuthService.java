package com.cryptopus.auth;

import com.cryptopus.auth.dto.LoginRequest;
import com.cryptopus.auth.dto.LoginResponse;
import com.cryptopus.auth.dto.OtpVerifyRequest;
import com.cryptopus.auth.dto.OtpVerifyResponse;
import com.cryptopus.auth.dto.RefreshRequest;
import com.cryptopus.auth.dto.RefreshResponse;
import com.cryptopus.auth.dto.RegisterRequest;
import com.cryptopus.auth.dto.RegisterResponse;
import com.cryptopus.config.ApiConfig;
import com.cryptopus.net.ApiClient;
import com.cryptopus.net.ApiException;

import java.util.concurrent.CompletableFuture;

/**
 * High-level, feature-focused facade for all auth-related endpoints.
 * Controllers should depend on this class, not on {@link ApiClient} directly.
 *
 * <p>Side-effects: on successful login / OTP / refresh, tokens are written into
 * {@link SessionManager}. Registers itself as the refresh provider on the
 * shared {@link ApiClient} so any authenticated endpoint benefits from
 * transparent 401 recovery.</p>
 */
public final class AuthService {

    private static final AuthService INSTANCE = new AuthService();

    public static AuthService get() {
        return INSTANCE;
    }

    private final ApiClient api = ApiClient.get();
    private final SessionManager session = SessionManager.get();

    private AuthService() {
        api.setTokenRefresher(this::refresh);
    }

    public CompletableFuture<LoginResponse> login(String email, String password) {
        return api.post(ApiConfig.AUTH_LOGIN,
                        new LoginRequest(email, password),
                        LoginResponse.class,
                        false)
                .thenApply(resp -> {
                    if (resp != null && resp.getTemporarySessionId() != null) {
                        session.setTemporarySessionId(resp.getTemporarySessionId());
                    }
                    return resp;
                });
    }

    public CompletableFuture<OtpVerifyResponse> verifyOtp(String temporarySessionId, String otpCode) {
        return api.post(ApiConfig.AUTH_VERIFY_OTP,
                        new OtpVerifyRequest(temporarySessionId, otpCode),
                        OtpVerifyResponse.class,
                        false)
                .thenApply(resp -> {
                    if (resp != null && resp.getAccessToken() != null) {
                        session.setSession(
                                resp.getAccessToken(),
                                resp.getRefreshToken(),
                                resp.getUserId(),
                                resp.getExpiresIn());
                    }
                    return resp;
                });
    }

    /**
     * Refreshes the current access token using the stored refresh token.
     * Returns a {@code CompletableFuture<Void>} so it can be plugged into
     * {@link ApiClient#setTokenRefresher}.
     */
    public CompletableFuture<Void> refresh() {
        String refreshToken = session.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            return CompletableFuture.failedFuture(
                    new ApiException.UnauthorizedException("No refresh token available.", null));
        }
        return api.post(ApiConfig.AUTH_REFRESH,
                        new RefreshRequest(refreshToken),
                        RefreshResponse.class,
                        false)
                .thenAccept(resp -> {
                    if (resp != null && resp.getAccessToken() != null) {
                        session.updateAccessToken(resp.getAccessToken(), resp.getExpiresIn());
                    } else {
                        throw new ApiException.UnauthorizedException(
                                "Could not refresh session.", null);
                    }
                });
    }

    /**
     * Registers a new user account. This endpoint is unauthenticated and
     * returns the server-assigned {@code user_id} on 201. Tokens are not
     * issued here — the client continues through the rest of the signup
     * flow and logs in separately.
     */
    public CompletableFuture<RegisterResponse> register(RegisterRequest request) {
        return api.post(ApiConfig.AUTH_REGISTER,
                        request,
                        RegisterResponse.class,
                        false);
    }

    public void logout() {
        session.clear();
    }
}
