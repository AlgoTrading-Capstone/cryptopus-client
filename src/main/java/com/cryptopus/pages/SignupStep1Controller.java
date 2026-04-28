package com.cryptopus.pages;

import com.cryptopus.auth.AuthService;
import com.cryptopus.auth.dto.RegisterRequest;
import com.cryptopus.auth.dto.RegisterResponse;
import com.cryptopus.data.Countries;
import com.cryptopus.nav.Page;
import com.cryptopus.nav.Router;
import com.cryptopus.net.ApiException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.skin.ComboBoxListViewSkin;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Controller for the first step of the signup flow ({@code SignupStep1.fxml}).
 *
 * <p>Collects the user's personal details and credentials, performs local
 * validation, and delegates registration to {@link AuthService}. On success,
 * the flow is intended to advance to {@code SIGNUP_STEP_2}; that page is not
 * implemented yet so the navigation is left as a TODO and the user sees a
 * success message inline.</p>
 */
public class SignupStep1Controller {

    // --- Validation patterns -------------------------------------------------

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    // Allow digits, spaces, +, -, parentheses. 7–15 digits total.
    private static final Pattern PHONE_ALLOWED =
            Pattern.compile("^[\\d+\\-() ]+$");

    // 3–10 alphanumerics (+ optional spaces / dashes). Keeps the rule
    // permissive enough to accept most locales.
    private static final Pattern POSTAL_CODE_PATTERN =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 \\-]{1,9}$");

    // Password: ≥8 chars, ≥1 uppercase, ≥1 digit, ≥1 special.
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");

    private static final DateTimeFormatter DOB_ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    // --- FXML bindings -------------------------------------------------------

    @FXML
    private TextField firstNameField;
    @FXML
    private TextField lastNameField;

    @FXML
    private TextField dobDayField;
    @FXML
    private TextField dobMonthField;
    @FXML
    private TextField dobYearField;

    @FXML
    private TextField emailField;
    @FXML
    private TextField phoneField;

    @FXML
    private TextField addressField;
    @FXML
    private TextField cityField;
    @FXML
    private ComboBox<String> countryCombo;
    @FXML
    private TextField postalCodeField;

    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Button backButton;
    @FXML
    private Button nextButton;
    @FXML
    private Label statusLabel;
    @FXML
    private Label dobErrorLabel;

    private final AuthService auth = AuthService.get();

    // -----------------------------------------------------------------------
    //  Lifecycle
    // -----------------------------------------------------------------------

    @FXML
    private void initialize() {
        backButton.setOnAction(e -> Router.get().goTo(Page.LOGIN));
        nextButton.setOnAction(e -> handleNext());

        // Restrict DOB fields to digits and cap their length.
        restrictToDigits(dobDayField, 2);
        restrictToDigits(dobMonthField, 2);
        restrictToDigits(dobYearField, 4);

        // Auto-advance focus once a DOB segment is filled: DD -> MM -> YYYY.
        autoAdvanceOnMaxLen(dobDayField, 2, dobMonthField);
        autoAdvanceOnMaxLen(dobMonthField, 2, dobYearField);

        // Clear the dedicated DOB error label as soon as the user edits any
        // of the three DOB fields.
        java.util.List.of(dobDayField, dobMonthField, dobYearField).forEach(f ->
                f.textProperty().addListener((obs, oldV, newV) -> clearDobError()));

        // Clear the error style as soon as the user edits a field.
        clearErrorOnEdit(firstNameField, "First name");
        clearErrorOnEdit(lastNameField, "Last name");
        clearErrorOnEdit(dobDayField, "DD");
        clearErrorOnEdit(dobMonthField, "MM");
        clearErrorOnEdit(dobYearField, "YYYY");
        clearErrorOnEdit(emailField, "you@example.com");
        clearErrorOnEdit(phoneField, "+1 555 123 4567");
        clearErrorOnEdit(addressField, "Street and house number");
        clearErrorOnEdit(cityField, "City");
        clearErrorOnEdit(postalCodeField, "Postal code");
        clearErrorOnEdit(passwordField, "Create a strong password");
        clearErrorOnEdit(confirmPasswordField, "Repeat your password");

        configureCountryCombo();
    }

    // -----------------------------------------------------------------------
    //  Country combo wiring
    // -----------------------------------------------------------------------

    /**
     * Populates the country {@link ComboBox} with the canonical list and wires
     * error-state clearing.
     */
    private void configureCountryCombo() {
        countryCombo.setItems(FXCollections.observableArrayList(Countries.ALL));

        // Clear error styling as soon as the user picks a value.
        countryCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                countryCombo.getStyleClass().remove("error");
                countryCombo.setPromptText("Country");
            }
        });

        // Match the popup's width to the ComboBox so the dropdown is not wider
        // than the control. The skin is created lazily the first
        // time the popup is opened, so we install the binding on demand.
        countryCombo.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (!isShowing) return;
            if (countryCombo.getSkin() instanceof ComboBoxListViewSkin<?> skin
                    && skin.getPopupContent() instanceof ListView<?> listView
                    && !listView.prefWidthProperty().isBound()) {
                listView.minWidthProperty().bind(countryCombo.widthProperty());
                listView.prefWidthProperty().bind(countryCombo.widthProperty());
                listView.maxWidthProperty().bind(countryCombo.widthProperty());
            }
        });
    }

    // -----------------------------------------------------------------------
    //  Submit flow
    // -----------------------------------------------------------------------

    private void handleNext() {
        clearStatus();
        clearDobError();

        boolean valid = true;

        String firstName = safeTrim(firstNameField.getText());
        String lastName = safeTrim(lastNameField.getText());
        String email = safeTrim(emailField.getText());
        String phone = safeTrim(phoneField.getText());
        String address = safeTrim(addressField.getText());
        String city = safeTrim(cityField.getText());
        String country = safeTrim(countryCombo.getValue());
        String postal = safeTrim(postalCodeField.getText());
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        String confirm = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();

        if (firstName.isEmpty()) {
            markError(firstNameField, "First name is required");
            valid = false;
        }
        if (lastName.isEmpty()) {
            markError(lastNameField, "Last name is required");
            valid = false;
        }

        LocalDate dob = validateDob();
        if (dob == null) valid = false;

        if (email.isEmpty() || !EMAIL_PATTERN.matcher(email).matches()) {
            markError(emailField, "Enter a valid email");
            valid = false;
        }
        if (!isValidPhone(phone)) {
            markError(phoneField, "Enter a valid phone number");
            valid = false;
        }
        if (address.isEmpty()) {
            markError(addressField, "Address is required");
            valid = false;
        }
        if (city.isEmpty()) {
            markError(cityField, "City is required");
            valid = false;
        }
        if (country.isEmpty()) {
            markCountryError("Country is required");
            valid = false;
        } else if (!Countries.isKnown(country)) {
            markCountryError("Select a country from the list");
            valid = false;
        }
        if (!POSTAL_CODE_PATTERN.matcher(postal).matches()) {
            markError(postalCodeField, "Enter a valid postal code");
            valid = false;
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            markError(passwordField, "Min 8 chars, 1 uppercase, 1 digit, 1 special");
            valid = false;
        }
        if (confirm.isEmpty() || !confirm.equals(password)) {
            markError(confirmPasswordField, "Passwords do not match");
            valid = false;
        }

        if (!valid) return;

        RegisterRequest request = new RegisterRequest(
                firstName,
                lastName,
                dob.format(DOB_ISO),
                email,
                phone,
                address,
                city,
                country,
                postal,
                password
        );

        nextButton.setDisable(true);
        backButton.setDisable(true);

        auth.register(request)
                .whenComplete((resp, err) -> Platform.runLater(() -> {
                    nextButton.setDisable(false);
                    backButton.setDisable(false);
                    if (err != null) {
                        showStatus(mapRegisterError(err), true);
                        return;
                    }
                    onRegisterSuccess(resp);
                }));
    }

    private void onRegisterSuccess(RegisterResponse resp) {
        if (resp == null || resp.getUserId() == null || resp.getEmail() == null) {
            showStatus("Unexpected response from server.", true);
            return;
        }
        // Hand the server-confirmed email to Step 2 via the Router's
        // controller-initializer hook, so the verify-email page owns its
        // context without any shared module state.
        final String email = resp.getEmail();
        Router.get().goTo(Page.SIGNUP_STEP_2,
                (SignupStep2Controller c) -> c.setEmail(email));
    }

    // -----------------------------------------------------------------------
    //  DOB validation
    // -----------------------------------------------------------------------

    /**
     * Validates the three DOB fields together. On failure, marks the DOB
     * fields and writes a message to the dedicated {@code dobErrorLabel}
     * beside the row; on success returns the parsed date.
     */
    private LocalDate validateDob() {
        String d = safeTrim(dobDayField.getText());
        String m = safeTrim(dobMonthField.getText());
        String y = safeTrim(dobYearField.getText());

        if (d.isEmpty() || m.isEmpty() || y.isEmpty()) {
            if (d.isEmpty()) {
                dobDayField.setPromptText("DD");
                addErrorClass(dobDayField);
            }
            if (m.isEmpty()) {
                dobMonthField.setPromptText("MM");
                addErrorClass(dobMonthField);
            }
            if (y.isEmpty()) {
                dobYearField.setPromptText("YYYY");
                addErrorClass(dobYearField);
            }
            showDobError("Date of birth is required");
            return null;
        }

        try {
            int day = Integer.parseInt(d);
            int month = Integer.parseInt(m);
            int year = Integer.parseInt(y);
            LocalDate date = LocalDate.of(year, month, day);
            if (date.isAfter(LocalDate.now())) {
                markDobError("Date of birth cannot be in the future");
                return null;
            }
            return date;
        } catch (NumberFormatException | java.time.DateTimeException e) {
            markDobError("Please enter a valid date");
            return null;
        }
    }

    /**
     * Highlights all three DOB fields and shows {@code message} beside them.
     */
    private void markDobError(String message) {
        addErrorClass(dobDayField);
        addErrorClass(dobMonthField);
        addErrorClass(dobYearField);
        showDobError(message);
    }

    private void showDobError(String message) {
        dobErrorLabel.setText(message);
        dobErrorLabel.setVisible(true);
        dobErrorLabel.setManaged(true);
    }

    private void clearDobError() {
        dobErrorLabel.setText("");
        dobErrorLabel.setVisible(false);
        dobErrorLabel.setManaged(false);
    }

    // -----------------------------------------------------------------------
    //  Small helpers
    // -----------------------------------------------------------------------

    private boolean isValidPhone(String phone) {
        if (phone.isEmpty() || !PHONE_ALLOWED.matcher(phone).matches()) return false;
        int digits = 0;
        for (int i = 0; i < phone.length(); i++) {
            if (Character.isDigit(phone.charAt(i))) digits++;
        }
        return digits >= 7 && digits <= 15;
    }

    private static void restrictToDigits(TextField field, int maxLen) {
        field.textProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) return;
            String sanitized = newV.replaceAll("\\D", "");
            if (sanitized.length() > maxLen) sanitized = sanitized.substring(0, maxLen);
            if (!sanitized.equals(newV)) field.setText(sanitized);
        });
    }

    /**
     * Moves focus from {@code current} to {@code next} as soon as the user
     * fills {@code current} up to {@code maxLen} characters. The focus check
     * guards against focus jumps caused by programmatic text changes
     * (e.g. clearing fields during error handling).
     */
    private static void autoAdvanceOnMaxLen(TextField current, int maxLen, TextField next) {
        current.textProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.length() >= maxLen && current.isFocused()) {
                next.requestFocus();
            }
        });
    }

    private static void markError(TextInputControl field, String errorPrompt) {
        field.clear();
        field.setPromptText(errorPrompt);
        addErrorClass(field);
    }

    /**
     * Country-specific analogue of {@link #markError(TextInputControl, String)}:
     * clears the current selection/text, shows {@code errorPrompt} as the
     * prompt, and toggles the {@code error} style class on the ComboBox.
     */
    private void markCountryError(String errorPrompt) {
        countryCombo.setValue(null);
        countryCombo.setPromptText(errorPrompt);
        if (!countryCombo.getStyleClass().contains("error")) {
            countryCombo.getStyleClass().add("error");
        }
    }

    private static void addErrorClass(TextInputControl field) {
        if (!field.getStyleClass().contains("error")) {
            field.getStyleClass().add("error");
        }
    }

    private static void clearErrorOnEdit(TextInputControl field, String defaultPrompt) {
        field.textProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.isEmpty()) {
                field.getStyleClass().remove("error");
                field.setPromptText(defaultPrompt);
            }
        });
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

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    // -----------------------------------------------------------------------
    //  Error mapping
    // -----------------------------------------------------------------------

    private String mapRegisterError(Throwable err) {
        Throwable t = unwrap(err);
        if (t instanceof ApiException.ConflictException c)
            return friendly(c, "An account with that email already exists.");
        if (t instanceof ApiException.ValidationException ve)
            return friendly(ve, "Some of the information is invalid.");
        if (t instanceof ApiException.UnauthorizedException) return "Registration is not permitted.";
        if (t instanceof ApiException.NotFoundException) return "Registration service is unavailable.";
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
}