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

import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.DeviceMessage;

/** Triggered companionDevice events for a connected companionDevice. */
oneway interface IDeviceCallback {
    /**
     * Triggered when secure channel has been established on a companionDevice. Encrypted messaging
     * now available.
     */
    void onSecureChannelEstablished(in ConnectedDevice connectedDevice);

    /** Triggered when a new message is received from a connectedDevice. */
    void onMessageReceived(in ConnectedDevice connectedDevice, in DeviceMessage message);

    /** Triggered when an error has occurred for a connectedDevice. */
    void onDeviceError(in ConnectedDevice connectedDevice, int error);
}
