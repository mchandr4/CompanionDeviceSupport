package com.google.android.connecteddevice.api;

import android.os.ParcelUuid;

import com.google.android.connecteddevice.api.IConnectionCallback;
import com.google.android.connecteddevice.api.IDeviceAssociationCallback;
import com.google.android.connecteddevice.api.IDeviceCallback;
import com.google.android.connecteddevice.api.IOnLogRequestedListener;
import com.google.android.connecteddevice.model.ConnectedDevice;

/** Manager of devices connected to the car. */
interface IConnectedDeviceManager {

    /** Returns {@link List<ConnectedDevice>} of devices currently connected. */
    List<ConnectedDevice> getActiveUserConnectedDevices();

    /**
     * Register a callback for manager triggered connection events for only the currently active
     * user's devices.
     *
     * @param callback {@link IConnectionCallback} to register.
     */
    void registerActiveUserConnectionCallback(in IConnectionCallback callback);

    /**
     * Unregister a connection callback from manager.
     *
     * @param callback {@link IConnectionCallback} to unregister.
     */
    void unregisterConnectionCallback(in IConnectionCallback callback);

    /**
     * Register a callback for a specific connectedDevice and recipient.
     *
     * @param connectedDevice {@link ConnectedDevice} to register triggers on.
     * @param recipientId {@link ParcelUuid} to register as recipient of.
     * @param callback {@link IDeviceCallback} to register.
     */
    void registerDeviceCallback(in ConnectedDevice connectedDevice, in ParcelUuid recipientId,
            in IDeviceCallback callback);

    /**
     * Unregister callback from connectedDevice events.
     *
     * @param connectedDevice {@link ConnectedDevice} callback was registered on.
     * @param recipientId {@link ParcelUuid} callback was registered under.
     * @param callback {@link IDeviceCallback} to unregister.
     */
    void unregisterDeviceCallback(in ConnectedDevice connectedDevice, in ParcelUuid recipientId,
            in IDeviceCallback callback);

    /**
     * Securely send message to a connectedDevice.
     *
     * @param connectedDevice {@link ConnectedDevice} to send the message to.
     * @param recipientId Recipient {@link ParcelUuid}.
     * @param message Message to send.
     * @return `true` if message was able to initiate, `false` if secure channel was not available.
     */
    boolean sendMessageSecurely(in ConnectedDevice connectedDevice, in ParcelUuid recipientId,
            in byte[] message);

    /**
     * Send an unencrypted message to a connectedDevice.
     *
     * @param connectedDevice {@link ConnectedDevice} to send the message to.
     * @param recipientId Recipient {@link ParcelUuid}.
     * @param message Message to send.
     */
    void sendMessageUnsecurely(in ConnectedDevice connectedDevice, in ParcelUuid recipientId,
            in byte[] message);

    /**
     * Register a callback for associated devic erelated events.
     *
     * @param callback {@link IDeviceAssociationCallback} to register.
     */
    void registerDeviceAssociationCallback(in IDeviceAssociationCallback callback);

    /**
     * Unregister a device association callback from manager.
     *
     * @param callback {@link IDeviceAssociationCallback} to unregister.
     */
    void unregisterDeviceAssociationCallback(in IDeviceAssociationCallback callback);

    /**
     * Register listener for the log request with the given logger identifier.
     *
     * @param listener {@link IOnLogRequestedListener} to register.
     */
    void registerOnLogRequestedListener(in int loggerId, in IOnLogRequestedListener listener);

    /**
     * Unregister listener from the log request.
     *
     * @param listener {@link IOnLogRequestedListener} to unregister.
     */
    void unregisterOnLogRequestedListener(in int loggerId, in IOnLogRequestedListener listener);

    /**
     * Process log records in the logger with given id so it can be combined with log records
     * from other loggers.
     *
     * @param loggerId of the logger.
     * @param logRecords to process.
     */
    void processLogRecords(in int loggerId, in byte[] logRecords);
}
