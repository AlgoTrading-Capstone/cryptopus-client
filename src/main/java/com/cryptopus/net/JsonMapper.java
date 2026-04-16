package com.cryptopus.net;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

/**
 * Singleton holder for the shared Jackson {@link ObjectMapper}.
 * Configured for snake_case wire format and lenient deserialization.
 */
public final class JsonMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonMapper() {
    }

    public static ObjectMapper get() {
        return MAPPER;
    }
}
