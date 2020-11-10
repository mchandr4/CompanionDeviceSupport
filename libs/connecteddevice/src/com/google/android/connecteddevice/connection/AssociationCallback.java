package com.google.android.connecteddevice.connection;

import androidx.annotation.NonNull;
import com.google.android.connecteddevice.model.Errors.DeviceError;

/** Callbacks that will be invoked during associating a new client. */
public interface AssociationCallback {

    /**
     * Invoked when IHU starts advertising with its device name for association successfully.
     *
     * @param deviceName The device name to identify the car.
     */
    default void onAssociationStartSuccess(@NonNull String deviceName) {}

    /** Invoked when IHU failed to start advertising for association. */
    default void onAssociationStartFailure() {}

    /**
     * Invoked when a {@link DeviceError} has been encountered in attempting
     * to associate a new device.
     *
     * @param error The failure indication.
     */
    default void onAssociationError(@DeviceError int error) {}

    /**
     * Invoked when a verification code needs to be displayed. The user needs to confirm, and
     * then notify that the code has been verified.
     *
     * @param code The verification code.
     */
    default void onVerificationCodeAvailable(@NonNull String code) {}

    /**
     * Invoked when the association has completed.
     *
     * @param deviceId The id of the newly associated device.
     */
    default void onAssociationCompleted(@NonNull String deviceId) {}
}
