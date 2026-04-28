package com.cryptopus.nav;

import com.cryptopus.shared.modal.AppShell;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.Consumer;

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
    private AppShell shell;

    private Router() {
    }

    /**
     * Binds the router to the primary {@link Scene}. Must be called once,
     * before any call to {@link #goTo(Page)}.
     *
     * <p>The router internally constructs an {@link AppShell} and installs
     * it as the scene root so that page navigations swap the shell's content
     * layer (preserving the modal overlay layer between transitions).</p>
     */
    public Router init(Scene scene) {
        this.scene = Objects.requireNonNull(scene, "scene");
        this.shell = new AppShell();
        scene.setRoot(shell);
        return this;
    }

    /**
     * Returns the modal overlay layer owned by the {@link AppShell}. Used by
     * {@code ModalService} to attach/detach the active modal dialog.
     */
    public StackPane modalLayer() {
        ensureInitialized();
        return shell.modalLayer();
    }

    /**
     * Returns the content layer owned by the {@link AppShell}. Used by
     * {@code ModalService} to apply blur / disable the page behind a modal.
     */
    public StackPane contentHolder() {
        ensureInitialized();
        return shell.contentHolder();
    }

    private void ensureInitialized() {
        if (scene == null || shell == null) {
            throw new IllegalStateException("Router.init(scene) must be called first.");
        }
    }

    /**
     * Loads the FXML for the given page and installs it as the scene root.
     * Runs synchronously on the JavaFX application thread.
     */
    public void goTo(Page page) {
        goTo(page, c -> { /* no controller initialization needed */ });
    }

    /**
     * Loads the FXML for {@code page}, invokes {@code onControllerReady} with
     * the freshly-created controller (before the root is attached to the
     * scene, so side-effects like focus requests happen only after the page is
     * actually visible would need deferring), then swaps in the new root.
     *
     * <p>This is the standard JavaFX parameter-passing mechanism: the caller
     * pushes data onto the next page's controller in-line with navigation,
     * so transient flow data never needs to live in module-level state.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     *   Router.get().goTo(Page.SIGNUP_STEP_2,
     *       (SignupStep2Controller c) -> c.setEmail(email));
     * }</pre>
     */
    public <C> void goTo(Page page, Consumer<C> onControllerReady) {
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(onControllerReady, "onControllerReady");
        ensureInitialized();
        String path = page.fxmlPath();
        if (path == null) {
            throw new IllegalStateException(
                    "Page " + page + " has no FXML associated with it yet.");
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                    Objects.requireNonNull(
                            Router.class.getResource(path),
                            "FXML resource not found: " + path));
            Parent root = loader.load();
            @SuppressWarnings("unchecked")
            C controller = (C) loader.getController();
            onControllerReady.accept(controller);
            shell.contentHolder().getChildren().setAll(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load FXML for page " + page, e);
        }
    }
}
