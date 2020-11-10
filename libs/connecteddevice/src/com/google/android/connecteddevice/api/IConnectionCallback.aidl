package com.google.android.connecteddevice.api;

import com.google.android.connecteddevice.model.ConnectedDevice;

/** Callback for triggered connection events. */
oneway interface IConnectionCallback {
    /** Triggered when a new connectedDevice has connected. */
    void onDeviceConnected(in ConnectedDevice connectedDevice);

    /** Triggered when a connectedDevice has disconnected. */
    void onDeviceDisconnected(in ConnectedDevice connectedDevice);
}
