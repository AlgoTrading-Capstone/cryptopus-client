package com.cryptopus.data;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Canonical list of country display names used anywhere in the app.
 *
 * <p>Built once at class-load time from {@link Locale#getISOCountries()} so we
 * don't ship a data file and don't depend on any external service. Display
 * names are resolved in {@link Locale#ENGLISH} so the list is identical
 * regardless of the user's system locale (the UI is English-first).</p>
 */
public final class Countries {

    /** Sorted, deduplicated, immutable list of country display names. */
    public static final List<String> ALL;

    static {
        ALL = Arrays.stream(Locale.getISOCountries())
                .map(code -> new Locale.Builder().setRegion(code).build()
                        .getDisplayCountry(Locale.ENGLISH))
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toUnmodifiableList());
    }

    private Countries() {}

    /** Returns {@code true} iff {@code name} is an exact match for a known country. */
    public static boolean isKnown(String name) {
        return name != null && ALL.contains(name.trim());
    }
}
