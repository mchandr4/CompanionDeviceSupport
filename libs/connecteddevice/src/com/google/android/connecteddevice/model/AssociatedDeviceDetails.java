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

package com.google.android.connecteddevice.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Class that contains the details of an associated device. */
public class AssociatedDeviceDetails {
  private final AssociatedDevice device;

  private final boolean isConnected;

  public AssociatedDeviceDetails(@NonNull AssociatedDevice device, boolean isConnected) {
    this.device = device;
    this.isConnected = isConnected;
  }

  /** Get the device id. */
  @NonNull
  public String getDeviceId() {
    return device.getDeviceId();
  }

  /** Get the name of the associated device. */
  @Nullable
  public String getDeviceName() {
    return device.getDeviceName();
  }

  /** Get the device address. */
  @NonNull
  public String getDeviceAddress() {
    return device.getDeviceAddress();
  }

  /** {@code true} if the connection is enabled for the device. */
  public boolean isConnectionEnabled() {
    return device.isConnectionEnabled();
  }

  /** {@code true} if the device is connected. */
  public boolean isConnected() {
    return isConnected;
  }

  /** Returns the claiming user id, or {@value AssociatedDevice#UNCLAIMED_USER_ID} if unclaimed. */
  public int getUserId() {
    return device.getUserId();
  }

  /** Get {@link AssociatedDevice}. */
  @NonNull
  public AssociatedDevice getAssociatedDevice() {
    return device;
  }
}
