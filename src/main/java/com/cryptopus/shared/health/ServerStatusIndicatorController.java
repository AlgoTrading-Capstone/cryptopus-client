package com.cryptopus.shared.health;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * Controller for the shared {@code ServerStatusIndicator.fxml} component.
 *
 * <p>Binds to {@link HealthService#statusProperty()} and renders:</p>
 * <ul>
 *   <li>A {@link Circle} with one of three color style classes.</li>
 *   <li>An opacity-pulse {@link Timeline} for CONNECTING / CONNECTED (none
 *       for DISCONNECTED).</li>
 *   <li>A human-friendly status {@link Label}.</li>
 * </ul>
 *
 * <p>Lifecycle: registers via a {@link WeakChangeListener} so the controller
 * becomes eligible for GC once {@code Router} swaps the scene root. A
 * secondary {@code sceneProperty()} listener on the root HBox stops the
 * running {@link Timeline} when the component is detached, preventing a
 * dangling animation on an orphaned node.</p>
 */
public class ServerStatusIndicatorController {

    private static final String DOT_RED    = "status-dot--red";
    private static final String DOT_YELLOW = "status-dot--yellow";
    private static final String DOT_GREEN  = "status-dot--green";

    @FXML private HBox   root;
    @FXML private Circle statusDot;
    @FXML private Label  statusLabel;

    private Timeline pulse;

    // Kept as a field so the WeakChangeListener in HealthService has a strong
    // referent held by the controller (and only the controller).
    private ChangeListener<HealthStatus> statusListener;

    @FXML
    private void initialize() {
        // Defensive: FXMLLoader should inject root via fx:id, but fall back to
        // walking the parent of any injected child if an older FXML variant
        // omits the id.
        if (root == null && statusDot != null && statusDot.getParent() instanceof HBox hb) {
            root = hb;
        }

        statusListener = (obs, oldVal, newVal) -> applyStatus(newVal);
        HealthService.get()
                .statusProperty()
                .addListener(new WeakChangeListener<>(statusListener));

        // Stop the animation when this component is detached from any scene.
        if (root != null) {
            root.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene == null && pulse != null) {
                    pulse.stop();
                }
            });
        }

        // Apply the current status immediately; no change event would fire
        // otherwise and the UI would stay blank until the next probe.
        applyStatus(HealthService.get().getStatus());
    }

    private void applyStatus(HealthStatus s) {
        if (s == null) s = HealthStatus.CONNECTING;

        statusDot.getStyleClass().removeAll(DOT_RED, DOT_YELLOW, DOT_GREEN);

        if (pulse != null) {
            pulse.stop();
            pulse = null;
        }
        statusDot.setOpacity(1.0);

        switch (s) {
            case CONNECTED -> {
                statusDot.getStyleClass().add(DOT_GREEN);
                statusLabel.setText("Connected to server");
                pulse = buildPulse(Duration.millis(900));
                pulse.play();
            }
            case CONNECTING -> {
                statusDot.getStyleClass().add(DOT_YELLOW);
                statusLabel.setText("Connecting to server…");
                pulse = buildPulse(Duration.millis(300));
                pulse.play();
            }
            case DISCONNECTED -> {
                statusDot.getStyleClass().add(DOT_RED);
                statusLabel.setText("Disconnected from server");
            }
        }
    }

    /**
     * Builds an indefinite opacity-pulse Timeline: 1.0 → 0.35 → 1.0 with the
     * given half-period (so full period is {@code 2 * halfPeriod}).
     */
    private Timeline buildPulse(Duration halfPeriod) {
        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(statusDot.opacityProperty(), 1.0)),
                new KeyFrame(halfPeriod,
                        new KeyValue(statusDot.opacityProperty(), 0.35)),
                new KeyFrame(halfPeriod.multiply(2),
                        new KeyValue(statusDot.opacityProperty(), 1.0))
        );
        tl.setCycleCount(Animation.INDEFINITE);
        return tl;
    }
}
