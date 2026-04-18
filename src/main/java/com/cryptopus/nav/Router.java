package com.cryptopus.nav;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Minimal scene-root based navigator.
 *
 * <p>The {@link Router} owns the primary {@link Scene} and swaps its root
 * whenever the application navigates between pages. Controllers depend on
 * this singleton to switch states; they never load FXML directly.</p>
 *
 * <p>Typical usage:</p>
 * <pre>{@code
 *   // In Main:
 *   Router.get().init(scene).goTo(Page.LOGIN);
 *
 *   // In a controller:
 *   Router.get().goTo(Page.SIGNUP_STEP_1);
 * }</pre>
 */
public final class Router {

    private static final Router INSTANCE = new Router();

    public static Router get() {
        return INSTANCE;
    }

    private Scene scene;

    private Router() {
    }

    /**
     * Binds the router to the primary {@link Scene}. Must be called once,
     * before any call to {@link #goTo(Page)}.
     */
    public Router init(Scene scene) {
        this.scene = Objects.requireNonNull(scene, "scene");
        return this;
    }

    /**
     * Loads the FXML for the given page and installs it as the scene root.
     * Runs synchronously on the JavaFX application thread.
     */
    public void goTo(Page page) {
        Objects.requireNonNull(page, "page");
        if (scene == null) {
            throw new IllegalStateException("Router.init(scene) must be called before goTo(...).");
        }
        String path = page.fxmlPath();
        if (path == null) {
            throw new IllegalStateException(
                    "Page " + page + " has no FXML associated with it yet.");
        }
        try {
            Parent root = FXMLLoader.load(
                    Objects.requireNonNull(
                            Router.class.getResource(path),
                            "FXML resource not found: " + path));
            scene.setRoot(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load FXML for page " + page, e);
        }
    }
}
