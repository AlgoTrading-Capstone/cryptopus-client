package com.cryptopus.pages;

import com.cryptopus.shared.modal.ModalService;
import com.cryptopus.shared.modal.ModalType;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

/**
 * Controller for the internal modal design playground ({@code ModalTest.fxml}).
 *
 * <p>Not part of the production flow. Each button opens a sample modal so the
 * developer can iterate on visuals, animation timing, and copy without going
 * through the real signup / auth paths.</p>
 */
public class ModalTestController {

    @FXML private Button successButton;
    @FXML private Button errorButton;
    @FXML private Button warningButton;
    @FXML private Button noneButton;

    @FXML
    private void initialize() {
        successButton.setOnAction(e -> openSuccess());
        errorButton.setOnAction(e -> openError());
        warningButton.setOnAction(e -> openWarning());
        noneButton.setOnAction(e -> openNone());
    }

    // -----------------------------------------------------------------------
    //  Sample modals — one per ModalType
    // -----------------------------------------------------------------------

    private void openSuccess() {
        ModalService.get().show(
                ModalType.SUCCESS,
                "Operation completed",
                "Everything went through as expected. You can continue with the next step whenever you're ready.",
                "Continue",
                ModalService.get()::close,
                "Close",
                ModalService.get()::close
        );
    }

    private void openError() {
        ModalService.get().show(
                ModalType.ERROR,
                "Something went wrong",
                "We couldn't complete the request. Please check your connection and try again.",
                "Retry",
                ModalService.get()::close,
                "Dismiss",
                ModalService.get()::close
        );
    }

    private void openWarning() {
        ModalService.get().show(
                ModalType.WARNING,
                "Heads up",
                "This action may have consequences you should review before proceeding.",
                "Proceed",
                ModalService.get()::close,
                "Cancel",
                ModalService.get()::close
        );
    }

    private void openNone() {
        ModalService.get().show(
                ModalType.NONE,
                "Just so you know",
                "A plain informational modal with no icon and no entrance animation on the icon slot.",
                "OK",
                ModalService.get()::close
        );
    }
}
