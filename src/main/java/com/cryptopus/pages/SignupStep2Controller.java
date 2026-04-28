package com.cryptopus.pages;

import com.cryptopus.auth.AuthService;
import com.cryptopus.auth.dto.VerifyEmailResponse;
import com.cryptopus.nav.Page;
import com.cryptopus.nav.Router;
import com.cryptopus.net.ApiException;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;

import java.util.List;

/**
 * Controller for Signup Step 2 — Email Verification ({@code SignupStep2.fxml}).
 *
 * <p>Receives the target email via {@link #setEmail(String)} (invoked by
 * {@link Router#goTo(Page, java.util.function.Consumer)} from Step 1), so no
 * module-level state is required. The user enters a 6-character alphanumeric
 * code across six single-character boxes; entry auto-submits to the server
 * once all boxes are filled.</p>
 *
 * <p>A 10-minute countdown runs while the page is visible. On expiry the
 * boxes are cleared and locked until the user requests a new code via the
 * Resend link (which currently targets a TODO stub in {@link AuthService}).</p>
 */
public class SignupStep2Controller {

    // --- Constants -----------------------------------------------------------

    private static final int CODE_LENGTH = 6;
    private static final int COUNTDOWN_SECONDS = 10 * 60;
    private static final int RESEND_COOLDOWN_SECONDS = 30;

    // --- FXML bindings -------------------------------------------------------

    @FXML private Label emailLabel;
    @FXML private TextField codeBox1;
    @FXML private TextField codeBox2;
    @FXML private TextField codeBox3;
    @FXML private TextField codeBox4;
    @FXML private TextField codeBox5;
    @FXML private TextField codeBox6;
    @FXML private Label timerLabel;
    @FXML private Hyperlink resendLink;
    @FXML private Label statusLabel;
    @FXML private Button backButton;
    @FXML private Button nextButton;

    // --- State ---------------------------------------------------------------

    private final AuthService auth = AuthService.get();
    private List<TextField> boxes;
    private String email;

    private Timeline countdown;
    private int remainingSeconds = COUNTDOWN_SECONDS;
    private boolean expired = false;
    private boolean verifying = false;

    private Timeline resendCooldown;
    private int resendRemaining = 0;

    // -----------------------------------------------------------------------
    //  Lifecycle
    // -----------------------------------------------------------------------

    @FXML
    private void initialize() {
        boxes = List.of(codeBox1, codeBox2, codeBox3, codeBox4, codeBox5, codeBox6);

        // Next Step is disabled-for-visual-consistency — verification is
        // triggered automatically when all six boxes are filled.
        nextButton.setDisable(true);
        backButton.setOnAction(e -> {
            stopTimers();
            Router.get().goTo(Page.LOGIN);
        });

        resendLink.setOnAction(e -> handleResend());

        wireCodeBoxes();
        startCountdown();

        // Defensive fallback: if setEmail was never called (direct navigation,
        // bug, or missing upstream data) lock the UI with an explicit error
        // instead of silently accepting input that cannot be verified.
        if (email == null || email.isBlank()) {
            applyNoEmailGuard();
        }
    }

    /**
     * Entry point invoked by the Router callback from Step 1. Must be called
     * before the page becomes interactive; {@link #initialize()} already ran
     * at this point but the scene is not yet attached, so the email is
     * displayed on first render.
     */
    public void setEmail(String email) {
        boolean guardWasActive = (this.email == null || this.email.isBlank());
        this.email = email;
        if (emailLabel != null) {
            emailLabel.setText(email == null ? "" : email);
        }
        // If the guard ran during initialize(), lift it now that we have an email.
        if (email != null && !email.isBlank() && !expired) {
            clearStatus();
            setBoxesDisabled(false);
            if (guardWasActive && (countdown == null || countdown.getStatus() != javafx.animation.Animation.Status.RUNNING)) {
                startCountdown();
            }
            Platform.runLater(() -> {
                if (!boxes.isEmpty()) boxes.get(0).requestFocus();
            });
        }
    }

    // -----------------------------------------------------------------------
    //  Code-box wiring
    // -----------------------------------------------------------------------

