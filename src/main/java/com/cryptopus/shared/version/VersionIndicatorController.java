package com.cryptopus.shared.version;

import com.cryptopus.config.ClientVersion;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controller for the shared {@code VersionIndicator.fxml} component.
 *
 * <p>Displays the client version (from a classpath properties file, read
 * synchronously at startup) and the server version (fetched lazily from
 * {@link VersionService}). On fetch failure the server line shows
 * {@code "unavailable"}; no retry is performed.</p>
 */
public class VersionIndicatorController {

    @FXML private Label clientVersionLabel;
    @FXML private Label serverVersionLabel;

    @FXML
    private void initialize() {
        clientVersionLabel.setText("Client version: " + ClientVersion.get());
        serverVersionLabel.setText("Server version: fetching...");

        VersionService.get().fetchServerVersion()
                .whenComplete((version, err) -> Platform.runLater(() -> {
                    if (err != null || version == null || version.isBlank()) {
                        serverVersionLabel.setText("Server version: unavailable");
                    } else {
                        serverVersionLabel.setText("Server version: " + version);
                    }
                }));
    }
}
