package com.google.android.connecteddevice.trust.api;

/** Listnener for trusted device enrollment notification request. */
oneway interface IOnTrustedDeviceEnrollmentNotificationRequestListener {
    /** Triggered when enrollment notification is needed to continue trusted device enrollment. */
    void onTrustedDeviceEnrollmentNotificationRequest();
}
