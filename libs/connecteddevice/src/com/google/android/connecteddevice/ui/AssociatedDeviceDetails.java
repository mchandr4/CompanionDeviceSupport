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

package com.google.android.connecteddevice.ui;

import android.app.ActivityManager;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.companionprotos.DeviceOS;
import com.google.android.connecteddevice.model.AssociatedDevice;
import java.util.Objects;

/**
 * Class that contains the details of an associated device.
 *
 * <p>Two {@link AssociatedDeviceDetails} are considered equal if their backing
 * {@link AssociatedDevice} are equal. The connection state is not taken into account.
 */
public class AssociatedDeviceDetails implements Parcelable {

  /** States of a device connection. */
  public enum ConnectionState {
    NOT_DETECTED,
    DETECTED,
    CONNECTED
  }

  private final AssociatedDevice device;

  private final ConnectionState state;

  public AssociatedDeviceDetails(@NonNull AssociatedDevice device, ConnectionState state) {
    this.device = device;
    this.state = state;
  }

  private AssociatedDeviceDetails(Parcel in) {
    this(
        in.readParcelable(AssociatedDevice.class.getClassLoader()),
        ConnectionState.values()[in.readInt()]);
  }

  /** Returns the id for this device. */
  @NonNull
  public String getId() {
    return device.getId();
  }

  /** Returns the address for this device. */
  @NonNull
  public String getAddress() {
    return device.getAddress();
  }

  /** Returns the name for this device or {@code null} if not known. */
  @Nullable
  public String getName() {
    return device.getName();
  }

  /** Return if connection is enabled for this device. */
  public boolean isConnectionEnabled() {
    return device.isConnectionEnabled();
  }

  /**
   * Returns the id of the claiming user, or {@value AssociatedDevice#UNCLAIMED_USER_ID} if
   * unclaimed.
   */
  public int getUserId() {
    return device.getUserId();
  }

  /**
   * Returns the operating system of the device or {@value DeviceOS#DEVICE_OS_UNKNOWN} if not known.
   */
  @NonNull
  public DeviceOS getOs() {
    return device.getOs();
  }

  /** Returns the version of the operating system of the device or {@code null} if not known. */
  @Nullable
  public String getOsVersion() {
    return device.getOsVersion();
  }

  /** Returns the Companion SDK version running on the device or {@code null} if not known. */
  @Nullable
  public String getCompanionSdkVersion() {
    return device.getCompanionSdkVersion();
  }

  /** Returns the current state of the device's connection. */
  @NonNull
  public ConnectionState getConnectionState() {
    return state;
  }

  /** Returns whether this device belongs to the current driver. */
  public boolean belongsToDriver() {
    return device.getUserId() == ActivityManager.getCurrentUser();
  }

  /** Get {@link AssociatedDevice}. */
  @NonNull
  public AssociatedDevice getAssociatedDevice() {
    return device;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof AssociatedDeviceDetails)) {
      return false;
    }

    // The connection state does not factor into equality.
    AssociatedDeviceDetails details = (AssociatedDeviceDetails) obj;
    return Objects.equals(device, details.device);
  }

  @Override
  public int hashCode() {
    return Objects.hash(device);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(device, /* flags= */ 0);
    dest.writeInt(state.ordinal());
  }

  // Explicitly specifying the type within <> to support building with Java 8.
  public static final Parcelable.Creator<AssociatedDeviceDetails> CREATOR =
      new Parcelable.Creator<AssociatedDeviceDetails>() {
        @Override
        public AssociatedDeviceDetails createFromParcel(Parcel source) {
          return new AssociatedDeviceDetails(source);
        }

        @Override
        public AssociatedDeviceDetails[] newArray(int size) {
          return new AssociatedDeviceDetails[size];
        }
      };
}
