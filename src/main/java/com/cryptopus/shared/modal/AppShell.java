package com.cryptopus.shared.modal;

import javafx.scene.layout.StackPane;

/**
 * Application-level layout shell.
 *
 * <p>A single {@link StackPane} that holds two layers, in z-order:</p>
 * <ol>
 *   <li>{@link #contentHolder()} — receives the current page's root from the
 *       {@code Router}. The {@code Router} replaces this layer's single child
 *       on every navigation.</li>
 *   <li>{@link #modalLayer()} — transparent overlay that {@code ModalService}
 *       uses to show modal dialogs. Stays empty (and mouse-transparent) when
 *       no modal is active so the page below remains fully interactive.</li>
 * </ol>
 *
 * <p>Owned by {@code Router}; callers reach it via
 * {@code Router.get().modalLayer()} / {@code contentHolder()}.</p>
 */
public final class AppShell extends StackPane {

    private final StackPane contentHolder = new StackPane();
    private final StackPane modalLayer = new StackPane();

    public AppShell() {
        // Page content layer — fills the shell, receives navigation roots.
        getChildren().add(contentHolder);

        // Modal overlay layer — sits above content. Mouse-transparent and
        // unmanaged-for-picking while empty so it never steals events from
        // the page beneath. ModalService flips these flags on show/close.
        modalLayer.setMouseTransparent(true);
        modalLayer.setPickOnBounds(false);
        getChildren().add(modalLayer);
    }

    /** Layer holding the active page root. The Router writes here. */
    public StackPane contentHolder() {
        return contentHolder;
    }

    /** Layer holding the active modal (if any). ModalService writes here. */
    public StackPane modalLayer() {
        return modalLayer;
    }
}
