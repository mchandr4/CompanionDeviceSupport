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

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logi;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** This task runs when sending a connection request to remote device. */
class ConnectTask implements Runnable {
  private static final String TAG = "ConnectTask";
  private final Callback callback;
  private final Executor callbackExecutor;
  private final AtomicBoolean isCanceled = new AtomicBoolean(false);
  @VisibleForTesting BluetoothSocket socket;

  ConnectTask(
      BluetoothDevice device,
      boolean isSecure,
      UUID serviceUuid,
      Callback callback,
      Executor callbackExecutor) {
    this.callback = callback;
    this.callbackExecutor = callbackExecutor;
    try {
      socket =
          isSecure
              ? device.createRfcommSocketToServiceRecord(serviceUuid)
              : device.createInsecureRfcommSocketToServiceRecord(serviceUuid);
    } catch (IOException e) {
      loge(TAG, "Socket create() failed", e);
    }
  }

  @Override
  public void run() {
    if (socket == null) {
      loge(TAG, "Socket is null, can not begin ConnectTask");
      callbackExecutor.execute(callback::onConnectionAttemptFailed);
      return;
    }

    logi(TAG, "Begin ConnectTask.");

    // Always cancel discovery because it will slow down a connection
    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();

    try {
      // This is a blocking call and will only return on a successful connection or an exception
      socket.connect();
    } catch (IOException e) {
      loge(TAG, "Exception when connecting to device.", e);

      // Do not trigger attempt failed callback when the task is cancelled intentionally.
      if (!isCanceled.get()) {
        callbackExecutor.execute(callback::onConnectionAttemptFailed);
      }
      return;
    }

    if (!isCanceled.get()) {
      callbackExecutor.execute(() -> callback.onConnectionSuccess(socket));
    }
  }

  /**
   * Cancels this connection task and closes any {@link BluetoothSocket}s that have been created.
   */
  @SuppressWarnings("ObjectToString")
  public void cancel() {
    logd(TAG, "CANCEL ConnectTask. Current socket: " + socket);

    if (isCanceled.getAndSet(true)) {
      logw(TAG, "Task already canceled. Ignoring.");
      return;
    }

    if (socket == null) {
      return;
    }

    try {
      socket.close();
    } catch (IOException e) {
      loge(TAG, "close() of connect socket failed", e);
    }
  }

  interface Callback {

    /** Will be called when the {@link ConnectTask} completed successfully. */
    void onConnectionSuccess(@NonNull BluetoothSocket socket);

    /**
     * Called when connection failed at any stage. Further call on the current {@link ConnectTask}
     * object should be avoid.
     */
    void onConnectionAttemptFailed();
  }
}
