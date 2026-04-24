package com.cryptopus.shared.health.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO mirroring the simple {@code /api/health} endpoint body:
 * <pre>{@code {"api":"UP","database":"UP","redis":"UP"}}</pre>
 *
 * <p>Intentionally plain; {@link com.cryptopus.shared.health.HealthService}
 * parses the JSON manually with null-safe {@code path(...)} lookups, but this
 * class is kept for documentation, admin-screen reuse and potential typed
 * deserialization later.</p>
 */
public final class HealthResponse {

    @JsonProperty("api")      private String api;
    @JsonProperty("database") private String database;
    @JsonProperty("redis")    private String redis;

    public String getApi()      { return api; }
    public String getDatabase() { return database; }
    public String getRedis()    { return redis; }

    public void setApi(String api)           { this.api = api; }
    public void setDatabase(String database) { this.database = database; }
    public void setRedis(String redis)       { this.redis = redis; }
}
