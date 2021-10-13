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

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.content.Context;
import android.os.ParcelUuid;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.api.Connector.AppNameCallback;
import com.google.android.connecteddevice.api.Connector.QueryCallback;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import java.util.List;

/**
 * Base class for a feature that must bind to {@code ConnectedDeviceService}. Callbacks are
 * registered automatically and events are forwarded to internal methods. Override these to add
 * custom logic for callback triggers.
 */
public abstract class RemoteFeature {

  private static final String TAG = "RemoteFeature";

  /** Intent action used to request a device be associated. */
  public static final String ACTION_ASSOCIATION_SETTING =
      "com.google.android.connecteddevice.api.ASSOCIATION_ACTIVITY";

  /** Data name for associated device. */
  public static final String ASSOCIATED_DEVICE_DATA_NAME_EXTRA =
      "com.google.android.connecteddevice.api.ASSOCIATED_DEVICE";

  private final Context context;

  private final ParcelUuid featureId;

  private final Connector connector;

  /**
   * Creates a new RemoteFeature.
   *
   * @param context {@link Context} of the application process
   * @param featureId The id for this feature
   */
  protected RemoteFeature(@NonNull Context context, @NonNull ParcelUuid featureId) {
    this(context, featureId, /* forceFgUserBind= */ false);
  }

  /**
   * Creates a new RemoteFeature.
   *
   * @param context {@link Context} of the application process
   * @param featureId The id for this feature
   * @param forceFgUserBind Force binding to the foreground user service to avoid a cross-user bind
   */
  protected RemoteFeature(
      @NonNull Context context, @NonNull ParcelUuid featureId, boolean forceFgUserBind) {
    this(context, featureId, new CompanionConnector(context, forceFgUserBind));
  }

  protected RemoteFeature(
      @NonNull Context context, @NonNull ParcelUuid featureId, Connector connector) {
    this.context = context;
    this.featureId = featureId;
    this.connector = connector;
    connector.setFeatureId(featureId);
    connector.setCallback(
        new Connector.Callback() {
          @Override
          public void onConnected() {
            logd(TAG, "Successfully connected. Initializing feature.");
            onReady();
          }

          @Override
          public void onDisconnected() {
            logd(TAG, "Disconnected from companion. Stopping feature.");
            onNotReady();
          }

          @Override
          public void onFailedToConnect() {
            loge(TAG, "Failed to connect. Stopping feature.");
            onNotReady();
          }

          @Override
          public void onAssociatedDeviceUpdated(@NonNull AssociatedDevice device) {
            RemoteFeature.this.onAssociatedDeviceUpdated(device);
          }

          @Override
          public void onAssociatedDeviceRemoved(@NonNull AssociatedDevice device) {
            RemoteFeature.this.onAssociatedDeviceRemoved(device);
          }

          @Override
          public void onAssociatedDeviceAdded(@NonNull AssociatedDevice device) {
            RemoteFeature.this.onAssociatedDeviceAdded(device);
          }

          @Override
          public void onDeviceError(@NonNull ConnectedDevice device, int error) {
            RemoteFeature.this.onDeviceError(device, error);
          }

          @Override
          public void onQueryReceived(
              @NonNull ConnectedDevice device,
              int queryId,
              @NonNull byte[] request,
              @NonNull byte[] parameters) {
            RemoteFeature.this.onQueryReceived(device, queryId, request, parameters);
          }

          @Override
          public void onMessageReceived(@NonNull ConnectedDevice device, @NonNull byte[] message) {
            RemoteFeature.this.onMessageReceived(device, message);
          }

          @Override
          public void onMessageFailedToSend(
              @NonNull String deviceId, @NonNull byte[] message, boolean isTransient) {
            RemoteFeature.this.onMessageFailedToSend(deviceId, message, isTransient);
          }

          @Override
          public void onSecureChannelEstablished(@NonNull ConnectedDevice device) {
            RemoteFeature.this.onSecureChannelEstablished(device);
          }

          @Override
          public void onDeviceDisconnected(@NonNull ConnectedDevice device) {
            RemoteFeature.this.onDeviceDisconnected(device);
          }

          @Override
          public void onDeviceConnected(@NonNull ConnectedDevice device) {
            RemoteFeature.this.onDeviceConnected(device);
          }
        });
  }

  /** Starts setup process and begins connecting to the platform. */
  @CallSuper
  public void start() {
    connector.connect();
  }

  /** Stops this feature and disconnects from the platform. */
  @CallSuper
  public void stop() {
    connector.disconnect();
  }

