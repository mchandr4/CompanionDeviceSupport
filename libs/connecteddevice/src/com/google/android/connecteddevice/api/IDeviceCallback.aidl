package com.google.android.connecteddevice.api;

import com.google.android.connecteddevice.model.ConnectedDevice;

/** Triggered companionDevice events for a connected companionDevice. */
oneway interface IDeviceCallback {
    /**
     * Triggered when secure channel has been established on a companionDevice. Encrypted messaging
     * now available.
     */
    void onSecureChannelEstablished(in ConnectedDevice connectedDevice);

    /** Triggered when a new message is received from a connectedDevice. */
    void onMessageReceived(in ConnectedDevice connectedDevice, in byte[] message);

    /** Triggered when an error has occurred for a connectedDevice. */
    void onDeviceError(in ConnectedDevice connectedDevice, int error);
}
