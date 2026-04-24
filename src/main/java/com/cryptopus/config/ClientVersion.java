package com.cryptopus.config;

import java.io.InputStream;
import java.util.Properties;

/**
 * Static accessor for the client version string.
 *
 * <p>The value is loaded once from {@code /com/cryptopus/config/version.properties}
 * on the classpath. The file is expected to be overwritten by CI (e.g. GitHub
 * Actions) on every commit so the packaged application always ships with an
 * up-to-date identifier. If the file is missing or malformed we fall back to
 * {@code "unknown"} rather than failing startup.</p>
 */
public final class ClientVersion {

    private static final String RESOURCE_PATH = "/com/cryptopus/config/version.properties";
    private static final String PROPERTY_KEY  = "client.version";
    private static final String FALLBACK      = "unknown";

    private static final String VERSION = load();

    private ClientVersion() {
    }

    public static String get() {
        return VERSION;
    }

    private static String load() {
        try (InputStream in = ClientVersion.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) return FALLBACK;
            Properties props = new Properties();
            props.load(in);
            String v = props.getProperty(PROPERTY_KEY);
            return (v == null || v.isBlank()) ? FALLBACK : v.trim();
        } catch (Exception e) {
            return FALLBACK;
        }
    }
}
