package com.cryptopus.shared.health;

import java.time.Instant;

/**
 * Immutable value object describing the outcome of a single health probe.
 *
 * <p>Exposed via {@link HealthService#snapshotProperty()} for future screens
 * (admin dashboard) that need the per-component breakdown. The top-level
 * traffic-light indicator does not read this; it binds to
 * {@link HealthService#statusProperty()} instead.</p>
 *
 * <p>Component flags use {@link Boolean} (not primitive {@code boolean}) so
 * {@code null} can unambiguously represent "unknown" — e.g. when the probe
 * failed at the network layer and we never saw a response body.</p>
 */
public final class HealthSnapshot {

    private final HealthStatus overall;
    private final Boolean apiUp;
    private final Boolean dbUp;
    private final Boolean redisUp;
    private final Instant lastProbeAt;
    private final String lastError;

    public HealthSnapshot(HealthStatus overall,
                          Boolean apiUp,
                          Boolean dbUp,
                          Boolean redisUp,
                          Instant lastProbeAt,
                          String lastError) {
        this.overall = overall;
        this.apiUp = apiUp;
        this.dbUp = dbUp;
        this.redisUp = redisUp;
        this.lastProbeAt = lastProbeAt;
        this.lastError = lastError;
    }

    public static HealthSnapshot initial() {
        return new HealthSnapshot(HealthStatus.CONNECTING, null, null, null, null, null);
    }

    public HealthStatus getOverall()      { return overall; }
    public Boolean      getApiUp()        { return apiUp; }
    public Boolean      getDbUp()         { return dbUp; }
    public Boolean      getRedisUp()      { return redisUp; }
    public Instant      getLastProbeAt()  { return lastProbeAt; }
    public String       getLastError()    { return lastError; }
}
