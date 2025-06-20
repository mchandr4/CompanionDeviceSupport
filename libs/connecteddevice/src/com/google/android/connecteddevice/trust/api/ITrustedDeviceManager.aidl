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

package com.google.android.connecteddevice.trust.api;

import com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback;
import com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback;
import com.google.android.connecteddevice.trust.api.IOnTrustedDevicesRetrievedListener;
import com.google.android.connecteddevice.trust.api.TrustedDevice;
import com.google.android.connecteddevice.api.IDeviceAssociationCallback;
import com.google.android.connecteddevice.model.ConnectedDevice;

/**
 * Manager of trusted devices with the car to be used by any service/activity that needs to interact
 * with trusted devices.
 */
interface ITrustedDeviceManager {

    /** Send unlock request to the mobile device. */
    void sendUnlockRequest();

    /** Indicate the escrow token has been added for a user and corresponding handle. */
    void onEscrowTokenAdded(in int userId, in long handle);

    /** Indicate the escrow token has been activated for a user and corresponding handle. */
    void onEscrowTokenActivated(in int userId, in long handle);

    /** Indicate when the user has successfully confirmed their credential. */
    void onCredentialVerified();

    /** Indicate the device has been unlocked for current user. */
    void onUserUnlocked();

    /** Register a new callback for trusted device events. */
    void registerTrustedDeviceCallback(in ITrustedDeviceCallback callback);

    /** Remove a previously registered callback. */
    void unregisterTrustedDeviceCallback(in ITrustedDeviceCallback callback);

    /** Register a new callback for enrollment triggered events. */
    void registerTrustedDeviceEnrollmentCallback(in ITrustedDeviceEnrollmentCallback callback);

    /** Remove a previously registered callback. */
    void unregisterTrustedDeviceEnrollmentCallback(in ITrustedDeviceEnrollmentCallback callback);

    /** Register a new callback for trusted device enrollment notification request. */
    void registerTrustedDeviceEnrollmentNotificationCallback(
             in IOnTrustedDeviceEnrollmentNotificationCallback callback);

    /** Remove a new callback for trusted device enrollment notification request. */
    void unregisterTrustedDeviceEnrollmentNotificationCallback(
             in IOnTrustedDeviceEnrollmentNotificationCallback callback);

    /** Set a delegate for TrustAgent operation calls. */
    void setTrustedDeviceAgentDelegate(in ITrustedDeviceAgentDelegate trustAgentDelegate);

    /** Remove a prevoiusly set delegate with device secure status. */
    void clearTrustedDeviceAgentDelegate(
             in ITrustedDeviceAgentDelegate trustAgentDelegate,
             in boolean isDeviceSecure);

    /** Retrieves trusted devices for the active user. */
    void retrieveTrustedDevicesForActiveUser(in IOnTrustedDevicesRetrievedListener listener);

    /** Removes a trusted device and invalidate any credentials associated with it. */
    void removeTrustedDevice(in TrustedDevice trustedDevice);

    /** Returns {@link List<ConnectedDevice>} of devices currently connected. */
    List<ConnectedDevice> getActiveUserConnectedDevices();

    /** Registers a new callback for associated device events. */
    void registerAssociatedDeviceCallback(in IDeviceAssociationCallback callback);

    /** Removes a previously registered callback. */
    void unregisterAssociatedDeviceCallback(IDeviceAssociationCallback callback);

    /** Attempts to initiate trusted device enrollment on the phone with the given device id. */
    void initiateEnrollment(in String deviceId);

    /**
     * Processes trusted device enrollment.
     *
     * @param isDeviceSecure {@code true} if the car is secured with a lockscreen.
     */
    void processEnrollment(boolean isDeviceSecure);

    /** Aborts an ongoing enrollment. */
    void abortEnrollment();
}
