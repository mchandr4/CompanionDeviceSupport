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
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.model.OobEligibleDevice;
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder;
import com.google.android.connecteddevice.transport.spp.Connection;
import com.google.android.connecteddevice.transport.spp.PendingConnection;
import com.google.android.connecteddevice.transport.spp.PendingSentMessage;
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

  @VisibleForTesting Callback callback;

  public BluetoothRfcommChannel(ConnectedDeviceSppDelegateBinder sppDelegateBinder) {
    this.sppDelegateBinder = sppDelegateBinder;
  }

  @Override
  public void completeOobDataExchange(
      @NonNull OobEligibleDevice device, @NonNull Callback callback) {
    completeOobDataExchange(device, callback, BluetoothAdapter.getDefaultAdapter());
  }

  @VisibleForTesting
  void completeOobDataExchange(
      OobEligibleDevice device, Callback callback, BluetoothAdapter bluetoothAdapter) {
    this.callback = callback;

    BluetoothDevice remoteDevice = bluetoothAdapter.getRemoteDevice(device.getDeviceAddress());

    try {
      PendingConnection connection =
          sppDelegateBinder.connectAsClient(RFCOMM_UUID, remoteDevice, /* isSecure= */ true);
      if (connection == null) {
        notifyFailure(
            "Connection with " + remoteDevice.getName() + " failed.", /* exception= */ null);
        return;
      }

      Handler handler = new Handler(Looper.getMainLooper());
      handler.postDelayed(
          () -> {
            logd(TAG, "Cancelling connection with " + remoteDevice.getName());
            try {
              sppDelegateBinder.cancelConnectionAttempt(connection);
            } catch (RemoteException e) {
              logw(TAG, "Failed to cancel connection attempt with " + remoteDevice.getName());
            }
            notifyFailure(
                "Connection with " + remoteDevice.getName() + " timed out.", /* exception= */ null);
          },
          CONNECTION_TIMEOUT_MS);

      connection
          .setOnConnectedListener(
              (uuid, btDevice, isSecure, deviceName) -> {
                handler.removeCallbacksAndMessages(null);
                activeConnection =
                    new Connection(new ParcelUuid(uuid), btDevice, isSecure, deviceName);
                notifySuccess();
              })
          .setOnConnectionErrorListener(
              () -> {
                handler.removeCallbacksAndMessages(null);
                notifyFailure(
                    "Connection with " + remoteDevice.getName() + " failed.",
                    /* exception= */ null);
              });
    } catch (RemoteException e) {
      notifyFailure("Connection with " + remoteDevice.getName() + " failed.", e);
    }
  }

  @Override
  public void sendOobData(byte[] oobData) {
    if (isInterrupted.get()) {
      logd(TAG, "Oob connection is interrupted, data will not be set.");
      return;
    }

    if (activeConnection == null) {
      notifyFailure("Connection is null, oob data cannot be sent", /* exception= */ null);
      return;
    }
    try {
      PendingSentMessage pendingSentMessage =
          sppDelegateBinder.sendMessage(activeConnection, oobData);

      if (pendingSentMessage == null) {
        notifyFailure("Sending oob data failed", null);
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
      notifyFailure("Sending oob data failed", e);
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
    // TODO(b/169876111): Call to sppDelegateBinder.cancelConnectionAttempt
  }

  private void notifyFailure(@NonNull String message, @Nullable Exception exception) {
    loge(TAG, message, exception);
    if (callback != null && !isInterrupted.get()) {
      callback.onOobExchangeFailure();
    }
  }

  private void notifySuccess() {
    if (callback != null && !isInterrupted.get()) {
      callback.onOobExchangeSuccess();
    }
  }
}
