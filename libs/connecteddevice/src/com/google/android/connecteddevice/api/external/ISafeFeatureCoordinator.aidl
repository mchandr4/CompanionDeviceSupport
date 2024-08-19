/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.connecteddevice.api.external;

import android.os.ParcelUuid;
import com.google.android.connecteddevice.api.external.ISafeConnectionCallback;
import com.google.android.connecteddevice.api.external.ISafeDeviceCallback;
import com.google.android.connecteddevice.api.external.ISafeOnAssociatedDevicesRetrievedListener;
import com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener;

/**
 * Coordinator between external features and connected devices.
 *
 * To maintain backward and forward compatibility, this class should not pass
 * customized parcelables. Instead please use supported primitive types
 * mentioned in the Android official doc.
 * See <a href="https://developer.android.com/guide/components/aidl">Android AIDL</a>
 *
 * Only make additive changes to maintain backward compatibility.
 * The added function needs to be assigned the transaction value noted below,
 * and the value needs to be appropriately incremented.
 *
 * Next transaction value: 10
 */
// TODO Link to the internal documentation on rules when modify
// this class.
interface ISafeFeatureCoordinator {
    /**
     * Returns list of ids of currently connected devices that belong to the
     * current user.
     */
    List<String> getConnectedDevices() = 0;

    /**
     * Registers a callback for connection events for only the driver's devices.
     *
     * @param callback {@link ISafeConnectionCallback} to register.
     */
    void registerConnectionCallback(ISafeConnectionCallback callback) = 1;

    /**
     * Unregisters a connection callback.
     *
     * @param callback {@link ISafeConnectionCallback} to unregister.
     */
    void unregisterConnectionCallback(ISafeConnectionCallback callback) = 2;

    /**
     * Registers a callback for a specific connectedDevice and recipient.
     *
     * Duplicate registration with the same [recipientId] will block the
     * recipient and prevent it from receiving callbacks.
     *
     * @param deviceId {@link String} to register triggers on.
     * @param recipientId {@link ParcelUuid} to register as recipient of.
     * @param callback {@link ISafeDeviceCallback} to register.
     */
    void registerDeviceCallback(String deviceId, in ParcelUuid recipientId,
            ISafeDeviceCallback callback) = 3;

    /**
     * Unregisters callback from connectedDevice events. The request will be
     * ignored if there is no matching {@link ISafeDeviceCallback} registered
     * with the Companion platform
     *
     * @param deviceId {@link String} that callback was registered on.
     * @param recipientId {@link ParcelUuid} callback was registered under.
     * @param callback {@link ISafeDeviceCallback} to unregister.
     */
    void unregisterDeviceCallback(String deviceId, in ParcelUuid recipientId,
            ISafeDeviceCallback callback) = 4;

    /**
     * Sends a message to a connected device.
     *
     * @param deviceId {@link String} to send the message to.
     * @param message byte array message proto to send.
     */
    boolean sendMessage(String deviceId, in byte[] message) = 5;

    /**
     * Registers listener for the log request with the given logger identifier.
     *
     * @param listener {@link ISafeOnLogRequestedListener} to register.
     */
    void registerOnLogRequestedListener(int loggerId,
            ISafeOnLogRequestedListener listener) = 6;

    /**
     * Unregisters listener from the log request. The request will be
     * ignored if there is no matching {@link ISafeOnLogRequestedListener}
     * registered with the Companion platform
     *
     * @param listener {@link ISafeOnLogRequestedListener} to unregister.
     */
    void unregisterOnLogRequestedListener(int loggerId,
            ISafeOnLogRequestedListener listener) = 7;

    /**
     * Processes log records in the logger with given id so it can be combined
     * with log records from other loggers.
     *
     * @param loggerId of the logger.
     * @param logRecords to process.
     */
    void processLogRecords(int loggerId, in byte[] logRecords) = 8;

    /**
     * Retrieves all associated devices's ids for current user.
     *
     * @param listener {@link ISafeOnAssociatedDevicesRetrievedListener} that
     * will be notified when the associated devices are retrieved.
     */
    void retrieveAssociatedDevices(
        ISafeOnAssociatedDevicesRetrievedListener listener) = 9;
}
