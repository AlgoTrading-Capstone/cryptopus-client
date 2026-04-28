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
 * One-shot icon animations used by {@link ModalService} to reinforce the
 * semantic type of a modal when it appears.
 *
 * <p>Animation design notes:</p>
 * <ul>
 *   <li>Durations live in the 280–420ms range. Shorter than ~250ms reads as
 *       "snap" / jitter; longer than ~500ms feels draggy for a passive cue.</li>
 *   <li>Easing uses cubic-bezier SPLINE curves (matching common web-animation
 *       practice: {@code easeOutBack}, {@code easeOutQuart},
 *       {@code easeInOutSine}) instead of JavaFX's built-in linear/EASE_OUT,
 *       which feel mechanical for expressive motion.</li>
 *   <li>Only transform/opacity properties are animated — never layout.</li>
 *   <li>Each animation resets the node to its neutral state on finish so a
 *       second opening of the same modal looks identical to the first.</li>
 * </ul>
 */
public final class IconAnimations {

    // --- Easing curves (cubic-bezier approximations of common web easings) ---
    //
    // Note: JavaFX's Interpolator.SPLINE requires control points in [0,1],
    // so true "back" / "elastic" curves (which use y > 1) cannot be expressed
    // here. Overshoot is instead produced by a two-phase ScaleTransition.

    /** easeOutQuart — strong deceleration. Good for shakes / quick exits. */
    private static final Interpolator EASE_OUT_QUART =
            Interpolator.SPLINE(0.25, 1.0, 0.5, 1.0);

    /** easeOutCubic — soft deceleration. Good for overshoot phase. */
    private static final Interpolator EASE_OUT_CUBIC =
            Interpolator.SPLINE(0.33, 1.0, 0.68, 1.0);

    /** easeInOutSine — symmetric, breath-like. Good for pulses and settles. */
    private static final Interpolator EASE_IN_OUT_SINE =
            Interpolator.SPLINE(0.37, 0.0, 0.63, 1.0);

    private IconAnimations() {
    }

    // -----------------------------------------------------------------------
    //  SUCCESS — single-phase pop with natural overshoot
    // -----------------------------------------------------------------------

    /**
     * Soft pop. Two-phase scale produces a natural overshoot (JavaFX's SPLINE
     * interpolator cannot express y > 1 curves like easeOutBack):
     *   phase 1 — 0.6 → 1.08, easeOutCubic (fast rise, soft arrival)
     *   phase 2 — 1.08 → 1.0, easeInOutSine (gentle settle)
     * Runs in parallel with a fade-in so the icon is visible throughout.
     * Total ~420ms.
     */
    public static void playSuccess(Node icon) {
        if (icon == null) return;

        icon.setOpacity(0);
        icon.setScaleX(0.6);
        icon.setScaleY(0.6);

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

        SequentialTransition pop = new SequentialTransition(rise, settle);
        new ParallelTransition(fade, pop).play();
    }

    // -----------------------------------------------------------------------
    //  ERROR — fade-in, then damped horizontal shake
    // -----------------------------------------------------------------------

    /**
     * Quick fade-in then a decaying horizontal shake ({@code ±6 → ±4 → ±2 → 0}).
     * The amplitude decay + easeOutQuart per keyframe produces a "settling"
     * feel rather than a harsh back-and-forth. Total ~420ms.
     */
    public static void playError(Node icon) {
        if (icon == null) return;

        icon.setOpacity(0);
        icon.setTranslateX(0);

        FadeTransition fade = new FadeTransition(Duration.millis(220), icon);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        Timeline shake = new Timeline(
                new KeyFrame(Duration.ZERO,        new KeyValue(icon.translateXProperty(),  0, EASE_OUT_QUART)),
                new KeyFrame(Duration.millis(80),  new KeyValue(icon.translateXProperty(), -6, EASE_OUT_QUART)),
                new KeyFrame(Duration.millis(165), new KeyValue(icon.translateXProperty(),  6, EASE_OUT_QUART)),
                new KeyFrame(Duration.millis(245), new KeyValue(icon.translateXProperty(), -4, EASE_OUT_QUART)),
                new KeyFrame(Duration.millis(320), new KeyValue(icon.translateXProperty(),  4, EASE_OUT_QUART)),
                new KeyFrame(Duration.millis(385), new KeyValue(icon.translateXProperty(), -2, EASE_OUT_QUART)),
                new KeyFrame(Duration.millis(450), new KeyValue(icon.translateXProperty(),  2, EASE_OUT_QUART)),
                new KeyFrame(Duration.millis(520), new KeyValue(icon.translateXProperty(),  0, EASE_OUT_QUART))
        );
        shake.setCycleCount(1);

        // Fade-in runs in parallel with the beginning of the shake so the
        // icon is already visible as the first sway starts — avoids the
        // "jerk then fade" effect.
        new ParallelTransition(fade, shake).play();
    }

    // -----------------------------------------------------------------------
    //  WARNING — gentle symmetric pulse
    // -----------------------------------------------------------------------

    /**
     * Slow, symmetric breath: 1.0 → 1.08 → 1.0 over ~420ms with easeInOutSine.
     * Reads as a calm "attention" cue rather than a jolt.
     */
    public static void playWarning(Node icon) {
        if (icon == null) return;

        icon.setScaleX(1.0);
        icon.setScaleY(1.0);

        ScaleTransition up = new ScaleTransition(Duration.millis(280), icon);
        up.setFromX(1.0);
        up.setFromY(1.0);
        up.setToX(1.08);
        up.setToY(1.08);
        up.setInterpolator(EASE_IN_OUT_SINE);

        ScaleTransition down = new ScaleTransition(Duration.millis(280), icon);
        down.setFromX(1.08);
        down.setFromY(1.08);
        down.setToX(1.0);
        down.setToY(1.0);
        down.setInterpolator(EASE_IN_OUT_SINE);

        new SequentialTransition(up, down).play();
    }
}