    /**
     * Installs the per-box behavior:
     * <ul>
     *   <li>single alphanumeric character, uppercased;</li>
     *   <li>multi-character input (typing or paste) flows into the next boxes;</li>
     *   <li>backspace on an empty box moves focus to the previous box;</li>
     *   <li>auto-submit once all six boxes are filled.</li>
     * </ul>
     */
    private void wireCodeBoxes() {
        for (int i = 0; i < boxes.size(); i++) {
            final int idx = i;
            TextField box = boxes.get(i);

            box.textProperty().addListener((obs, oldV, newV) -> {
                if (newV == null) return;

                // Keep only alphanumerics, uppercase. If sanitation changed the
                // text, write it back — the recursive notification will fall
                // through the conditions below without looping.
                String sanitized = newV.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
                if (!sanitized.equals(newV)) {
                    box.setText(sanitized);
                    return;
                }

                if (sanitized.length() > 1) {
                    // Typed or pasted more than one char: spill across the
                    // following boxes so paste "ABC123" into box 1 fills all.
                    distributeOverflow(idx, sanitized);
                    return;
                }

                if (sanitized.length() == 1 && idx < boxes.size() - 1) {
                    boxes.get(idx + 1).requestFocus();
                }

                maybeAutoSubmit();
            });

            // Backspace on an empty box jumps back so the user can continue
            // deleting without having to re-click.
            box.setOnKeyPressed(ev -> {
                if (ev.getCode() == KeyCode.BACK_SPACE
                        && box.getText().isEmpty()
                        && idx > 0) {
                    TextField prev = boxes.get(idx - 1);
                    prev.requestFocus();
                    prev.deselect();
                    prev.end();
                }
            });
        }
    }

    /**
     * Writes {@code text} across the boxes starting at {@code startIdx}, one
     * character per box. Focuses the first empty box that follows (or keeps
     * focus on the last box if the sequence was fully filled).
     */
    private void distributeOverflow(int startIdx, String text) {
        int written = 0;
        for (int i = 0; i < text.length() && startIdx + i < boxes.size(); i++) {
            boxes.get(startIdx + i).setText(String.valueOf(text.charAt(i)));
            written++;
        }
        int nextFocus = Math.min(startIdx + written, boxes.size() - 1);
        boxes.get(nextFocus).requestFocus();
        boxes.get(nextFocus).end();
        maybeAutoSubmit();
    }

