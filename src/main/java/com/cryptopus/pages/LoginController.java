package com.cryptopus.pages;

import com.cryptopus.auth.AuthService;
import com.cryptopus.auth.SessionManager;
import com.cryptopus.auth.dto.LoginResponse;
import com.cryptopus.auth.dto.OtpVerifyResponse;
import com.cryptopus.nav.Page;
import com.cryptopus.nav.Router;
import com.cryptopus.net.ApiException;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Controller for the combined Login + OTP screen ({@code Login.fxml}).
 *
 * <p>Responsibilities are kept thin: local input validation, wiring UI events
 * to {@link AuthService}, switching between the credentials and OTP panes,
 * and surfacing errors back to the UI. All real HTTP work lives in the net /
 * auth layers.</p>
 */
public class LoginController {

    // Loose but reasonable email regex – server is authoritative.
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    // --- Credentials pane ---
    @FXML
    private VBox credentialsPane;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button loginButton;
    @FXML
    private Label loginStatusLabel;
    @FXML
    private Hyperlink forgotPasswordLink;
    @FXML
    private Hyperlink signUpLink;

    // --- OTP pane ---
    @FXML
    private VBox otpPane;
    @FXML
    private TextField otpField1;
    @FXML
    private TextField otpField2;
    @FXML
    private TextField otpField3;
    @FXML
    private TextField otpField4;
    @FXML
    private TextField otpField5;
    @FXML
    private TextField otpField6;
    @FXML
    private Hyperlink backToLoginLink;
    @FXML
    private Label otpStatusLabel;

    private List<TextField> otpFields;

    private final AuthService auth = AuthService.get();
    private final SessionManager session = SessionManager.get();

    /**
     * Guard against double-submission when all 6 digits are entered very quickly.
     */
    private final AtomicBoolean otpSubmitting = new AtomicBoolean(false);

    @FXML
    private void initialize() {
        otpFields = List.of(otpField1, otpField2, otpField3, otpField4, otpField5, otpField6);

        loginButton.setOnAction(e -> handleLogin());
        emailField.setOnAction(e -> handleLogin());
        passwordField.setOnAction(e -> handleLogin());

        backToLoginLink.setOnAction(e -> switchToCredentials());
        signUpLink.setOnAction(e -> Router.get().goTo(Page.SIGNUP_STEP_1));

        configureOtpFields();
    }

    // -----------------------------------------------------------------------
    //  Login flow
    // -----------------------------------------------------------------------

    private void handleLogin() {
        clearStatus(loginStatusLabel);
        restorePrompt(emailField, "Enter your email");
        restorePrompt(passwordField, "Enter your password");

        String email = safeTrim(emailField.getText());
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        boolean valid = true;
        if (email.isEmpty() || !EMAIL_PATTERN.matcher(email).matches()) {
            emailField.clear();
            emailField.setPromptText("Please enter a valid email");
            emailField.getStyleClass().add("error");
            valid = false;
        }
        if (password.isEmpty()) {
            passwordField.clear();
            passwordField.setPromptText("Password is required");
            passwordField.getStyleClass().add("error");
            valid = false;
        }
        if (!valid) return;

        loginButton.setDisable(true);

        auth.login(email, password)
                .whenComplete((resp, err) -> Platform.runLater(() -> {
                    loginButton.setDisable(false);
                    if (err != null) {
                        showStatus(loginStatusLabel, mapLoginError(err));
                        return;
                    }
                    onLoginSuccess(resp);
                }));
    }

    /**
     * Routes the user based on their server-reported registration state:
     * <ul>
     *   <li>{@code email_verified=false} → Signup Step 2 (Verify Email).
     *       The client also kicks off a {@code resend-verification-email}
     *       in the background so the user lands on a fresh code without
     *       having to ask for one.</li>
     *   <li>{@code email_verified=true, otp_verified=false} → Signup Step 3
     *       (OTP Setup). The verified email is forwarded so Step 3 can call
     *       {@code /api/auth/setup-otp} immediately.</li>
     *   <li>{@code email_verified=true, otp_verified=true} → switch to the
     *       OTP-verification pane on this same screen, using the
     *       {@code temporary_session_id} that {@link AuthService} already
     *       stored in {@link SessionManager}.</li>
     * </ul>
     */
    private void onLoginSuccess(LoginResponse resp) {
        if (resp == null) {
            showStatus(loginStatusLabel, "Unexpected response from server.");
            return;
        }

        final String email = safeTrim(emailField.getText());

        if (!resp.isEmailVerified()) {
            // Defensive: server should never send this combo, but if it does,
            // email-not-verified takes precedence regardless of otp_verified.
            primeResendAndGoToStep2(email);
            return;
        }

        if (!resp.isOtpVerified()) {
            // Email verified but OTP setup never finished — finish signup.
            Router.get().goTo(Page.SIGNUP_STEP_3,
                    (SignupStep3Controller c) -> c.setEmail(email));
            return;
        }

        // Fully registered: backend issued a temporary_session_id; verify OTP.
        if (resp.getTemporarySessionId() == null) {
            showStatus(loginStatusLabel, "Unexpected response from server.");
            return;
        }
        // temp session id already stored by AuthService; just switch UI.
        switchToOtp();
    }

    /**
     * Fire-and-forget resend so the user gets a fresh code as soon as they
     * land on Step 2. Errors are intentionally swallowed: Step 2 has its own
     * countdown / manual Resend link the user can fall back on.
     */
    private void primeResendAndGoToStep2(String email) {
        if (email != null && !email.isBlank()) {
            auth.resendVerificationEmail(email).exceptionally(ex -> null);
        }
        Router.get().goTo(Page.SIGNUP_STEP_2,
                (SignupStep2Controller c) -> c.setEmail(email));
    }

