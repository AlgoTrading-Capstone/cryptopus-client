package com.cryptopus.shared.modal;

import com.cryptopus.nav.Router;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Singleton service that mounts a custom modal dialog into the application
 * shell's modal layer (see {@link AppShell}).
 *
 * <p>While a modal is showing:</p>
 * <ul>
 *   <li>The page below is blurred and disabled, blocking keyboard/focus.</li>
 *   <li>The backdrop consumes mouse events so clicks never leak through.</li>
 *   <li>{@code ESC} is consumed (modal is non-dismissible by default).</li>
 *   <li>{@code Enter} fires the primary button (it is the default button).</li>
 * </ul>
 *
 * <p>Only one modal can be active at a time; calling {@link #show} while one
 * is already visible logs a warning and is a no-op.</p>
 */
public final class ModalService {

    private static final Logger LOG = Logger.getLogger(ModalService.class.getName());

    private static final String MODAL_FXML = "/com/cryptopus/shared/ModalDialog.fxml";
    private static final Duration OPEN_DURATION = Duration.millis(340);
    private static final Duration CLOSE_DURATION = Duration.millis(220);
    private static final double BLUR_RADIUS = 10;
    private static final double CARD_START_SCALE = 0.92;

    /** easeOutCubic — soft deceleration that reads as "placed" not "snapped". */
    private static final Interpolator EASE_OUT_CUBIC =
            Interpolator.SPLINE(0.33, 1.0, 0.68, 1.0);

    private static final ModalService INSTANCE = new ModalService();

    public static ModalService get() {
        return INSTANCE;
    }

    private Parent activeRoot;

    private ModalService() {
    }

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    /**
     * Shows a modal with only a primary button. The {@code type} drives both
     * the icon and its entrance animation (pass {@link ModalType#NONE} for
     * an icon-less modal).
     */
    public void show(ModalType type,
                     String title,
                     String message,
                     String primaryText,
                     Runnable primaryAction) {
        show(type, title, message, primaryText, primaryAction, null, null);
    }

    /**
     * Shows a modal with a primary button and an optional secondary button.
     * When {@code secondaryText} is {@code null} or blank, the secondary
     * button is hidden and the layout adapts.
     *
     * <p>The {@code type} drives the icon ({@code success.png} / {@code
     * error.png} / {@code warning.png}) and its one-shot entrance animation
     * (see {@link IconAnimations}). Use {@link ModalType#NONE} for an
     * icon-less modal.</p>
     *
     * <p>Button actions receive no arguments and must call {@link #close()}
     * themselves if they want to dismiss the modal — this keeps the behavior
     * explicit at the call site (e.g. an action might navigate without
     * closing, or close before navigating).</p>
     */
    public void show(ModalType type,
                     String title,
                     String message,
                     String primaryText,
                     Runnable primaryAction,
                     String secondaryText,
                     Runnable secondaryAction) {
        Objects.requireNonNull(primaryText, "primaryText");
        Objects.requireNonNull(primaryAction, "primaryAction");
        final ModalType effectiveType = (type == null) ? ModalType.NONE : type;

        if (isShowing()) {
            LOG.warning("ModalService.show called while a modal is already active; ignoring.");
            return;
        }

        FXMLLoader loader = new FXMLLoader(
                Objects.requireNonNull(
                        ModalService.class.getResource(MODAL_FXML),
                        "Modal FXML not found: " + MODAL_FXML));
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load modal FXML", e);
        }
        ModalDialogController controller = loader.getController();

        controller.setTitle(title);
        controller.setMessage(message);
        controller.setIcon(iconNameFor(effectiveType));
        controller.configurePrimary(primaryText, primaryAction);
        controller.configureSecondary(secondaryText, secondaryAction);

        // Put the icon into its pre-entrance state synchronously, before the
        // modal becomes visible, so it never flashes at rest for one frame.
        if (controller.iconView() != null
                && controller.iconView().isVisible()
                && controller.iconView().getImage() != null) {
            IconAnimations.prepare(effectiveType, controller.iconView());
        }

        // Block clicks on the backdrop (non-dismissible by default).
        controller.backdrop().addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);

        // Consume ESC to keep the modal non-dismissible by default.
        root.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) ev.consume();
        });

        // --- Mount into the shell's modal layer ---
        StackPane modalLayer = Router.get().modalLayer();
        StackPane content = Router.get().contentHolder();

        // Activate the overlay layer for input.
        modalLayer.setMouseTransparent(false);
        modalLayer.setPickOnBounds(true);
        modalLayer.getChildren().setAll(root);

        // Dim & block the page behind.
        content.setEffect(new GaussianBlur(BLUR_RADIUS));
        content.setDisable(true);

        activeRoot = root;

        // --- Entrance animation: fade backdrop, scale-up the card ---
        root.setOpacity(0);
        controller.card().setScaleX(CARD_START_SCALE);
        controller.card().setScaleY(CARD_START_SCALE);

        FadeTransition fade = new FadeTransition(OPEN_DURATION, root);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(EASE_OUT_CUBIC);

        ScaleTransition scale = new ScaleTransition(OPEN_DURATION, controller.card());
        scale.setFromX(CARD_START_SCALE);
        scale.setFromY(CARD_START_SCALE);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(EASE_OUT_CUBIC);

        ParallelTransition open = new ParallelTransition(fade, scale);
        open.setOnFinished(e -> {
            controller.primaryButton().requestFocus();
            // Icon entrance animation. Skipped when no icon is rendered.
            if (effectiveType != ModalType.NONE
                    && controller.iconView() != null
                    && controller.iconView().isVisible()
                    && controller.iconView().getImage() != null) {
                IconAnimations.play(effectiveType, controller.iconView());
            }
        });
        open.play();
    }

    /**
     * Maps a {@link ModalType} to the filename (without extension) of its
     * corresponding icon under {@code /com/cryptopus/assets/icons/}. Returns
     * {@code null} for {@link ModalType#NONE} so the controller collapses
     * the icon slot.
     */
    private static String iconNameFor(ModalType type) {
        return switch (type) {
            case SUCCESS -> "success";
            case ERROR   -> "error";
            case WARNING -> "warning";
            case NONE    -> null;
        };
    }

    /**
     * Closes the active modal (if any) with a brief fade-out, then clears the
     * blur/disable on the page behind.
     */
    public void close() {
        if (!isShowing()) return;

        Parent root = activeRoot;
        StackPane modalLayer = Router.get().modalLayer();
        StackPane content = Router.get().contentHolder();

        FadeTransition fade = new FadeTransition(CLOSE_DURATION, root);
        fade.setFromValue(root.getOpacity());
        fade.setToValue(0);
        fade.setInterpolator(EASE_OUT_CUBIC);
        fade.setOnFinished(e -> {
            modalLayer.getChildren().remove(root);
            modalLayer.setMouseTransparent(true);
            modalLayer.setPickOnBounds(false);
            content.setEffect(null);
            content.setDisable(false);
            activeRoot = null;
        });
        fade.play();
    }

    public boolean isShowing() {
        return activeRoot != null;
    }
}
