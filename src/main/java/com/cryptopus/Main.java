package com.cryptopus;

import com.cryptopus.auth.AuthService;
import com.cryptopus.auth.SessionManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

/**
 * Entry point of the Cryptopus Client application.
 * Responsible for initializing and configuring the primary stage.
 */
public class Main extends Application {

    // Minimum window dimensions to ensure usability across different screen sizes
    private static final double MIN_WIDTH = 1400;
    private static final double MIN_HEIGHT = 850;

    @Override
    public void start(Stage primaryStage) throws IOException {
        // Initialize the shared auth service so it registers its token-refresh hook
        // on the ApiClient before any controller issues HTTP calls.
        AuthService.get();

        // Load the initial login screen from FXML
        Parent root = FXMLLoader.load(
                getClass().getResource("/com/cryptopus/pages/Login.fxml")
        );

        // Initial scene size (used as fallback before maximization)
        Scene scene = new Scene(root, 1200, 800, false, SceneAntialiasing.BALANCED);

        loadAppFonts();

        // Apply global stylesheet
        scene.getStylesheets().add(
                getClass().getResource("/com/cryptopus/global.css").toExternalForm()
        );

        // Set window title
        primaryStage.setTitle("Cryptopus");

        // Set application icon
        primaryStage.getIcons().add(
                new Image(getClass().getResourceAsStream("/com/cryptopus/assets/icons/app-icon.png"))
        );

        // Enforce minimum window dimensions
        primaryStage.setMinWidth(MIN_WIDTH);
        primaryStage.setMinHeight(MIN_HEIGHT);

        // Start application in maximized mode
        primaryStage.setMaximized(true);

        // Attach scene to stage
        primaryStage.setScene(scene);

        // Display the window
        primaryStage.show();
    }

    /**
     * Loads all application fonts from the resources folder.
     */
    private void loadAppFonts() {
        final String basePath = "/com/cryptopus/assets/fonts/";

        Font.loadFont(getClass().getResourceAsStream(basePath + "conthrax-sb.ttf"), 14);
        Font.loadFont(getClass().getResourceAsStream(basePath + "ClashGrotesk-Extralight.otf"), 14);
        Font.loadFont(getClass().getResourceAsStream(basePath + "ClashGrotesk-Light.otf"), 14);
        Font.loadFont(getClass().getResourceAsStream(basePath + "ClashGrotesk-Regular.otf"), 14);
        Font.loadFont(getClass().getResourceAsStream(basePath + "ClashGrotesk-Medium.otf"), 14);
        Font.loadFont(getClass().getResourceAsStream(basePath + "ClashGrotesk-Semibold.otf"), 14);
        Font.loadFont(getClass().getResourceAsStream(basePath + "ClashGrotesk-Bold.otf"), 14);
    }

    /**
     * Invoked by JavaFX when the application is shutting down.
     * Wipes every in-memory authentication artifact so no token outlives the process.
     */
    @Override
    public void stop() {
        SessionManager.get().clear();
    }

    public static void main(String[] args) {
        launch();
    }
}