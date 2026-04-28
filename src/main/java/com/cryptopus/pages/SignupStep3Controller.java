package com.cryptopus.pages;

import com.cryptopus.auth.AuthService;
import com.cryptopus.auth.dto.SetupOtpResponse;
import com.cryptopus.auth.dto.VerifyOtpSetupResponse;
import com.cryptopus.nav.Page;
import com.cryptopus.nav.Router;
import com.cryptopus.net.ApiException;
import com.cryptopus.shared.modal.ModalService;
import com.cryptopus.shared.modal.ModalType;
import com.cryptopus.util.QrCodeGenerator;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Controller for Signup Step 3 — OTP / 2FA setup ({@code SignupStep3.fxml}).
 *
 * <p>Receives the verified email via {@link #setEmail(String)} (invoked by
 * {@link Router#goTo(Page, java.util.function.Consumer)} from Step 2). On
 * load, calls {@code POST /api/auth/setup-otp} to obtain the {@code otpauth://}
 * URL, renders it as a QR image, then auto-submits the user-entered 6-digit
 * code to {@code POST /api/auth/verify-otp-setup}.</p>
 *
 * <p>No countdown timer is shown — the QR/secret itself does not expire on a
 * 30-second cadence; only the TOTP code inside the authenticator app does.</p>
 */
public class SignupStep3Controller {

    // --- Constants -----------------------------------------------------------

    private static final int CODE_LENGTH = 6;
    private static final int QR_SIZE = 220;

    // --- FXML bindings -------------------------------------------------------

    @FXML private StackPane qrPane;
    @FXML private ImageView qrImageView;
    @FXML private VBox loadingPane;
    @FXML private VBox helpPane;
    @FXML private Label helpPromptLabel;
    @FXML private Hyperlink helpToggleLink;
    @FXML private Label otpSecretLabel;

    @FXML private TextField codeBox1;
    @FXML private TextField codeBox2;
    @FXML private TextField codeBox3;
    @FXML private TextField codeBox4;
    @FXML private TextField codeBox5;
    @FXML private TextField codeBox6;

    @FXML private Label statusLabel;
    @FXML private Button backButton;
    @FXML private Button nextButton;

    // --- State ---------------------------------------------------------------

    private final AuthService auth = AuthService.get();
    private List<TextField> boxes;

    private String email;
    private String otpSecret;
    private boolean qrLoaded = false;
    private boolean verifying = false;
    private boolean locked = false;

    // -----------------------------------------------------------------------
    //  Lifecycle
    // -----------------------------------------------------------------------

    @FXML
    private void initialize() {
        boxes = List.of(codeBox1, codeBox2, codeBox3, codeBox4, codeBox5, codeBox6);

        // Next Step is disabled-for-visual-consistency — verification is
        // triggered automatically when all six digits are filled.
        nextButton.setDisable(true);
        backButton.setOnAction(e -> Router.get().goTo(Page.LOGIN));

        helpToggleLink.setOnAction(e -> toggleHelp());

        wireCodeBoxes();
        setBoxesDisabled(true);            // remain disabled until QR loads
        showLoading(true);
        clearStatus();

        // Defer the network call so the scene is attached before we start I/O.
        Platform.runLater(this::loadQrCodeIfReady);
    }

    /**
     * Entry point invoked by the Router callback from Step 2. Must be called
     * before the page becomes interactive; {@link #initialize()} already ran
     * but the scene is not yet attached, so the request kicks off on the
     * next pulse.
     */
    public void setEmail(String email) {
        this.email = email;
        // If initialize() already deferred loadQrCodeIfReady() before the
        // email arrived, that call will see this.email and proceed; otherwise
        // we'll be picked up on the deferred dispatch.
    }

    private void loadQrCodeIfReady() {
        if (email == null || email.isBlank()) {
            applyNoEmailGuard();
            return;
        }
        loadQrCode();
    }

    // -----------------------------------------------------------------------
    //  setup-otp call
    // -----------------------------------------------------------------------

    private void loadQrCode() {
        showLoading(true);
        clearStatus();

        auth.setupOtp(email)
                .whenComplete((resp, err) -> Platform.runLater(() -> {
                    if (err != null) {
                        showLoading(false);
                        showStatus(mapSetupError(err), true);
                        return;
                    }
                    onSetupSuccess(resp);
                }));
    }

    private void onSetupSuccess(SetupOtpResponse resp) {
        if (resp == null || resp.getQrCodeUrl() == null || resp.getQrCodeUrl().isBlank()) {
            showLoading(false);
            showStatus("Unexpected response from server.", true);
            return;
        }
        try {
            Image qr = QrCodeGenerator.toImage(resp.getQrCodeUrl(), QR_SIZE, QR_SIZE);
            qrImageView.setImage(qr);
        } catch (RuntimeException e) {
            showLoading(false);
            showStatus("Could not render the QR code. Please try again.", true);
            return;
        }
        otpSecret = resp.getOtpSecret();
        if (otpSecret != null && !otpSecret.isBlank()) {
            otpSecretLabel.setText(otpSecret);
            otpSecretLabel.setVisible(true);
            otpSecretLabel.setManaged(true);
        }
        showLoading(false);
        qrLoaded = true;
        setBoxesDisabled(false);
        focusFirstBox();
    }

    // -----------------------------------------------------------------------
    //  Code-box wiring
    // -----------------------------------------------------------------------

    /**
     * Installs the per-box behavior:
     * <ul>
     *   <li>single digit only;</li>
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

                String sanitized = newV.replaceAll("\\D", "");
                if (!sanitized.equals(newV)) {
                    box.setText(sanitized);
                    return;
                }

                if (sanitized.length() > 1) {
                    distributeOverflow(idx, sanitized);
                    return;
                }

                if (sanitized.length() == 1 && idx < boxes.size() - 1) {
                    boxes.get(idx + 1).requestFocus();
                }

                maybeAutoSubmit();
            });

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

    private String currentCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (TextField b : boxes) {
            String t = b.getText();
            if (t != null) sb.append(t);
        }
        return sb.toString();
    }

    private void maybeAutoSubmit() {
        if (verifying || locked || !qrLoaded) return;
        String code = currentCode();
        if (code.length() == CODE_LENGTH) {
            submitVerification(code);
        }
    }

    // -----------------------------------------------------------------------
    //  verify-otp-setup call
    // -----------------------------------------------------------------------

    private void submitVerification(String code) {
        if (email == null || email.isBlank()) {
            showStatus("Missing email for verification. Please restart signup.", true);
            return;
        }
        verifying = true;
        setBoxesDisabled(true);
        clearStatus();

        auth.verifyOtpSetup(email, code)
                .whenComplete((resp, err) -> Platform.runLater(() -> {
                    verifying = false;
                    if (err != null) {
                        handleVerifyFailure(err);
                        return;
                    }
                    handleVerifySuccess(resp);
                }));
    }

    private void handleVerifySuccess(VerifyOtpSetupResponse resp) {
        locked = true;
        setBoxesDisabled(true);
        clearStatus();
        showSuccessModal();
    }

    /**
     * Opens the post-signup success modal. Extracted so it can be invoked
     * directly during design iteration (see {@code initialize()}).
     */
    private void showSuccessModal() {
        ModalService.get().show(
                ModalType.SUCCESS,
                "Registration completed successfully!",
                "Your account is now secured. We recommend connecting your Kraken trading account next so you can continue the setup process.",
                "Connect Kraken",
                () -> {
                    // TODO: navigate to the Kraken connection page once it exists.
                    ModalService.get().close();
                },
                "Back to Login",
                () -> {
                    ModalService.get().close();
                    Router.get().goTo(Page.LOGIN);
                }
        );
    }

    private void handleVerifyFailure(Throwable err) {
        Throwable t = unwrap(err);
        if (t instanceof ApiException.GoneException) {
            // 410: the setup session is gone. The user must restart signup.
            locked = true;
            clearBoxes();
            setBoxesDisabled(true);
            showStatus("Setup session expired. Please restart signup.", true);
            return;
        }
        if (t instanceof ApiException.UnauthorizedException) {
            // 401: incorrect code — let the user retry.
            clearBoxes();
            setBoxesDisabled(false);
            showStatus("Incorrect code. Please try again.", true);
            focusFirstBox();
            return;
        }
        if (t instanceof ApiException.ValidationException ve) {
            clearBoxes();
            setBoxesDisabled(false);
            showStatus(friendly(ve, "Invalid code. Please try again."), true);
            focusFirstBox();
            return;
        }
        if (t instanceof ApiException.NotFoundException nfe) {
            clearBoxes();
            setBoxesDisabled(false);
            showStatus(friendly(nfe, "No pending OTP setup for this account."), true);
            return;
        }
        if (t instanceof ApiException.ConflictException c) {
            locked = true;
            setBoxesDisabled(true);
            showStatus(friendly(c, "OTP is already enabled for this account."), true);
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
    //  Setup-error mapping (for /setup-otp failures during initial load)
    // -----------------------------------------------------------------------

    private String mapSetupError(Throwable err) {
        Throwable t = unwrap(err);
        if (t instanceof ApiException.ConflictException c)
            return friendly(c, "OTP is already enabled for this account.");
        if (t instanceof ApiException.NotFoundException nfe)
            return friendly(nfe, "Account not found. Please restart signup.");
        if (t instanceof ApiException.ValidationException ve)
            return friendly(ve, "Could not initialize OTP setup.");
        if (t instanceof ApiException.UnauthorizedException)
            return "OTP setup is not permitted for this account.";
        if (t instanceof ApiException.ServerException)
            return "Server error. Please try again later.";
        if (t instanceof ApiException.NetworkException ne)
            return friendly(ne, "Cannot reach the server.");
        if (t instanceof ApiException.UnexpectedResponseException)
            return "Unexpected server response.";
        return "Unexpected error. Please try again.";
    }

    // -----------------------------------------------------------------------
    //  Help-view toggle
    // -----------------------------------------------------------------------

    private void toggleHelp() {
        boolean showHelp = !helpPane.isVisible();
        helpPane.setVisible(showHelp);
        helpPane.setManaged(showHelp);
        qrPane.setVisible(!showHelp);
        qrPane.setManaged(!showHelp);
        if (showHelp) {
            helpPromptLabel.setText("Done? Go back to the QR code:");
            helpToggleLink.setText("Click here");
        } else {
            helpPromptLabel.setText("Need help or can't scan the QR code?");
            helpToggleLink.setText("Click here");
        }
    }

    // -----------------------------------------------------------------------
    //  Small helpers
    // -----------------------------------------------------------------------

    private void showLoading(boolean show) {
        loadingPane.setVisible(show);
        loadingPane.setManaged(show);
        qrImageView.setVisible(!show && qrImageView.getImage() != null);
        qrImageView.setManaged(!show && qrImageView.getImage() != null);
    }

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

    private void applyNoEmailGuard() {
        locked = true;
        showLoading(false);
        setBoxesDisabled(true);
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
