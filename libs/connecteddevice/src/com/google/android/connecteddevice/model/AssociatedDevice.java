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

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Objects;

/** Contains basic info of an associated device. */
public class AssociatedDevice implements Parcelable {
  /** Default placeholder userId value for an unclaimed device. */
  public static final int UNCLAIMED_USER_ID = -1;

  private final String deviceId;

  private final String deviceAddress;

  private final String deviceName;

  private final boolean isConnectionEnabled;

  private final int userId;

  /**
   * Create a new AssociatedDevice.
   *
   * @param deviceId Id of the associated device.
   * @param deviceAddress Address of the associated device.
   * @param deviceName Name of the associated device. {@code null} if not known.
   * @param isConnectionEnabled If connection is enabled for this device.
   */
  public AssociatedDevice(
      @NonNull String deviceId,
      @NonNull String deviceAddress,
      @Nullable String deviceName,
      boolean isConnectionEnabled
  ) {
    this(deviceId, deviceAddress, deviceName, isConnectionEnabled, UNCLAIMED_USER_ID);
  }

  /**
   * Create a new AssociatedDevice.
   *
   * @param deviceId Id of the associated device.
   * @param deviceAddress Address of the associated device.
   * @param deviceName Name of the associated device. {@code null} if not known.
   * @param isConnectionEnabled If connection is enabled for this device.
   * @param userId Id of the claiming user, or {@value #UNCLAIMED_USER_ID} if unclaimed.
   */
  public AssociatedDevice(
      @NonNull String deviceId,
      @NonNull String deviceAddress,
      @Nullable String deviceName,
      boolean isConnectionEnabled,
      int userId) {
    this.deviceId = deviceId;
    this.deviceAddress = deviceAddress;
    this.deviceName = deviceName;
    this.isConnectionEnabled = isConnectionEnabled;
    this.userId = userId;
  }

  private AssociatedDevice(Parcel in) {
    this(in.readString(), in.readString(), in.readString(), in.readBoolean(), in.readInt());
  }

  /** Returns the id for this device. */
  @NonNull
  public String getDeviceId() {
    return deviceId;
  }

  /** Returns the address for this device. */
  @NonNull
  public String getDeviceAddress() {
    return deviceAddress;
  }

  /** Returns the name for this device or {@code null} if not known. */
  @Nullable
  public String getDeviceName() {
    return deviceName;
  }

  /** Return if connection is enabled for this device. */
  public boolean isConnectionEnabled() {
    return isConnectionEnabled;
  }

  /** Returns the id of the claiming user, or {@value #UNCLAIMED_USER_ID} if not claimed. */
  public int getUserId() {
    return userId;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof AssociatedDevice)) {
      return false;
    }
    AssociatedDevice associatedDevice = (AssociatedDevice) obj;
    return Objects.equals(deviceId, associatedDevice.deviceId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(deviceId);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(deviceId);
    dest.writeString(deviceAddress);
    dest.writeString(deviceName);
    dest.writeBoolean(isConnectionEnabled);
    dest.writeInt(userId);
  }

  // Explicitly specifying the type within <> to support building with Java 8.
  public static final Parcelable.Creator<AssociatedDevice> CREATOR =
      new Parcelable.Creator<AssociatedDevice>() {
        @Override
        public AssociatedDevice createFromParcel(Parcel source) {
          return new AssociatedDevice(source);
        }

        @Override
        public AssociatedDevice[] newArray(int size) {
          return new AssociatedDevice[size];
        }
      };
}
