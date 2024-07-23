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
import com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.DeviceMessage;

/**
 * Coordinator between features and connected devices.
 *
 * Only make additive changes to maintain backward compatibility.
 * The added function needs to be assigned the transaction value noted below,
 * and the value needs to be appropriately incremented.
 *
 * Next transaction value: 28
 */
interface IFeatureCoordinator {

    /**
     * Returns {@link List<ConnectedDevice>} of devices currently connected that
     * belong to the current driver.
     */
    List<ConnectedDevice> getConnectedDevicesForDriver() = 0;

    /**
     * Returns {@link List<ConnectedDevice>} of devices currently connected that
     * belong to any of the passengers.
     */
    List<ConnectedDevice> getConnectedDevicesForPassengers() = 1;

    /** Returns {@link List<ConnectedDevice>} of all devices currently connected. */
    List<ConnectedDevice> getAllConnectedDevices() = 2;

    /**
     * Registers a callback for connection events for only the driver's devices.
     *
     * @param callback {@link IConnectionCallback} to register.
     */
    void registerDriverConnectionCallback(in IConnectionCallback callback) = 3;

    /**
     * Registers a callback for connection events for only passengers' devices.
     *
     * @param callback {@link IConnectionCallback} to register.
     */
    void registerPassengerConnectionCallback(
            in IConnectionCallback callback) = 4;

    /**
     * Registers a callback for connection events for all devices.
     *
     * @param callback {@link IConnectionCallback} to register.
     */
    void registerAllConnectionCallback(in IConnectionCallback callback) = 5;

    /**
     * Unregisters a connection callback.
     *
     * @param callback {@link IConnectionCallback} to unregister.
     */
    void unregisterConnectionCallback(in IConnectionCallback callback) = 6;

    /**
     * Registers a callback for a specific connectedDevice and recipient.
     *
     * Duplicate registration with the same [recipientId] will block the
     * recipient and prevent it from receiving callbacks.
     *
     * @param connectedDevice {@link ConnectedDevice} to register triggers on.
     * @param recipientId {@link ParcelUuid} to register as recipient of.
     * @param callback {@link IDeviceCallback} to register.
     */
    void registerDeviceCallback(in ConnectedDevice connectedDevice,
            in ParcelUuid recipientId, in IDeviceCallback callback) = 7;

    /**
     * Unregisters callback from connectedDevice events.
     *
     * @param connectedDevice {@link ConnectedDevice} callback was registered on.
     * @param recipientId {@link ParcelUuid} callback was registered under.
     * @param callback {@link IDeviceCallback} to unregister.
     */
    void unregisterDeviceCallback(in ConnectedDevice connectedDevice,
            in ParcelUuid recipientId, in IDeviceCallback callback) = 8;

    /**
     * Sends a message to a connected device.
     *
     * @param connectedDevice {@link ConnectedDevice} to send the message to.
     * @param message Message to send.
     */
    boolean sendMessage(in ConnectedDevice connectedDevice,
            in DeviceMessage message) = 9;

    /**
     * Registers a callback for associated device related events.
     *
     * @param callback {@link IDeviceAssociationCallback} to register.
     */
    void registerDeviceAssociationCallback(
            in IDeviceAssociationCallback callback) = 10;

    /**
     * Unregisters a device association callback.
     *
     * @param callback {@link IDeviceAssociationCallback} to unregister.
     */
    void unregisterDeviceAssociationCallback(
            in IDeviceAssociationCallback callback) = 11;

    /**
     * Registers listener for the log request with the given logger identifier.
     *
     * @param listener {@link ISafeOnLogRequestedListener} to register.
     */
    void registerOnLogRequestedListener(in int loggerId,
            in ISafeOnLogRequestedListener listener) = 12;

    /**
     * Unregisters listener from the log request.
     *
     * @param listener {@link ISafeOnLogRequestedListener} to unregister.
     */
    void unregisterOnLogRequestedListener(in int loggerId,
            in ISafeOnLogRequestedListener listener) = 13;

    /**
     * Processes log records in the logger with given id so it can be combined
     * with log records from other loggers.
     *
     * @param loggerId of the logger.
     * @param logRecords to process.
     */
    void processLogRecords(in int loggerId, in byte[] logRecords) = 14;

    /** Starts the association with a new device. */
    void startAssociation(in IAssociationCallback callback) = 15;

    /** Stop the association process if it is still in progress. */
    void stopAssociation() = 16;

    /**
     * Retrieves all associated devices for all users.
     *
     * @param listener {@link IOnAssociatedDevicesRetrievedListener} that will
     * be notified when the associated devices are retrieved.
     */
    void retrieveAssociatedDevices(
            in IOnAssociatedDevicesRetrievedListener listener) = 17;

    /**
     * Retrieves associated devices belonging to the driver (the current user).
     *
     * @param listener {@link IOnAssociatedDevicesRetrievedListener} that will
     * be notified when the associated devices are retrieved.
     */
    void retrieveAssociatedDevicesForDriver(
            in IOnAssociatedDevicesRetrievedListener listener) = 18;

    /**
     * Retrieves associated devices belonging to all of the passengers.
     *
     * @param listener {@link IOnAssociatedDevicesRetrievedListener} that will
     * be notified when the associated devices are retrieved.
     */
    void retrieveAssociatedDevicesForPassengers(
            in IOnAssociatedDevicesRetrievedListener listener) = 19;

    /** Confirms the paring code. */
    void acceptVerification() = 20;

    /** Removes the associated device of the given identifier for the active user. */
    void removeAssociatedDevice(in String deviceId) = 21;

    /** Enables connection on the associated device with the given identifier. */
    void enableAssociatedDeviceConnection(in String deviceId) = 22;

    /** Disables connection on the associated device with the given identifier. */
    void disableAssociatedDeviceConnection(in String deviceId) = 23;

    /** Starts the association with a new device.
     *
     * @param callback {@link IAssociationCallback} that will be notified for assocaition events.
     * @param identifier {@link ParcelUuid} to identify the association.
     */
    void startAssociationWithIdentifier(in IAssociationCallback callback,
            in ParcelUuid identifier) = 24;

    /** Claim an associated device to belong to the current user. */
    void claimAssociatedDevice(in String deviceId) = 25;

    /** Remove the claim on the identified associated device. */
    void removeAssociatedDeviceClaim(in String deviceId) = 26;

    /**
     * Returns the support status of a feature on the phone side.
     *
     * Returns:
     * - a postive value if the feature is supported on the phone side;
     * - a negative value if the feature is NOT supported on the phone side;
     * - 0 if the status is unknown.
     */
    int isFeatureSupportedCached(in String deviceId, in String featureId) = 27;
}
