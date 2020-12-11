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

package com.google.android.connecteddevice.transport.spp;

import android.bluetooth.BluetoothDevice;
import android.os.ParcelUuid;
import com.google.android.connecteddevice.transport.spp.Connection;
import com.google.android.connecteddevice.transport.spp.ISppCallback;

interface IConnectedDeviceSppDelegate {

  void setCallback(in ISppCallback callback);
  void clearCallback();
  void notifyConnected(in int pendingConnectionId,
                       in BluetoothDevice remoteDevice,
                       in String deviceName);
  void notifyMessageReceived(in Connection connection, in byte[] message);
  void notifyConnectAttemptFailed(in int pendingConnectionId);
  void notifyError(in Connection connection);
}