    // -----------------------------------------------------------------------
    //  OTP flow
    // -----------------------------------------------------------------------

    private void configureOtpFields() {
        for (int i = 0; i < otpFields.size(); i++) {
            final int index = i;
            TextField current = otpFields.get(i);

            current.textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal == null) return;

                // Restrict to a single digit.
                String sanitized = newVal.replaceAll("\\D", "");
                if (sanitized.length() > 1) sanitized = sanitized.substring(0, 1);
                if (!sanitized.equals(newVal)) {
                    current.setText(sanitized);
                    return; // listener will re-fire with sanitized value
                }

                if (!sanitized.isEmpty() && index < otpFields.size() - 1) {
                    otpFields.get(index + 1).requestFocus();
                }

                maybeAutoSubmitOtp();
            });

            current.setOnKeyPressed(ev -> {
                // Backspace on empty field → jump back.
                if (ev.getCode().getName().equalsIgnoreCase("Backspace")
                        && current.getText().isEmpty() && index > 0) {
                    otpFields.get(index - 1).requestFocus();
                }
            });
        }
    }

    private void maybeAutoSubmitOtp() {
        StringBuilder code = new StringBuilder(6);
        for (TextField f : otpFields) {
            String t = f.getText();
            if (t == null || t.isEmpty()) return;
            code.append(t);
        }
        if (code.length() != 6) return;

        // Atomic flip prevents duplicate submissions if the listener fires quickly.
        if (!otpSubmitting.compareAndSet(false, true)) return;

        submitOtp(code.toString());
    }

    private void submitOtp(String code) {
        clearStatus(otpStatusLabel);
        setOtpFieldsDisabled(true);

        String tempSessionId = session.getTemporarySessionId();
        if (tempSessionId == null) {
            setOtpFieldsDisabled(false);
            otpSubmitting.set(false);
            showStatus(otpStatusLabel, "Your login session expired. Please log in again.");
            switchToCredentials();
            return;
        }

        auth.verifyOtp(tempSessionId, code)
                .whenComplete((resp, err) -> Platform.runLater(() -> {
                    setOtpFieldsDisabled(false);
                    otpSubmitting.set(false);
                    if (err != null) {
                        clearOtpFields();
                        otpFields.get(0).requestFocus();
                        showStatus(otpStatusLabel, mapOtpError(err));
                        return;
                    }
                    onOtpSuccess(resp);
                }));
    }

    private void onOtpSuccess(OtpVerifyResponse resp) {
        if (resp == null || resp.getAccessToken() == null) {
            showStatus(otpStatusLabel, "Unexpected response from server.");
            return;
        }
        // Tokens are now stored in SessionManager (done by AuthService).
        // TODO: Navigate to the main dashboard page here once the router / SceneNavigator exists.
        //       e.g. Router.get().goTo(Pages.DASHBOARD);
        showStatus(otpStatusLabel, "Authentication successful.");
    }

    // -----------------------------------------------------------------------
    //  View switching helpers
    // -----------------------------------------------------------------------

    private void switchToOtp() {
        credentialsPane.setVisible(false);
        credentialsPane.setManaged(false);
        otpPane.setVisible(true);
        otpPane.setManaged(true);
        clearOtpFields();
        clearStatus(otpStatusLabel);
        otpFields.get(0).requestFocus();
    }

    private void switchToCredentials() {
        otpPane.setVisible(false);
        otpPane.setManaged(false);
        credentialsPane.setVisible(true);
        credentialsPane.setManaged(true);
        clearOtpFields();
        clearStatus(otpStatusLabel);
        session.setTemporarySessionId(null);
        emailField.requestFocus();
    }

    private void clearOtpFields() {
        for (TextField f : otpFields) f.clear();
    }

    private void setOtpFieldsDisabled(boolean disabled) {
        for (TextField f : otpFields) f.setDisable(disabled);
    }

    // -----------------------------------------------------------------------
    //  Error mapping & small UI utilities
    // -----------------------------------------------------------------------

    private String mapLoginError(Throwable err) {
        Throwable t = unwrap(err);
        if (t instanceof ApiException.UnauthorizedException) return "Invalid email or password.";
        if (t instanceof ApiException.ValidationException ve) return friendly(ve, "Invalid credentials.");
        if (t instanceof ApiException.NotFoundException) return "Login service is unavailable.";
        if (t instanceof ApiException.ConflictException c) return friendly(c, "Request conflict.");
        if (t instanceof ApiException.ServerException) return "Server error. Please try again later.";
        if (t instanceof ApiException.NetworkException ne) return friendly(ne, "Cannot reach the server.");
        if (t instanceof ApiException.UnexpectedResponseException) return "Unexpected server response.";
        return "Unexpected error. Please try again.";
    }

    private String mapOtpError(Throwable err) {
        Throwable t = unwrap(err);
        if (t instanceof ApiException.UnauthorizedException) return "Incorrect or expired OTP code.";
        if (t instanceof ApiException.ValidationException ve) return friendly(ve, "Incorrect OTP code.");
        if (t instanceof ApiException.NotFoundException) return "Login session not found. Please log in again.";
        if (t instanceof ApiException.ServerException) return "Server error. Please try again later.";
        if (t instanceof ApiException.NetworkException ne) return friendly(ne, "Cannot reach the server.");
        if (t instanceof ApiException.UnexpectedResponseException) return "Unexpected server response.";
        return "Unexpected error. Please try again.";
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

    private static void showStatus(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private static void clearStatus(Label label) {
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
    }

    private static void restorePrompt(TextField field, String prompt) {
        field.setPromptText(prompt);
        field.getStyleClass().remove("error");
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}

