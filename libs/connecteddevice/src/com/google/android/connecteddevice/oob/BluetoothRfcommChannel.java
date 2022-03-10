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

package com.google.android.connecteddevice.oob;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.transport.BluetoothDeviceProvider;
import com.google.android.connecteddevice.transport.IConnectionProtocol;
import com.google.android.connecteddevice.transport.ProtocolDevice;
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder;
import com.google.android.connecteddevice.transport.spp.Connection;
import com.google.android.connecteddevice.transport.spp.PendingConnection;
import com.google.android.connecteddevice.transport.spp.PendingSentMessage;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Handles out of band data exchange over a secure RFCOMM channel. */
public class BluetoothRfcommChannel implements OobChannel {
  private static final String TAG = "BluetoothRfcommChannel";
  // TODO(b/159500330): Generate random UUID.
  private static final UUID RFCOMM_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
  private static final int CONNECTION_TIMEOUT_MS = 500;

  private final AtomicBoolean isInterrupted = new AtomicBoolean();
  private final ConnectedDeviceSppDelegateBinder sppDelegateBinder;
  private Connection activeConnection;
  private PendingConnection activePendingConnection;

  public BluetoothRfcommChannel(ConnectedDeviceSppDelegateBinder sppDelegateBinder) {
    this.sppDelegateBinder = sppDelegateBinder;
  }

  @Override
  public boolean completeOobDataExchange(@NonNull ProtocolDevice device, @NonNull byte[] oobData) {
    IConnectionProtocol protocol = device.getProtocol();
    if (!(protocol instanceof BluetoothDeviceProvider)) {
      logw(TAG, "Protocol is not supported by current OOB channel, ignored.");
      return false;
    }
    BluetoothDevice remoteDevice =
        ((BluetoothDeviceProvider) protocol).getBluetoothDeviceById(device.getProtocolId());
    completeOobDataExchange(
        remoteDevice, () -> BluetoothAdapter.getDefaultAdapter().getBondedDevices(), oobData);
    return true;
  }

  @VisibleForTesting
  void completeOobDataExchange(
      BluetoothDevice remoteDevice, BondedDevicesResolver bondedDevicesResolver, byte[] oobData) {
    Set<BluetoothDevice> bondedDevices = bondedDevicesResolver.getBondedDevices();
    if (bondedDevices == null || !bondedDevices.contains(remoteDevice)) {
      loge(
          TAG,
          "This device has not been bonded to device with address " + remoteDevice.getAddress());
      return;
    }

    try {
      PendingConnection connection =
          sppDelegateBinder.connectAsClient(RFCOMM_UUID, remoteDevice, /* isSecure= */ true);
      if (connection == null) {
        loge(TAG, "Connection with " + remoteDevice.getName() + " failed.");
        return;
      }

      activePendingConnection = connection;
      Handler handler = new Handler(Looper.getMainLooper());
      handler.postDelayed(
          () -> {
            logd(TAG, "Cancelling connection with " + remoteDevice.getName());
            try {
              sppDelegateBinder.cancelConnectionAttempt(connection);
            } catch (RemoteException e) {
              logw(TAG, "Failed to cancel connection attempt with " + remoteDevice.getName());
            }
            loge(TAG, "Connection with " + remoteDevice.getName() + " timed out.");
          },
          CONNECTION_TIMEOUT_MS);

      connection
          .setOnConnectedListener(
              (uuid, btDevice, isSecure, deviceName) -> {
                handler.removeCallbacksAndMessages(null);
                activePendingConnection = null;
                activeConnection =
                    new Connection(new ParcelUuid(uuid), btDevice, isSecure, deviceName);
                sendOobData(oobData);
              })
          .setOnConnectionErrorListener(
              () -> {
                handler.removeCallbacksAndMessages(null);
                loge(TAG, "Connection with " + remoteDevice.getName() + " failed.");
              });
    } catch (RemoteException e) {
      loge(TAG, "Connection with " + remoteDevice.getName() + " failed.", e);
    }
  }

  private void sendOobData(byte[] oobData) {
    if (isInterrupted.get()) {
      logd(TAG, "Oob connection is interrupted, data will not be set.");
      return;
    }

    if (activeConnection == null) {
      loge(TAG, "Connection is null, oob data cannot be sent");
      return;
    }

    try {
      PendingSentMessage pendingSentMessage =
          sppDelegateBinder.sendMessage(activeConnection, oobData);

      if (pendingSentMessage == null) {
        loge(TAG, "Sending oob data failed");
        return;
      }

      pendingSentMessage.setOnSuccessListener(
          () -> {
            try {
              disconnect();
            } catch (RemoteException e) {
              logw(TAG, "Sending oob data succeeded, but disconnect failed");
            }
          });
    } catch (RemoteException e) {
      loge(TAG, "Sending oob data failed", e);
    }
  }

  @Override
  public void interrupt() {
    logd(TAG, "Interrupt received.");
    isInterrupted.set(true);
    try {
      disconnect();
    } catch (RemoteException e) {
      loge(TAG, "Disconnect failed", e);
    }
  }

  private void disconnect() throws RemoteException {
    if (activeConnection != null) {
      sppDelegateBinder.disconnect(activeConnection);
      activeConnection = null;
    }
    if (activePendingConnection != null) {
      sppDelegateBinder.cancelConnectionAttempt(activePendingConnection);
      activePendingConnection = null;
    }
  }

  /**
   * Interface for determining all the devices that the current device is Bluetooth bonded to, used
   * for testing.
   */
  interface BondedDevicesResolver {
    Set<BluetoothDevice> getBondedDevices();
  }
}