  /** Returns the {@link Context} registered with the feature. */
  @NonNull
  public Context getContext() {
    return context;
  }

  /** Returns the {@link ParcelUuid} feature id registered for the feature. */
  @NonNull
  public ParcelUuid getFeatureId() {
    return featureId;
  }

  /** Securely sends message to a device. */
  public void sendMessageSecurely(@NonNull String deviceId, @NonNull byte[] message) {
    connector.sendMessageSecurely(deviceId, message);
  }

  /** Securely sends message to a device. */
  public void sendMessageSecurely(@NonNull ConnectedDevice device, @NonNull byte[] message) {
    connector.sendMessageSecurely(device, message);
  }

  /** Securely sends a query to a device and register a {@link QueryCallback} for a response. */
  public void sendQuerySecurely(
      @NonNull String deviceId,
      @NonNull byte[] request,
      @Nullable byte[] parameters,
      @NonNull QueryCallback callback) {
    connector.sendQuerySecurely(deviceId, request, parameters, callback);
  }

  /** Securely sends a query to a device and register a {@link QueryCallback} for a response. */
  public void sendQuerySecurely(
      @NonNull ConnectedDevice device,
      @NonNull byte[] request,
      @Nullable byte[] parameters,
      @NonNull QueryCallback callback) {
    connector.sendQuerySecurely(device, request, parameters, callback);
  }

  /** Sends a secure response to a query with an indication of whether it was successful. */
  public void respondToQuerySecurely(
      @NonNull ConnectedDevice device, int queryId, boolean success, @Nullable byte[] response) {
    connector.respondToQuerySecurely(device, queryId, success, response);
  }

  /**
   * Returns the {@link ConnectedDevice} with a matching device id for the currently active user.
   * Returns {@code null} if no match found.
   */
  @Nullable
  public ConnectedDevice getConnectedDeviceById(@NonNull String deviceId) {
    return connector.getConnectedDeviceById(deviceId);
  }

  /** Queries the {@link ConnectedDevice} for its companion application name. */
  public void getCompanionApplicationName(ConnectedDevice device, AppNameCallback callback) {
    connector.retrieveCompanionApplicationName(device, callback);
  }

  /** Returns all the currently connected devices. */
  public List<ConnectedDevice> getConnectedDevices() {
    return connector.getConnectedDevices();
  }

  // These can be overridden to perform custom actions.

  /** Called when the platform has connected and is ready for interaction. */
  protected void onReady() {}

  /** Called when the platform has disconnected and is no longer ready for interaction. */
  protected void onNotReady() {}

  /** Called when a new {@link ConnectedDevice} is connected. */
  protected void onDeviceConnected(@NonNull ConnectedDevice device) {}

  /** Called when a {@link ConnectedDevice} disconnects. */
  protected void onDeviceDisconnected(@NonNull ConnectedDevice device) {}

  /** Called when a secure channel has been established with a {@link ConnectedDevice}. */
  protected void onSecureChannelEstablished(@NonNull ConnectedDevice device) {}

  /**
   * Called when a message fails to send to a device.
   *
   * @param deviceId Id of the device the message failed to send to.
   * @param message Message to send.
   * @param isTransient {@code true} if cause of failure is transient and can be retried. {@code
   *     false} if failure is permanent.
   */
  protected void onMessageFailedToSend(
      @NonNull String deviceId,
      @NonNull byte[] message,
      boolean isTransient) {}

  /** Called when a new {@link byte[]} message is received for this feature. */
  protected void onMessageReceived(@NonNull ConnectedDevice device, @NonNull byte[] message) {}

  /** Called when a new query is received for this feature. */
  protected void onQueryReceived(
      @NonNull ConnectedDevice device,
      int queryId,
      @NonNull byte[] request,
      @NonNull byte[] parameters) {}

  /** Called when an error has occurred with the connection. */
  protected void onDeviceError(@NonNull ConnectedDevice device, int error) {}

  /** Called when a new {@link AssociatedDevice} is added for the given user. */
  protected void onAssociatedDeviceAdded(@NonNull AssociatedDevice device) {}

  /** Called when an {@link AssociatedDevice} is removed for the given user. */
  protected void onAssociatedDeviceRemoved(@NonNull AssociatedDevice device) {}

  /** Called when an {@link AssociatedDevice} is updated for the given user. */
  protected void onAssociatedDeviceUpdated(@NonNull AssociatedDevice device) {}
}
