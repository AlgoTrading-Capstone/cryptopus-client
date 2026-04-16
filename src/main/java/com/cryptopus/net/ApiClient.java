package com.cryptopus.net;

import com.cryptopus.auth.SessionManager;
import com.cryptopus.config.ApiConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Shared HTTP layer for all backend calls.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Serialize request bodies and deserialize the standard {@code {status, data}} envelope.</li>
 *   <li>Attach {@code Authorization: Bearer &lt;token&gt;} when the call is authenticated.</li>
 *   <li>Translate non-2xx responses and I/O failures into a typed {@link ApiException} hierarchy.</li>
 *   <li>Transparently refresh the access token once on {@code 401} for authenticated requests.</li>
 * </ul>
 *
 * <p>All methods are non-blocking and return {@link CompletableFuture}. Callers are responsible
 * for marshalling UI updates back onto the JavaFX thread (e.g. via {@code Platform.runLater}).</p>
 */
public final class ApiClient {

    private static final ApiClient INSTANCE = new ApiClient();

    public static ApiClient get() {
        return INSTANCE;
    }

    private final HttpClient http;
    private final ObjectMapper mapper;

    /**
     * Hook used to refresh the access token on 401. Registered by {@code AuthService}
     * to avoid a circular dependency between the net and auth layers.
     */
    private volatile Supplier<CompletableFuture<Void>> tokenRefresher;

    private ApiClient() {
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(ApiConfig.CONNECT_TIMEOUT)
                .build();
        this.mapper = JsonMapper.get();
    }

    /**
     * Register the refresh callback. Calling it should refresh the access token
     * in {@link SessionManager} or complete exceptionally if refresh is impossible.
     */
    public void setTokenRefresher(Supplier<CompletableFuture<Void>> refresher) {
        this.tokenRefresher = refresher;
    }

    // --- Public API --------------------------------------------------------

    public <T> CompletableFuture<T> post(String path, Object body, Class<T> dataType, boolean authenticated) {
        return send("POST", path, body, dataType, authenticated, true);
    }

    public <T> CompletableFuture<T> get(String path, Class<T> dataType, boolean authenticated) {
        return send("GET", path, null, dataType, authenticated, true);
    }

    // --- Internals ---------------------------------------------------------

    private <T> CompletableFuture<T> send(String method,
                                          String path,
                                          Object body,
                                          Class<T> dataType,
                                          boolean authenticated,
                                          boolean allowRefreshRetry) {
        HttpRequest request;
        try {
            request = buildRequest(method, path, body, authenticated);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(
                    new ApiException.UnexpectedResponseException("Failed to build request.", -1, e));
        }

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((resp, err) -> handleResult(resp, err))
                .thenCompose(result -> {
                    // 401 on authenticated request → try refresh exactly once.
                    if (result.unauthorized && authenticated && allowRefreshRetry && tokenRefresher != null) {
                        return tokenRefresher.get()
                                .thenCompose(v -> send(method, path, body, dataType, true, false))
                                .exceptionallyCompose(refreshErr -> CompletableFuture.failedFuture(
                                        new ApiException.UnauthorizedException(
                                                "Your session has expired. Please log in again.",
                                                result.serverError)));
                    }
                    if (result.failure != null) {
                        return CompletableFuture.failedFuture(result.failure);
                    }
                    return CompletableFuture.completedFuture(parseData(result.body, result.status, dataType));
                });
    }

    private HttpRequest buildRequest(String method, String path, Object body, boolean authenticated) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.BASE_URL + path))
                .timeout(ApiConfig.REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");

        if (authenticated) {
            String token = SessionManager.get().getAccessToken();
            if (token == null || token.isBlank()) {
                throw new ApiException.UnauthorizedException("Not authenticated.", null);
            }
            b.header("Authorization", "Bearer " + token);
        }

        HttpRequest.BodyPublisher publisher = (body == null)
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(body), StandardCharsets.UTF_8);

        return switch (method) {
            case "POST"   -> b.POST(publisher).build();
            case "PUT"    -> b.PUT(publisher).build();
            case "DELETE" -> b.DELETE().build();
            default       -> b.GET().build();
        };
    }

    /** Intermediate result struct to simplify the {@code handle→thenCompose} chain. */
    private static final class Result {
        int status;
        String body;
        String serverError;
        boolean unauthorized;
        ApiException failure;
    }

    private Result handleResult(HttpResponse<String> resp, Throwable err) {
        Result r = new Result();
        if (err != null) {
            r.failure = mapThrowable(err);
            return r;
        }
        r.status = resp.statusCode();
        r.body = resp.body();

        if (r.status >= 200 && r.status < 300) {
            return r;
        }

        r.serverError = extractServerError(r.body);

        switch (r.status) {
            case 400 -> r.failure = new ApiException.ValidationException(
                    friendlyOr(r.serverError, "Invalid request."), r.serverError);
            case 401 -> {
                r.unauthorized = true;
                r.failure = new ApiException.UnauthorizedException(
                        friendlyOr(r.serverError, "Unauthorized."), r.serverError);
            }
            case 404 -> r.failure = new ApiException.NotFoundException(
                    friendlyOr(r.serverError, "Not found."), r.serverError);
            case 409 -> r.failure = new ApiException.ConflictException(
                    friendlyOr(r.serverError, "Conflict."), r.serverError);
            default -> {
                if (r.status >= 500) {
                    r.failure = new ApiException.ServerException(
                            "The server encountered an error. Please try again later.",
                            r.status, r.serverError);
                } else {
                    r.failure = new ApiException.UnexpectedResponseException(
                            "Unexpected response from server.", r.status, null);
                }
            }
        }
        return r;
    }

    private <T> T parseData(String body, int status, Class<T> dataType) {
        if (dataType == Void.class || body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode data = root.has("data") ? root.get("data") : root;
            return mapper.readerFor(TypeFactory.defaultInstance().constructType(dataType))
                    .readValue(data);
        } catch (Exception e) {
            throw new ApiException.UnexpectedResponseException(
                    "Could not parse server response.", status, e);
        }
    }

    private String extractServerError(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode root = mapper.readTree(body);
            if (root.has("error") && root.get("error").isTextual()) {
                return root.get("error").asText();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    private ApiException mapThrowable(Throwable err) {
        Throwable t = (err instanceof CompletionException ce && ce.getCause() != null) ? ce.getCause() : err;
        if (t instanceof ApiException ae) return ae;
        if (t instanceof HttpTimeoutException) {
            return new ApiException.NetworkException(
                    "The server took too long to respond. Please try again.", t);
        }
        if (t instanceof ConnectException) {
            return new ApiException.NetworkException(
                    "Cannot reach the server. Please check your connection.", t);
        }
        return new ApiException.NetworkException(
                "Network error: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()), t);
    }

    private static String friendlyOr(String serverError, String fallback) {
        return (serverError == null || serverError.isBlank()) ? fallback : serverError;
    }
}
