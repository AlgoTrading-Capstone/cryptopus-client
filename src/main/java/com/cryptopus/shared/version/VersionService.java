package com.cryptopus.shared.version;

import com.cryptopus.config.ApiConfig;
import com.cryptopus.net.ApiClient;
import com.cryptopus.shared.version.dto.VersionResponse;

import java.util.concurrent.CompletableFuture;

/**
 * Fetches the server version from {@code GET /api/version}.
 *
 * <p>The server version is effectively static per backend deployment, so the
 * result of the first successful call is cached for the lifetime of the
 * process. Failures are not cached; callers may retry.</p>
 */
public final class VersionService {

    private static final VersionService INSTANCE = new VersionService();

    public static VersionService get() {
        return INSTANCE;
    }

    private final ApiClient api = ApiClient.get();
    private volatile String cachedServerVersion;

    private VersionService() {
    }

    /**
     * Returns the server version string. On success the value is cached; on
     * failure the future completes exceptionally with the original
     * {@code ApiException}.
     */
    public CompletableFuture<String> fetchServerVersion() {
        String cached = cachedServerVersion;
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return api.get(ApiConfig.SYSTEM_VERSION, VersionResponse.class, false)
                .thenApply(resp -> {
                    String v = (resp == null) ? null : resp.getVersion();
                    if (v != null && !v.isBlank()) {
                        cachedServerVersion = v;
                    }
                    return v;
                });
    }
}
