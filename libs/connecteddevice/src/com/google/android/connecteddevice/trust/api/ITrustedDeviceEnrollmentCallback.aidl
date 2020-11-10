package com.google.android.connecteddevice.trust.api;

import com.google.android.connecteddevice.trust.api.TrustedDevice;

/** Callback for triggered trusted device enrollment events. */
oneway interface ITrustedDeviceEnrollmentCallback {

    /** Triggered when credentials validation is needed to authenticate a new escrow token. */
    void onValidateCredentialsRequest();

    /** Triggered when an error happens during trusted device enrollment. */
    void onTrustedDeviceEnrollmentError(in int error);
}
