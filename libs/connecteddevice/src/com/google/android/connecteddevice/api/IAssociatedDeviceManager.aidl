/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.google.android.connecteddevice.api.IAssociationCallback;
import com.google.android.connecteddevice.api.IConnectionCallback;
import com.google.android.connecteddevice.api.IDeviceAssociationCallback;
import com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.OobEligibleDevice;

/** Manager of devices associated with the car. */
interface IAssociatedDeviceManager {
    /**
     * Starts the association with a new device.
     *
     * @param callback {@link IAssociationCallback} to be notified for association event.
     */
    void startAssociation(in IAssociationCallback callback);

    /** Stops the association with current device. */
    void stopAssociation();

    /**
     * Retrieves the devices associated with the active user from the database.
     *
     * @param listener {@link IOnAssociatedDevicesRetrievedListener} that will
     * be notified when the associated devices are retrieved.
     */
    void retrievedActiveUserAssociatedDevices(in IOnAssociatedDevicesRetrievedListener listener);

    /** Confirms the paring code. */
    void acceptVerification();

    /** Remove the associated device of the given identifier for the active user. */
    void removeAssociatedDevice(in String deviceId);

    /**
     * Register a callback for associated device related events.
     *
     * @param callback {@link IDeviceAssociationCallback} to register.
     */
    void registerDeviceAssociationCallback(in IDeviceAssociationCallback callback);

    /** Unregister the device association callback from manager.
     *
     * @param callback {@link IDeviceAssociationCallback} to unregister.
     */
    void unregisterDeviceAssociationCallback(in IDeviceAssociationCallback callback);

    /** Returns {@link List<ConnectedDevice>} of devices currently connected. */
    List<ConnectedDevice> getActiveUserConnectedDevices();

    /**
     * Register a callback for connection events for only the currently active user's devices.
     *
     * @param callback {@link IConnectionCallback} to register.
     */
    void registerConnectionCallback(in IConnectionCallback callback);

    /**
     * Unregister the connection callback from manager.
     *
     * @param callback {@link IConnectionCallback} to unregister.
     */
    void unregisterConnectionCallback(in IConnectionCallback callback);

    /** Enable connection on the associated device with the given identifier. */
    void enableAssociatedDeviceConnection(in String deviceId);

    /** Disable connection on the associated device with the given identifier. */
    void disableAssociatedDeviceConnection(in String deviceId);
}
