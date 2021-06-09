/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.connecteddevice.api;

import android.os.ParcelUuid;

import com.google.android.connecteddevice.api.IAssociationCallback;
import com.google.android.connecteddevice.api.IConnectionCallback;
import com.google.android.connecteddevice.api.IDeviceAssociationCallback;
import com.google.android.connecteddevice.api.IDeviceCallback;
import com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener;
import com.google.android.connecteddevice.api.IOnLogRequestedListener;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.DeviceMessage;

/** Coordinator between features and connected devices. */
interface IFeatureCoordinator {

    /** Returns {@link List<ConnectedDevice>} of devices currently connected. */
    List<ConnectedDevice> getConnectedDevicesForDriver();

    /**
     * Register a callback for connection events for only the driver's devices.
     *
     * @param callback {@link IConnectionCallback} to register.
     */
    void registerDriverConnectionCallback(in IConnectionCallback callback);

    /**
     * Register a callback for connection events for only passengers' devices.
     *
     * @param callback {@link IConnectionCallback} to register.
     */
    void registerPassengerConnectionCallback(in IConnectionCallback callback);

    /**
     * Register a callback for connection events for all devices.
     *
     * @param callback {@link IConnectionCallback} to register.
     */
    void registerAllConnectionCallback(in IConnectionCallback callback);

    /**
     * Unregister a connection callback.
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
     * Send a message to a connected device.
     *
     * @param connectedDevice {@link ConnectedDevice} to send the message to.
     * @param message Message to send.
     */
    boolean sendMessage(in ConnectedDevice connectedDevice, in DeviceMessage message);


    /**
     * Register a callback for associated devic related events.
     *
     * @param callback {@link IDeviceAssociationCallback} to register.
     */
    void registerDeviceAssociationCallback(in IDeviceAssociationCallback callback);

    /**
     * Unregister a device association callback.
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

    /** Starts the association with a new device. */
    void startAssociation(in IAssociationCallback callback);

    /**
     * Retrieve the devices associated with the active user from the database.
     *
     * @param listener {@link IOnAssociatedDevicesRetrievedListener} that will
     * be notified when the associated devices are retrieved.
     */
    void retrieveActiveUserAssociatedDevices(in IOnAssociatedDevicesRetrievedListener listener);

    /** Confirm the paring code. */
    void acceptVerification();

    /** Remove the associated device of the given identifier for the active user. */
    void removeAssociatedDevice(in String deviceId);

    /** Enable connection on the associated device with the given identifier. */
    void enableAssociatedDeviceConnection(in String deviceId);

    /** Disable connection on the associated device with the given identifier. */
    void disableAssociatedDeviceConnection(in String deviceId);
}
