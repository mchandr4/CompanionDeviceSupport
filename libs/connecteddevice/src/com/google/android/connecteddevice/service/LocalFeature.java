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

package com.google.android.connecteddevice.service;

import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.content.Context;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.ConnectedDeviceManager;
import com.google.android.connecteddevice.ConnectedDeviceManager.ConnectionCallback;
import com.google.android.connecteddevice.ConnectedDeviceManager.DeviceCallback;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.Errors.DeviceError;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Base class for features local to the service. */
public abstract class LocalFeature {

  private static final String TAG = "LocalFeature";

  private final Context context;

  private final ConnectedDeviceManager connectedDeviceManager;

  private final UUID featureId;

  private final Executor executor = Executors.newSingleThreadExecutor();

  protected LocalFeature(
      @NonNull Context context,
      @NonNull ConnectedDeviceManager connectedDeviceManager,
      @NonNull UUID featureId) {
    this.context = context;
    this.connectedDeviceManager = connectedDeviceManager;
    this.featureId = featureId;
  }

  /** Start setup process and subscribe callbacks with {@link ConnectedDeviceManager}. */
  @CallSuper
  public void start() {
    connectedDeviceManager.registerActiveUserConnectionCallback(connectionCallback, executor);

    List<ConnectedDevice> activeUsersDevices =
        connectedDeviceManager.getActiveUserConnectedDevices();
    for (ConnectedDevice device : activeUsersDevices) {
      connectedDeviceManager.registerDeviceCallback(
          device, featureId, deviceCallback, executor);
    }
  }

  /** Stop execution and clean up callbacks established by this feature. */
  @CallSuper
  public void stop() {
    connectedDeviceManager.unregisterConnectionCallback(connectionCallback);
    for (ConnectedDevice device : connectedDeviceManager.getActiveUserConnectedDevices()) {
      connectedDeviceManager.unregisterDeviceCallback(device, featureId, deviceCallback);
    }
  }

  /** Return the {@link Context} registered with the feature. */
  @NonNull
  public Context getContext() {
    return context;
  }

  /** Return the {@link ConnectedDeviceManager} registered with the feature. */
  @NonNull
  public ConnectedDeviceManager getConnectedDeviceManager() {
    return connectedDeviceManager;
  }

  /** Return the {@link UUID} feature id registered for the feature. */
  @NonNull
  public UUID getFeatureId() {
    return featureId;
  }

  /** Securely send message to a device. */
  public void sendMessageSecurely(@NonNull String deviceId, @NonNull byte[] message) {
    ConnectedDevice device = getConnectedDeviceById(deviceId);
    if (device == null) {
      loge(
          TAG,
          "No matching device found with id "
              + deviceId
              + " when trying to send "
              + "secure message.");
      onMessageFailedToSend(deviceId, message);
      return;
    }

    sendMessageSecurely(device, message);
  }

  /** Securely send message to a device. */
  public void sendMessageSecurely(@NonNull ConnectedDevice device, byte[] message) {
    getConnectedDeviceManager().sendMessageSecurely(device, getFeatureId(), message);
  }

  /** Send a message to a device without encryption. */
  public void sendMessageUnsecurely(@NonNull String deviceId, @NonNull byte[] message) {
    ConnectedDevice device = getConnectedDeviceById(deviceId);
    if (device == null) {
      loge(
          TAG,
          "No matching device found with id "
              + deviceId
              + " when trying to send "
              + "unsecure message.");
      onMessageFailedToSend(deviceId, message);
      return;
    }

    sendMessageUnsecurely(device, message);
  }

  /** Send a message to a device without encryption. */
  public void sendMessageUnsecurely(@NonNull ConnectedDevice device, @NonNull byte[] message) {
    getConnectedDeviceManager().sendMessageUnsecurely(device, getFeatureId(), message);
  }

  /**
   * Return the {@link ConnectedDevice} with a matching device id for the currently active user.
   * Returns {@code null} if no match found.
   */
  @Nullable
  public ConnectedDevice getConnectedDeviceById(@NonNull String deviceId) {
    List<ConnectedDevice> connectedDevices =
        getConnectedDeviceManager().getActiveUserConnectedDevices();

    for (ConnectedDevice device : connectedDevices) {
      if (device.getDeviceId().equals(deviceId)) {
        return device;
      }
    }

    return null;
  }

  // These can be overridden to perform custom actions.

  /** Called when a new {@link ConnectedDevice} is connected. */
  protected void onDeviceConnected(@NonNull ConnectedDevice device) {}

  /** Called when a {@link ConnectedDevice} disconnects. */
  protected void onDeviceDisconnected(@NonNull ConnectedDevice device) {}

  /** Called when a secure channel has been established with a {@link ConnectedDevice}. */
  protected void onSecureChannelEstablished(@NonNull ConnectedDevice device) {}

  /** Called when a new {@link byte[]} message is received for this feature. */
  protected void onMessageReceived(@NonNull ConnectedDevice device, @NonNull byte[] message) {}

  /**
   * Called when a message fails to send to a device.
   *
   * @param deviceId Id of the device the message failed to send to.
   * @param message Message to send.
   */
  protected void onMessageFailedToSend(@NonNull String deviceId, @NonNull byte[] message) {}

  /** Called when an error has occurred with the connection. */
  protected void onDeviceError(@NonNull ConnectedDevice device, @DeviceError int error) {}

  private final ConnectionCallback connectionCallback =
      new ConnectionCallback() {
        @Override
        public void onDeviceConnected(ConnectedDevice device) {
          connectedDeviceManager.registerDeviceCallback(
              device, featureId, deviceCallback, executor);
          LocalFeature.this.onDeviceConnected(device);
        }

        @Override
        public void onDeviceDisconnected(ConnectedDevice device) {
          connectedDeviceManager.unregisterDeviceCallback(device, featureId, deviceCallback);
          LocalFeature.this.onDeviceDisconnected(device);
        }
      };

  private final DeviceCallback deviceCallback =
      new DeviceCallback() {
        @Override
        public void onSecureChannelEstablished(ConnectedDevice device) {
          LocalFeature.this.onSecureChannelEstablished(device);
        }

        @Override
        public void onMessageReceived(ConnectedDevice device, byte[] message) {
          LocalFeature.this.onMessageReceived(device, message);
        }

        @Override
        public void onDeviceError(ConnectedDevice device, @DeviceError int error) {
          LocalFeature.this.onDeviceError(device, error);
        }
      };
}
