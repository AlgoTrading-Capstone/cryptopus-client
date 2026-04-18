package com.cryptopus.nav;

/**
 * Enumeration of all navigable application pages.
 *
 * <p>Each entry holds the absolute classpath location of its FXML file.
 * A {@code null} FXML path indicates a page that is planned but not yet
 * implemented; attempting to navigate to it will fail fast in {@link Router}.</p>
 */
public enum Page {

    LOGIN("/com/cryptopus/pages/Login.fxml"),
    SIGNUP_STEP_1("/com/cryptopus/pages/SignupStep1.fxml"),

    // Planned, not yet implemented. Kept here so the router contract is
    // already wired for the rest of the signup flow.
    SIGNUP_STEP_2(null);

    private final String fxmlPath;

    Page(String fxmlPath) {
        this.fxmlPath = fxmlPath;
    }

    public String fxmlPath() {
        return fxmlPath;
    }
}
