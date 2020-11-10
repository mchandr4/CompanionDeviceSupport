package com.google.android.connecteddevice.api;

/** Callback for triggered association events. */
oneway interface IAssociationCallback {

    /** Triggered when IHU starts advertising for association successfully. */
    void onAssociationStartSuccess(in String deviceName);

    /** Triggered when IHU failed to start advertising for association. */
    void onAssociationStartFailure();

    /** Triggered when an error has been encountered during assocition with a new device. */
    void onAssociationError(in int error);

    /**  Triggered when a pairing code is available to be present. */
    void onVerificationCodeAvailable(in String code);

    /** Triggered when the assocition has completed */
    void onAssociationCompleted();
}
