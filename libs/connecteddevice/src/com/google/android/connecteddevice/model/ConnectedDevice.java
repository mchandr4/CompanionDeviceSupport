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

/** View model representing a connected device. */
public class ConnectedDevice implements Parcelable {

  private final String deviceId;

  private final String deviceName;

  private final boolean belongsToDriver;

  private final boolean hasSecureChannel;

  /**
   * Create a new connected device.
   *
   * @param deviceId Id of the connected device.
   * @param deviceName Name of the connected device. {@code null} if not known.
   * @param belongsToDriver User associated with current driver's user profile.
   * @param hasSecureChannel {@code true} if a secure channel is available for this device.
   */
  public ConnectedDevice(
      @NonNull String deviceId,
      @Nullable String deviceName,
      boolean belongsToDriver,
      boolean hasSecureChannel) {
    this.deviceId = deviceId;
    this.deviceName = deviceName;
    this.belongsToDriver = belongsToDriver;
    this.hasSecureChannel = hasSecureChannel;
  }

  private ConnectedDevice(Parcel in) {
    this(in.readString(), in.readString(), in.readBoolean(), in.readBoolean());
  }

  /** Returns the id for this device. */
  @NonNull
  public String getDeviceId() {
    return deviceId;
  }

  /** Returns the name for this device or {@code null} if not known. */
  @Nullable
  public String getDeviceName() {
    return deviceName;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(deviceId);
    dest.writeString(deviceName);
    dest.writeBoolean(belongsToDriver);
    dest.writeBoolean(hasSecureChannel);
  }

  /**
   * @deprecated Use {@link #isAssociatedWithDriver()} instead.
   *
   * Returns {@code true} if this device is associated with the user currently in the foreground.
   */
  @Deprecated
  public boolean isAssociatedWithActiveUser() {
    return belongsToDriver;
  }

  /**
   * Returns {@code true} if this device is associated with the current driver's user profile,
   * {@code false} otherwise.
   */
  public boolean isAssociatedWithDriver() {
    return belongsToDriver;
  }

  /** Returns {@code true} if this device has a secure channel available. */
  public boolean hasSecureChannel() {
    return hasSecureChannel;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof ConnectedDevice)) {
      return false;
    }
    ConnectedDevice connectedDevice = (ConnectedDevice) obj;
    return Objects.equals(deviceId, connectedDevice.deviceId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(deviceId);
  }

  public static final Parcelable.Creator<ConnectedDevice> CREATOR =
      new Parcelable.Creator<ConnectedDevice>() {

        @Override
        public ConnectedDevice createFromParcel(Parcel source) {
          return new ConnectedDevice(source);
        }

        @Override
        public ConnectedDevice[] newArray(int size) {
          return new ConnectedDevice[size];
        }
      };
}
