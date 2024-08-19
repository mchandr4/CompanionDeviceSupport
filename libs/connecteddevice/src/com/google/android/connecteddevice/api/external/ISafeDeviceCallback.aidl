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

/**
 * Triggered events for a connected companion device.
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
 * Next transaction value: 3
 */
// TODO Link to the internal documentation on rules for modifying
// this class.
oneway interface ISafeDeviceCallback {
    /**
     * Triggered when secure channel has been established on a device.
     * Encrypted messaging now available.
     *
     * @param deviceId that established the secure channel.
     */
    void onSecureChannelEstablished(String deviceId) = 0;

    /**
     * Triggered when a new message is received from a connected device.
     *
     * @param deviceId from which the message is received.
     * @param message byte array of the message received.
     */
    void onMessageReceived(String deviceId, in byte[] message) = 1;

    /**
     * Triggered when an error has occurred for a connected device.
     *
     * @param deviceId of the device which the error occured for.
     * @param error code {@link Errors} of the error happened.
     */
    void onDeviceError(String deviceId, int error) = 2;
}
