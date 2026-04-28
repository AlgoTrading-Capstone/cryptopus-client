package com.cryptopus;

import com.cryptopus.auth.AuthService;
import com.cryptopus.auth.SessionManager;
import com.cryptopus.nav.Page;
import com.cryptopus.nav.Router;
import com.cryptopus.pages.SignupStep2Controller;
import com.cryptopus.pages.SignupStep3Controller;
import com.cryptopus.shared.health.HealthService;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;

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

        // Start the background server-health poller so the shared traffic-light
        // indicator on the Login / Signup pages has fresh state before render.
        HealthService.get().start();

        // Load fonts before any FXML is parsed so its styles resolve correctly.
        loadAppFonts();

        // Create the primary scene with a throwaway placeholder root; the
        // Router immediately replaces it with the real login page below.
        Scene scene = new Scene(new StackPane(), 1200, 800, false, SceneAntialiasing.BALANCED);

        // Apply the global stylesheet before the first page loads so its
        // styles are available on initial render.
        scene.getStylesheets().add(
                getClass().getResource("/com/cryptopus/global.css").toExternalForm()
        );

        // Bind the router to the primary scene
        Router.get().init(scene);

        // --- DEV SHORTCUT ---

        //Router.get().goTo(Page.LOGIN);
        //Router.get().goTo(Page.SIGNUP_STEP_1);
        //Router.get().goTo(Page.SIGNUP_STEP_2, (SignupStep2Controller c) -> c.setEmail("dev@cryptopus.local"));
        Router.get().goTo(Page.SIGNUP_STEP_3, (SignupStep3Controller c) -> c.setEmail("dev@cryptopus.local"));

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
        HealthService.get().shutdown();
        SessionManager.get().clear();
    }

    public static void main(String[] args) {
        launch();
    }
}