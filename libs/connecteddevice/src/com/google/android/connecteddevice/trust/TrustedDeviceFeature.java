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

package com.google.android.connecteddevice.trust;

import android.content.Context;
import android.os.ParcelUuid;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.api.Connector;
import com.google.android.connecteddevice.api.RemoteFeature;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;

/** Feature wrapper for trusted device. */
class TrustedDeviceFeature extends RemoteFeature {

  private static final String TAG = "TrustedDeviceFeature";

  @VisibleForTesting
  static final ParcelUuid FEATURE_ID =
      ParcelUuid.fromString("85dff28b-3036-4662-bb22-baa7f898dc47");

  private Callback callback;

  private AssociatedDeviceCallback associatedDeviceCallback;

  TrustedDeviceFeature(@NonNull Context context) {
    super(context, FEATURE_ID);
  }

  @VisibleForTesting
  TrustedDeviceFeature(@NonNull Context context, @NonNull Connector connector) {
    super(context, FEATURE_ID, connector);
  }

  /** Set a {@link Callback} for events from the device. Set {@code null} to clear. */
  void setCallback(@Nullable Callback callback) {
    this.callback = callback;
  }

  /** Set an {@link AssociatedDeviceCallback} for associated device events. */
  void setAssociatedDeviceCallback(@NonNull AssociatedDeviceCallback callback) {
    associatedDeviceCallback = callback;
  }

  /** Clear the callback of associated device events. */
  void clearAssociatedDeviceCallback() {
    associatedDeviceCallback = null;
  }

  @Override
  protected void onMessageReceived(ConnectedDevice device, byte[] message) {
    if (callback != null) {
      callback.onMessageReceived(device, message);
    }
  }

  @Override
  protected void onDeviceError(ConnectedDevice device, int error) {
    if (callback != null) {
      callback.onDeviceError(device, error);
    }
  }

  @Override
  protected void onSecureChannelEstablished(@NonNull ConnectedDevice device) {
    if (callback != null) {
      callback.onSecureChannelEstablished(device);
    }
  }

  @Override
  protected void onAssociatedDeviceAdded(AssociatedDevice device) {
    if (associatedDeviceCallback != null) {
      associatedDeviceCallback.onAssociatedDeviceAdded(device);
    }
  }

  @Override
  protected void onAssociatedDeviceRemoved(AssociatedDevice device) {
    if (associatedDeviceCallback != null) {
      associatedDeviceCallback.onAssociatedDeviceRemoved(device);
    }
  }

  @Override
  protected void onAssociatedDeviceUpdated(AssociatedDevice device) {
    if (associatedDeviceCallback != null) {
      associatedDeviceCallback.onAssociatedDeviceUpdated(device);
    }
  }

  interface Callback {
    /** Called when a new device has connected and can be sent secure messages. */
    void onSecureChannelEstablished(@NonNull ConnectedDevice device);

    /** Called when a new {@link byte[]} message is received for this feature. */
    void onMessageReceived(@NonNull ConnectedDevice device, @NonNull byte[] message);

    /** Called when an error has occurred with the connection. */
    void onDeviceError(@NonNull ConnectedDevice device, int error);
  }

  interface AssociatedDeviceCallback {
    /** Called when a new {@link AssociatedDevice} is added for the given user. */
    void onAssociatedDeviceAdded(@NonNull AssociatedDevice device);

    /** Called when an {@link AssociatedDevice} is removed for the given user. */
    void onAssociatedDeviceRemoved(AssociatedDevice device);

    /** Called when an {@link AssociatedDevice} is updated for the given user. */
    void onAssociatedDeviceUpdated(@NonNull AssociatedDevice device);
  }
}
