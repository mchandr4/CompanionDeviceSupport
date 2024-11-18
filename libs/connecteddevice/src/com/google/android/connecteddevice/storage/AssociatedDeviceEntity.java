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

package com.google.android.connecteddevice.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.android.companionprotos.DeviceOS;
import com.google.android.connecteddevice.model.AssociatedDevice;

/** Table entity representing an associated device. */
@Entity(tableName = "associated_devices")
public class AssociatedDeviceEntity {

  /** Id of the device. */
  @PrimaryKey
  @NonNull
  public String id;

  /** Id of user associated with this device. */
  public int userId;

  /** Bluetooth address of the device. */
  @Nullable
  public String address;

  /** Associated device name. */
  @Nullable public String name;

  /** Operating system of this device. */
  @NonNull public DeviceOS os;

  /** Version of the operating system of this device. */
  @Nullable public String osVersion;

  /** The Companion SDK’s version running on this device. */
  @Nullable public String companionSdkVersion;

  /** {@code true} if the connection is enabled for this device. */
  public boolean isConnectionEnabled;

  public AssociatedDeviceEntity() {}

  public AssociatedDeviceEntity(
      int userId, AssociatedDevice associatedDevice, boolean isConnectionEnabled) {
    this.userId = userId;
    id = associatedDevice.getId();
    address = associatedDevice.getAddress();
    name = associatedDevice.getName();
    this.isConnectionEnabled = isConnectionEnabled;
    this.os = associatedDevice.getOs();
    this.osVersion = associatedDevice.getOsVersion();
    this.companionSdkVersion = associatedDevice.getCompanionSdkVersion();
  }

  /** Return a new {@link AssociatedDevice} of this entity. */
  public AssociatedDevice toAssociatedDevice() {
    return new AssociatedDevice(
        id, address, name, isConnectionEnabled, userId, os, osVersion, companionSdkVersion);
  }
}
