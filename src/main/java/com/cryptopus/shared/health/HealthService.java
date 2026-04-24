package com.cryptopus.shared.health;

import com.cryptopus.config.ApiConfig;
import com.cryptopus.net.JsonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Singleton background service that polls {@code GET /api/health} and exposes
 * a coarse JavaFX-observable status for the shared traffic-light indicator.
 *
 * <p>Key design points (see {@code server-status-indicator} plan for detail):</p>
 * <ul>
 *   <li>Owns its own {@link HttpClient} and {@link ScheduledExecutorService}
 *       so the liveness probe has a much tighter SLA than normal business
 *       calls made through {@code ApiClient} and is unaffected by the
 *       401/token-refresh retry path.</li>
 *   <li>Adaptive polling: {@value ApiConfig#HEALTH_POLL_CONNECTED} when
 *       healthy, {@value ApiConfig#HEALTH_POLL_DISCONNECTED} when recovering.</li>
 *   <li>Hysteresis: a healthy state requires 2 consecutive failures before
 *       flipping to {@link HealthStatus#DISCONNECTED} (first-ever failure
 *       from cold start is enough since there is no prior good state).</li>
 *   <li>Hard 2&nbsp;s {@link HttpRequest#timeout(java.time.Duration) request
 *       timeout} guarantees a probe cannot pin the UI on CONNECTING.</li>
 * </ul>
 *
 * <p>All {@link javafx.beans.property.Property} writes are marshalled onto
 * the JavaFX application thread via {@link Platform#runLater(Runnable)}.</p>
 */
public final class HealthService {

    private static final HealthService INSTANCE = new HealthService();

    public static HealthService get() {
        return INSTANCE;
    }

    // --- Observables (read on FX thread only) ---
    private final ReadOnlyObjectWrapper<HealthStatus>   status   =
            new ReadOnlyObjectWrapper<>(HealthStatus.CONNECTING);
    private final ReadOnlyObjectWrapper<HealthSnapshot> snapshot =
            new ReadOnlyObjectWrapper<>(HealthSnapshot.initial());

    // --- Wiring (owned by this service) ---
    private final HttpClient               http;
    private final ObjectMapper             json = JsonMapper.get();
    private ScheduledExecutorService       scheduler;

    // --- Internal state (touch on scheduler thread only) ---
    private HealthStatus                confirmed           = HealthStatus.CONNECTING;
    private int                         consecutiveFailures = 0;
    private boolean                     hasEverSucceeded    = false;
    private CompletableFuture<HttpResponse<String>> inFlight;
    private ScheduledFuture<?>          nextTick;
    private volatile boolean            running             = false;

    private HealthService() {
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(ApiConfig.HEALTH_CONNECT_TIMEOUT)
                .build();
    }

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    public ReadOnlyObjectProperty<HealthStatus> statusProperty() {
        return status.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<HealthSnapshot> snapshotProperty() {
        return snapshot.getReadOnlyProperty();
    }

    public HealthStatus getStatus() {
        return status.get();
    }

    /**
     * Idempotently start the polling loop. First probe fires with no delay so
     * the UI never stays on the initial {@code CONNECTING} for more than one
     * request timeout ({@value ApiConfig#HEALTH_REQUEST_TIMEOUT}).
     */
    public synchronized void start() {
        if (running) return;
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "health-probe");
            t.setDaemon(true);
            return t;
        });
        scheduleNext(0);
    }

    /**
     * Idempotently stop polling, cancel any in-flight probe and tear down the
     * scheduler. Safe to call during JavaFX application shutdown.
     */
    public synchronized void shutdown() {
        if (!running) return;
        running = false;
        if (nextTick != null) nextTick.cancel(false);
        if (inFlight != null) inFlight.cancel(true);
        if (scheduler != null) scheduler.shutdownNow();
    }

    // -----------------------------------------------------------------------
    //  Scheduler loop
    // -----------------------------------------------------------------------

    private void scheduleNext(long delayMs) {
        if (!running) return;
        nextTick = scheduler.schedule(this::tick, delayMs, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        if (!running) return;

        // Skip if the previous probe somehow outlived its timeout; the probe's
        // own HttpRequest.timeout should prevent this but we stay defensive.
        if (inFlight != null && !inFlight.isDone()) {
            scheduleNext(pollIntervalMs());
            return;
        }

        // Publish CONNECTING only during the cold-start window (before any
        // probe has resolved). Once we have a confirmed state — green or red —
        // we keep it steady until the next confirmed transition:
        //   * CONNECTED   stays green during in-flight probes (avoids yellow
        //                 blinking every 10 s during healthy operation).
        //   * DISCONNECTED stays red  during retry probes (avoids a 1 ms
        //                 yellow flicker when the server fast-fails, e.g.
        //                 connection-refused).
        if (confirmed == HealthStatus.CONNECTING) {
            publishStatus(HealthStatus.CONNECTING);
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(ApiConfig.BASE_URL + ApiConfig.SYSTEM_HEALTH))
                    .timeout(ApiConfig.HEALTH_REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
        } catch (Exception buildErr) {
            onProbeFailed("bad request: " + buildErr.getMessage());
            return;
        }

        inFlight = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        inFlight.whenComplete((resp, err) -> {
            // Callback may run on HttpClient's internal executor; re-marshal to
            // the scheduler thread so mutation of internal state stays single-
            // threaded (and so publishStatus's Platform.runLater is consistent).
            if (!running) return;
            scheduler.execute(() -> handleProbeResult(resp, err));
        });
    }

    private long pollIntervalMs() {
        return (confirmed == HealthStatus.CONNECTED
                ? ApiConfig.HEALTH_POLL_CONNECTED
                : ApiConfig.HEALTH_POLL_DISCONNECTED).toMillis();
    }

    // -----------------------------------------------------------------------
    //  State machine
    // -----------------------------------------------------------------------

    private void handleProbeResult(HttpResponse<String> resp, Throwable err) {
        if (err != null) {
            Throwable cause = (err instanceof CompletionException && err.getCause() != null)
                    ? err.getCause() : err;
            onProbeFailed(cause.getClass().getSimpleName()
                    + (cause.getMessage() == null ? "" : ": " + cause.getMessage()));
            return;
        }

        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            onProbeFailed("HTTP " + code);
            return;
        }

        Boolean apiUp, dbUp, redisUp;
        try {
            JsonNode root = json.readTree(resp.body());
            // The simple endpoint is flat; fall back to a potential 'data'
            // envelope so this keeps working if the backend ever wraps it.
            JsonNode node = root.has("api") ? root : root.path("data");
            apiUp   = parseFlag(node.path("api"));
            dbUp    = parseFlag(node.path("database"));
            redisUp = parseFlag(node.path("redis"));
        } catch (Exception parseErr) {
            onProbeFailed("parse error: " + parseErr.getMessage());
            return;
        }

        boolean allUp = Boolean.TRUE.equals(apiUp)
                     && Boolean.TRUE.equals(dbUp)
                     && Boolean.TRUE.equals(redisUp);

        if (allUp) {
            onProbeSucceeded(apiUp, dbUp, redisUp);
        } else {
            onProbeFailed("component down", apiUp, dbUp, redisUp);
        }
    }

    private void onProbeSucceeded(Boolean apiUp, Boolean dbUp, Boolean redisUp) {
        consecutiveFailures = 0;
        hasEverSucceeded = true;
        confirmed = HealthStatus.CONNECTED;
        publishStatus(HealthStatus.CONNECTED);
        publishSnapshot(HealthStatus.CONNECTED, apiUp, dbUp, redisUp, null);
        scheduleNext(pollIntervalMs());
    }

    private void onProbeFailed(String reason) {
        onProbeFailed(reason, null, null, null);
    }

    private void onProbeFailed(String reason, Boolean apiUp, Boolean dbUp, Boolean redisUp) {
        consecutiveFailures++;

        // Hysteresis: once CONNECTED, need 2 consecutive failures to flip red.
        // Before the first success we drop on the first failure to avoid
        // sitting on yellow indefinitely at startup when the server is down.
        boolean downgrade = !hasEverSucceeded || consecutiveFailures >= 2;
        if (downgrade) {
            confirmed = HealthStatus.DISCONNECTED;
            publishStatus(HealthStatus.DISCONNECTED);
            publishSnapshot(HealthStatus.DISCONNECTED, apiUp, dbUp, redisUp, reason);
        } else {
            // Still considered healthy; no visible change but record the
            // probe outcome on the snapshot for the admin screen.
            publishSnapshot(HealthStatus.CONNECTED, apiUp, dbUp, redisUp, reason);
        }
        scheduleNext(pollIntervalMs());
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    private static Boolean parseFlag(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String s = node.asText("").trim();
        if (s.isEmpty()) return null;
        return "UP".equalsIgnoreCase(s);
    }

    private void publishStatus(HealthStatus s) {
        runOnFx(() -> status.set(s));
    }

    private void publishSnapshot(HealthStatus overall, Boolean apiUp, Boolean dbUp,
                                 Boolean redisUp, String lastError) {
        HealthSnapshot snap = new HealthSnapshot(
                overall, apiUp, dbUp, redisUp, Instant.now(), lastError);
        runOnFx(() -> snapshot.set(snap));
    }

    private static void runOnFx(Runnable r) {
        try {
            Platform.runLater(r);
        } catch (IllegalStateException ignored) {
            // JavaFX toolkit not started yet or already shut down — harmless.
        }
    }
}
