package com.cryptopus.shared.modal;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * One-shot <em>entrance</em> animations for the modal icon.
 *
 * <p>Each variant has two entry points used together:</p>
 * <ol>
 *   <li>{@link #prepare(ModalType, Node)} — applied <strong>synchronously
 *       at mount</strong>, before the modal fades in. Puts the icon in its
 *       starting state (e.g. invisible + scaled down) so it never flashes
 *       at full opacity in place before the entrance plays.</li>
 *   <li>{@link #play(ModalType, Node)} — invoked when the modal's open
 *       transition finishes. Animates from the prepared state to the
 *       neutral resting state.</li>
 * </ol>
 */
public final class IconAnimations {

    // --- Easing curves ---

    /** easeOutQuart — strong deceleration. Good for shake decay. */
    private static final Interpolator EASE_OUT_QUART =
            Interpolator.SPLINE(0.25, 1.0, 0.5, 1.0);

    /** easeOutCubic — soft deceleration. Good for the rise phase of a pop. */
    private static final Interpolator EASE_OUT_CUBIC =
            Interpolator.SPLINE(0.33, 1.0, 0.68, 1.0);

    /** easeInOutSine — symmetric, breath-like. Good for settles and pulses. */
    private static final Interpolator EASE_IN_OUT_SINE =
            Interpolator.SPLINE(0.37, 0.0, 0.63, 1.0);

    private IconAnimations() {
    }

    // -----------------------------------------------------------------------
    //  Public dispatch — keep call-sites in ModalService trivial
    // -----------------------------------------------------------------------

    /**
     * Sets the icon's initial state for the upcoming entrance animation.
     * Must be called <em>before</em> the modal becomes visible to avoid the
     * icon flashing at rest for one frame.
     */
    public static void prepare(ModalType type, Node icon) {
        if (icon == null || type == null) return;
        switch (type) {
            case SUCCESS -> prepareSuccess(icon);
            case ERROR   -> prepareError(icon);
            case WARNING -> prepareWarning(icon);
            case NONE    -> { /* no-op */ }
        }
    }

    /** Plays the entrance animation. Pair with {@link #prepare}. */
    public static void play(ModalType type, Node icon) {
        if (icon == null || type == null) return;
        switch (type) {
            case SUCCESS -> playSuccess(icon);
            case ERROR   -> playError(icon);
            case WARNING -> playWarning(icon);
            case NONE    -> { /* no-op */ }
        }
    }

    // -----------------------------------------------------------------------
    //  SUCCESS — fade in + two-phase pop with overshoot
    // -----------------------------------------------------------------------

    private static void prepareSuccess(Node icon) {
        icon.setOpacity(0);
        icon.setScaleX(0.6);
        icon.setScaleY(0.6);
        icon.setTranslateX(0);
    }

    private static void playSuccess(Node icon) {
        FadeTransition fade = new FadeTransition(Duration.millis(360), icon);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition rise = new ScaleTransition(Duration.millis(360), icon);
        rise.setFromX(0.6);
        rise.setFromY(0.6);
        rise.setToX(1.08);
        rise.setToY(1.08);
        rise.setInterpolator(EASE_OUT_CUBIC);

        ScaleTransition settle = new ScaleTransition(Duration.millis(220), icon);
        settle.setFromX(1.08);
        settle.setFromY(1.08);
        settle.setToX(1.0);
        settle.setToY(1.0);
        settle.setInterpolator(EASE_IN_OUT_SINE);

        new ParallelTransition(fade, new SequentialTransition(rise, settle)).play();
    }

    // -----------------------------------------------------------------------
    //  ERROR — fade in + decaying horizontal shake
    // -----------------------------------------------------------------------

    private static void prepareError(Node icon) {
        icon.setOpacity(0);
        icon.setScaleX(1.0);
        icon.setScaleY(1.0);
        icon.setTranslateX(0);
    }

    private static void playError(Node icon) {
        FadeTransition fade = new FadeTransition(Duration.millis(220), icon);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        // Keyframes scaled to a 580ms total to match SUCCESS / WARNING.
        Timeline shake = new Timeline(
                new KeyFrame(Duration.ZERO,        new KeyValue(icon.translateXProperty(),  0, EASE_OUT_QUART)),
                new KeyFrame(Duration.millis(90),  new KeyValue(icon.translateXProperty(), -6, EASE_OUT_QUART)),
                new KeyFrame(Duration.millis(185), new KeyValue(icon.translateXProperty(),  6, EASE_OUT_QUART)),
                new KeyFrame(Duration.millis(275), new KeyValue(icon.translateXProperty(), -4, EASE_OUT_QUART)),
                new KeyFrame(Duration.millis(355), new KeyValue(icon.translateXProperty(),  4, EASE_OUT_QUART)),
                new KeyFrame(Duration.millis(430), new KeyValue(icon.translateXProperty(), -2, EASE_OUT_QUART)),
                new KeyFrame(Duration.millis(500), new KeyValue(icon.translateXProperty(),  2, EASE_OUT_QUART)),
                new KeyFrame(Duration.millis(580), new KeyValue(icon.translateXProperty(),  0, EASE_OUT_QUART))
        );
        shake.setCycleCount(1);

        new ParallelTransition(fade, shake).play();
    }

    // -----------------------------------------------------------------------
    //  WARNING — fade in, then blink twice (opacity-only, no scale)
    // -----------------------------------------------------------------------

    private static void prepareWarning(Node icon) {
        icon.setOpacity(0);
        icon.setScaleX(1.0);
        icon.setScaleY(1.0);
        icon.setTranslateX(0);
    }

    private static void playWarning(Node icon) {
        // Entrance fade-in (140ms) → blink (dim/restore) → blink again.
        // Pure opacity keeps it visually distinct from SUCCESS's scale pop.
        Timeline blink = new Timeline(
                new KeyFrame(Duration.ZERO,        new KeyValue(icon.opacityProperty(), 0.0,  EASE_IN_OUT_SINE)),
                new KeyFrame(Duration.millis(140), new KeyValue(icon.opacityProperty(), 1.0,  EASE_IN_OUT_SINE)),
                new KeyFrame(Duration.millis(250), new KeyValue(icon.opacityProperty(), 0.35, EASE_IN_OUT_SINE)),
                new KeyFrame(Duration.millis(360), new KeyValue(icon.opacityProperty(), 1.0,  EASE_IN_OUT_SINE)),
                new KeyFrame(Duration.millis(470), new KeyValue(icon.opacityProperty(), 0.35, EASE_IN_OUT_SINE)),
                new KeyFrame(Duration.millis(580), new KeyValue(icon.opacityProperty(), 1.0,  EASE_IN_OUT_SINE))
        );
        blink.setCycleCount(1);
        blink.play();
    }
}