    /** Returns the concatenated code across all six boxes. */
    private String currentCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (TextField b : boxes) {
            String t = b.getText();
            if (t != null) sb.append(t);
        }
        return sb.toString();
    }

    private void maybeAutoSubmit() {
        if (verifying || expired) return;
        String code = currentCode();
        if (code.length() == CODE_LENGTH) {
            submitVerification(code);
        }
    }

    // -----------------------------------------------------------------------
    //  Verification submit
    // -----------------------------------------------------------------------

    private void submitVerification(String code) {
        if (email == null || email.isBlank()) {
            showStatus("Missing email for verification. Please restart signup.", true);
            return;
        }
        verifying = true;
        setBoxesDisabled(true);
        clearStatus();

        auth.verifyEmail(email, code)
                .whenComplete((resp, err) -> Platform.runLater(() -> {
                    verifying = false;
                    if (err != null) {
                        handleVerifyFailure(err);
                        return;
                    }
                    handleVerifySuccess(resp);
                }));
    }

    private void handleVerifySuccess(VerifyEmailResponse resp) {
        stopTimers();
        setBoxesDisabled(true);
        resendLink.setDisable(true);
        // Hand the verified email to Step 3 via the Router callback so the
        // OTP-setup page can call /setup-otp and /verify-otp-setup with it.
        final String verifiedEmail = email;
        Router.get().goTo(Page.SIGNUP_STEP_3,
                (SignupStep3Controller c) -> c.setEmail(verifiedEmail));
    }

    private void handleVerifyFailure(Throwable err) {
        Throwable t = unwrap(err);
        if (t instanceof ApiException.GoneException) {
            // Server-side expiry: same UX as our client-side timer expiry.
            expireCode("Verification code expired. Please request a new one.");
            return;
        }
        if (t instanceof ApiException.NotFoundException nfe) {
            clearBoxes();
            setBoxesDisabled(false);
            showStatus(friendly(nfe, "No pending verification for this email."), true);
            return;
        }
        if (t instanceof ApiException.ValidationException ve) {
            clearBoxes();
            setBoxesDisabled(false);
            showStatus(friendly(ve, "Invalid verification code. Please try again."), true);
            focusFirstBox();
            return;
        }
        if (t instanceof ApiException.ServerException) {
            setBoxesDisabled(false);
            showStatus("Server error. Please try again later.", true);
            return;
        }
        if (t instanceof ApiException.NetworkException ne) {
            setBoxesDisabled(false);
            showStatus(friendly(ne, "Cannot reach the server."), true);
            return;
        }
        setBoxesDisabled(false);
        showStatus("Unexpected error. Please try again.", true);
    }

    // -----------------------------------------------------------------------
    //  Countdown timer
    // -----------------------------------------------------------------------

    private void startCountdown() {
        remainingSeconds = COUNTDOWN_SECONDS;
        expired = false;
        timerLabel.getStyleClass().remove("expired");
        updateTimerLabel();

        if (countdown != null) countdown.stop();
        countdown = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            remainingSeconds--;
            updateTimerLabel();
            if (remainingSeconds <= 0) {
                countdown.stop();
                expireCode("Verification code expired. Please request a new one.");
            }
        }));
        countdown.setCycleCount(Timeline.INDEFINITE);
        countdown.play();
    }

    private void updateTimerLabel() {
        int safe = Math.max(0, remainingSeconds);
        int m = safe / 60;
        int s = safe % 60;
        timerLabel.setText(String.format("%d:%02d", m, s));
    }

    /**
     * Locks the code inputs, clears any partial entry, marks the timer
     * label as expired, and surfaces the given message. The user must click
     * Resend to re-enable the inputs and restart the countdown.
     */
    private void expireCode(String message) {
        expired = true;
        if (countdown != null) countdown.stop();
        remainingSeconds = 0;
        updateTimerLabel();
        if (!timerLabel.getStyleClass().contains("expired")) {
            timerLabel.getStyleClass().add("expired");
        }
        clearBoxes();
        setBoxesDisabled(true);
        showStatus(message, true);
        // Enable resend immediately even if a cooldown was running: the user
        // needs a way out of the expired state.
        cancelResendCooldown();
    }

    // -----------------------------------------------------------------------
    //  Resend
    // -----------------------------------------------------------------------

    private void handleResend() {
        if (resendLink.isDisabled()) return;
        resendLink.setDisable(true);
        clearStatus();

        auth.resendVerificationEmail(email)
                .whenComplete((v, err) -> Platform.runLater(() -> {
                    if (err != null) {
                        // Reset the link so the user can try again.
                        resendLink.setDisable(false);
                        showStatus("Could not resend the code. Please try again.", true);
                        return;
                    }
                    onResendSucceeded();
                }));
    }

    private void onResendSucceeded() {
        // Fresh 10-minute window, empty boxes, focus the first one.
        clearBoxes();
        setBoxesDisabled(false);
        startCountdown();
        focusFirstBox();
        startResendCooldown();
        showStatus("A new code has been sent to " + (email == null ? "your email" : email) + ".", false);
    }

    private void startResendCooldown() {
        resendRemaining = RESEND_COOLDOWN_SECONDS;
        resendLink.setDisable(true);
        updateResendLabel();

        if (resendCooldown != null) resendCooldown.stop();
        resendCooldown = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            resendRemaining--;
            if (resendRemaining <= 0) {
                cancelResendCooldown();
            } else {
                updateResendLabel();
            }
        }));
        resendCooldown.setCycleCount(Timeline.INDEFINITE);
        resendCooldown.play();
    }

    private void cancelResendCooldown() {
        if (resendCooldown != null) {
            resendCooldown.stop();
            resendCooldown = null;
        }
        resendRemaining = 0;
        resendLink.setText("Resend code");
        resendLink.setDisable(false);
    }

    private void updateResendLabel() {
        resendLink.setText("Resend code in " + resendRemaining + "s");
    }

    // -----------------------------------------------------------------------
    //  Small helpers
    // -----------------------------------------------------------------------

    private void clearBoxes() {
        for (TextField b : boxes) b.setText("");
    }

    private void setBoxesDisabled(boolean disabled) {
        for (TextField b : boxes) b.setDisable(disabled);
    }

    private void focusFirstBox() {
        if (boxes.isEmpty()) return;
        Platform.runLater(() -> boxes.get(0).requestFocus());
    }

    private void stopTimers() {
        if (countdown != null) countdown.stop();
        if (resendCooldown != null) resendCooldown.stop();
    }

    private void applyNoEmailGuard() {
        setBoxesDisabled(true);
        if (countdown != null) countdown.stop();
        showStatus("Missing email context. Please restart signup from the beginning.", true);
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
        statusLabel.getStyleClass().removeAll("text-md-error", "text-md");
        statusLabel.getStyleClass().add(isError ? "text-md-error" : "text-md");
    }

    private void clearStatus() {
        statusLabel.setText("");
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
    }

    private static String friendly(ApiException e, String fallback) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? fallback : msg;
    }

    private static Throwable unwrap(Throwable t) {
        while (t != null && t.getCause() != null
                && (t instanceof java.util.concurrent.CompletionException
                || t instanceof java.util.concurrent.ExecutionException)) {
            t = t.getCause();
        }
        return t;
    }

}
