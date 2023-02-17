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

/**
 * Callback for triggered connection events.
 *
 * Only make additive changes to maintain backward compatibility.
 * The added function needs to be assigned the transaction value noted below,
 * and the value needs to be appropriately incremented.
 *
 * Next transaction value: 2
 */
oneway interface IConnectionCallback {
    /** Triggered when a new connectedDevice has connected. */
    void onDeviceConnected(in ConnectedDevice connectedDevice) = 0;

    /** Triggered when a connectedDevice has disconnected. */
    void onDeviceDisconnected(in ConnectedDevice connectedDevice) = 1;
}
