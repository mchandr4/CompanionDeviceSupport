package com.google.android.connecteddevice.trust.api;

import com.google.android.connecteddevice.trust.api.TrustedDevice;

/** Callback for triggered trusted device events. */
oneway interface ITrustedDeviceCallback {

    /** Triggered when a new device has been enrolled. */
    void onTrustedDeviceAdded(in TrustedDevice device);

    /** Triggered when a new device has been unenrolled. */
    void onTrustedDeviceRemoved(in TrustedDevice device);
}
