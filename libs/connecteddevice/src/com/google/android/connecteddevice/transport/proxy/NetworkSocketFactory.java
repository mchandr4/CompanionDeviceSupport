/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.google.android.connecteddevice.transport.proxy;

import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logi;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

/** Creates a socket over an active network. */
public final class NetworkSocketFactory implements SocketFactory {
  // Reuse TAG for simplicity.
  private static final String TAG = "NetworkSocketFactory";

  // Pre-allocated address by Android emulator from
  // https://developer.android.com/studio/run/emulator-networking
  private static final byte[] HOST_LOOPBACK_ADDRESS = {10, 0, 2, 2};

  // Ensure the host running the proxy service listens to the same port.
  private static final int PORT = 12120;

  private final Context context;

  // Socket (used by proxy) depends on active network, which might not be available when the
  // containing service is brought up on boot.
  // This value being counted down to zero indicates an active network is ready.
  private final CountDownLatch networkReadySignal = new CountDownLatch(1);

  public NetworkSocketFactory(Context context) {
    this.context = context;
  }

  /**
   * Creates a socket over an active network.
   *
   * <p>This method will block waiting for an active network.
   *
   * <p>Returns null if the wait for network was interrupted or socket creation threw exception.
   */
  @Override
  @Nullable
  public Socket createSocket() {
    ensureNetworkIsReady(context.getSystemService(ConnectivityManager.class));

    logi(TAG, "Start waiting for active network.");
    try {
      networkReadySignal.await();
    } catch (InterruptedException e) {
      loge(TAG, "Thread interrupted waiting for active network. ", e);
      return null;
    }

    logi(TAG, "Setting up socket for proxy.");
    Socket socket = null;
    try {
      InetAddress address = InetAddress.getByAddress(HOST_LOOPBACK_ADDRESS);
      socket = new Socket(address, PORT);
      socket.setKeepAlive(true);
    } catch (IOException e) {
      loge(TAG, "Could not create new socket.", e);
    }
    return socket;
  }

  /** Ensures {@link #networkReadySignal} will be counted down to zero. */
  private void ensureNetworkIsReady(ConnectivityManager connectivityManager) {
    if (connectivityManager.getActiveNetwork() != null) {
      logi(TAG, "Active network is already available.");
      networkReadySignal.countDown();
      return;
    }

    logi(TAG, "Waiting for active network.");
    connectivityManager.registerDefaultNetworkCallback(
        new ConnectivityManager.NetworkCallback() {
          @Override
          public void onAvailable(Network network) {
            logi(TAG, "Received callback for active network:" + network);
            networkReadySignal.countDown();
            connectivityManager.unregisterNetworkCallback(this);
          }
        });
  }
}
