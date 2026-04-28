package com.cryptopus.shared.modal;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * Backs {@code ModalDialog.fxml}. Pure view-state binding: every method here
 * mutates a node property and nothing else. Lifecycle (mount, animate, dim,
 * dismiss) is owned by {@link ModalService}.
 */
public final class ModalDialogController {

    private static final String ICON_BASE_PATH = "/com/cryptopus/assets/icons/";
    private static final String ICON_EXTENSION = ".png";

    @FXML private StackPane backdrop;
    @FXML private VBox card;
    @FXML private HBox headerRow;
    @FXML private ImageView iconView;
    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private Button primaryButton;
    @FXML private Button secondaryButton;

    // -----------------------------------------------------------------------
    //  Configuration API (called by ModalService before mount)
    // -----------------------------------------------------------------------

    public void setTitle(String text) {
        titleLabel.setText(text == null ? "" : text);
    }

    public void setMessage(String text) {
        messageLabel.setText(text == null ? "" : text);
    }

    /**
     * Loads an icon from {@code /com/cryptopus/assets/icons/{name}.png}. When
     * {@code name} is {@code null} or blank, the icon slot collapses so the
     * header row has no leading empty space.
     */
    public void setIcon(String name) {
        if (name == null || name.isBlank()) {
            iconView.setImage(null);
            iconView.setVisible(false);
            iconView.setManaged(false);
            return;
        }
        String resourcePath = ICON_BASE_PATH + name + ICON_EXTENSION;
        var stream = getClass().getResourceAsStream(resourcePath);
        if (stream == null) {
            // Missing icon should not break the modal — degrade to no-icon.
            iconView.setImage(null);
            iconView.setVisible(false);
            iconView.setManaged(false);
            return;
        }
        iconView.setImage(new Image(stream));
        iconView.setVisible(true);
        iconView.setManaged(true);
    }

    public void configurePrimary(String text, Runnable action) {
        primaryButton.setText(text == null ? "" : text);
        primaryButton.setDefaultButton(true);
        primaryButton.setOnAction(e -> {
            if (action != null) action.run();
        });
    }

    public void configureSecondary(String text, Runnable action) {
        if (text == null || text.isBlank()) {
            secondaryButton.setVisible(false);
            secondaryButton.setManaged(false);
            secondaryButton.setOnAction(null);
            return;
        }
        secondaryButton.setText(text);
        secondaryButton.setVisible(true);
        secondaryButton.setManaged(true);
        secondaryButton.setOnAction(e -> {
            if (action != null) action.run();
        });
    }

    // -----------------------------------------------------------------------
    //  Accessors used by ModalService
    // -----------------------------------------------------------------------

    public StackPane backdrop() {
        return Objects.requireNonNull(backdrop, "backdrop not injected");
    }

    public VBox card() {
        return Objects.requireNonNull(card, "card not injected");
    }

    public Button primaryButton() {
        return primaryButton;
    }

    /**
     * Returns the icon view so {@code ModalService} can apply one-shot
     * animations to it. May be hidden/unmanaged when no icon was configured.
     */
    public ImageView iconView() {
        return iconView;
    }
}
