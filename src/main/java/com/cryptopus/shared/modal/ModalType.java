package com.cryptopus.shared.modal;

/**
 * Semantic type of a modal dialog. Drives the one-shot icon animation played
 * by {@link ModalService} when the modal is shown.
 *
 * <p>{@link #NONE} disables icon animation entirely; use it for neutral /
 * informational modals where a motion cue would be out of place.</p>
 */
public enum ModalType {
    NONE,
    SUCCESS,
    ERROR,
    WARNING
}
