package com.cryptopus.shared.health;

/**
 * Coarse, user-facing view of the server connectivity state.
 *
 * <p>The enum deliberately collapses all server-side degradation modes
 * (api/db/redis DOWN, 5xx, timeouts, network errors) into a single
 * {@link #DISCONNECTED} state. Per-component details live on
 * {@link HealthSnapshot} for later admin tooling.</p>
 */
public enum HealthStatus {
    /** No confirmed reachability to the backend. */
    DISCONNECTED,
    /** A probe is in flight and we have no current confirmed good state. */
    CONNECTING,
    /** Last probe succeeded with api, database and redis all reporting {@code UP}. */
    CONNECTED
}
