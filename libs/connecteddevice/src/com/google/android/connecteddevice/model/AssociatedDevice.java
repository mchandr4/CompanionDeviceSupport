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
import com.google.android.companionprotos.DeviceOS;
import java.util.Objects;

/** Contains basic info of an associated device. */
public class AssociatedDevice implements Parcelable {
  /** Default placeholder userId value for an unclaimed device. */
  public static final int UNCLAIMED_USER_ID = -1;

  private final String id;

  private final String address;

  private final String name;

  private final boolean isConnectionEnabled;

  private final int userId;

  private final DeviceOS os;

  private final String osVersion;

  private final String companionSdkVersion;

  /**
   * Create a new AssociatedDevice.
   *
   * @param id Id of the associated device.
   * @param address Address of the associated device.
   * @param name Name of the associated device. {@code null} if not known.
   * @param isConnectionEnabled If connection is enabled for this device.
   */
  public AssociatedDevice(
      @NonNull String id,
      @NonNull String address,
      @Nullable String name,
      boolean isConnectionEnabled) {
    this(id, address, name, isConnectionEnabled, UNCLAIMED_USER_ID);
  }

  /**
   * Create a new AssociatedDevice.
   *
   * @param id Id of the associated device.
   * @param address Address of the associated device.
   * @param name Name of the associated device. {@code null} if not known.
   * @param isConnectionEnabled If connection is enabled for this device.
   * @param userId Id of the claiming user, or {@value #UNCLAIMED_USER_ID} if unclaimed.
   */
  public AssociatedDevice(
      @NonNull String id,
      @NonNull String address,
      @Nullable String name,
      boolean isConnectionEnabled,
      int userId) {
    this(
        id,
        address,
        name,
        isConnectionEnabled,
        userId,
        DeviceOS.DEVICE_OS_UNKNOWN,
        /* osVersion= */ null,
        /* companionSdkVersion= */ null);
  }

  /**
   * Create a new AssociatedDevice using a numeric representation of the operating system.
   *
   * @param id Id of the associated device.
   * @param address Address of the associated device.
   * @param name Name of the associated device. {@code null} if not known.
   * @param isConnectionEnabled If connection is enabled for this device.
   * @param userId Id of the claiming user, or {@value #UNCLAIMED_USER_ID} if unclaimed.
   * @param os The numeric representation of the operating system of the associated device.
   * @param osVersion The version of the operating system of the associated device.
   * @param companionSdkVersion The Companion SDK version running on the associated device.
   */
  public AssociatedDevice(
      @NonNull String id,
      @NonNull String address,
      @Nullable String name,
      boolean isConnectionEnabled,
      int userId,
      int os,
      @Nullable String osVersion,
      @Nullable String companionSdkVersion) {
    this(
        id,
        address,
        name,
        isConnectionEnabled,
        userId,
        DeviceOS.forNumber(os),
        osVersion,
        companionSdkVersion);
  }

  /**
   * Create a new AssociatedDevice.
   *
   * @param id Id of the associated device.
   * @param address Address of the associated device.
   * @param name Name of the associated device. {@code null} if not known.
   * @param isConnectionEnabled If connection is enabled for this device.
   * @param userId Id of the claiming user, or {@value #UNCLAIMED_USER_ID} if unclaimed.
   * @param os The operating system of the device.
   * @param osVersion The version of the operating system of the device.
   * @param companionSdkVersion The Companion SDK version running on the device.
   */
  public AssociatedDevice(
      @NonNull String id,
      @NonNull String address,
      @Nullable String name,
      boolean isConnectionEnabled,
      int userId,
      @NonNull DeviceOS os,
      @Nullable String osVersion,
      @Nullable String companionSdkVersion) {
    this.id = id;
    this.address = address;
    this.name = name;
    this.isConnectionEnabled = isConnectionEnabled;
    this.userId = userId;
    this.os = os;
    this.osVersion = osVersion;
    this.companionSdkVersion = companionSdkVersion;
  }

  private AssociatedDevice(Parcel in) {
    this(
        in.readString(),
        in.readString(),
        in.readString(),
        in.readBoolean(),
        in.readInt(),
        in.readInt(),
        in.readString(),
        in.readString());
  }

  /** Returns the id for this device. */
  @NonNull
  public String getId() {
    return id;
  }

  /** Returns the address for this device. */
  @NonNull
  public String getAddress() {
    return address;
  }

  /** Returns the name for this device or {@code null} if not known. */
  @Nullable
  public String getName() {
    return name;
  }

  /** Return if connection is enabled for this device. */
  public boolean isConnectionEnabled() {
    return isConnectionEnabled;
  }

  /** Returns the id of the claiming user, or {@value #UNCLAIMED_USER_ID} if not claimed. */
  public int getUserId() {
    return userId;
  }

  /** Returns the operating system of the device. */
  @NonNull
  public DeviceOS getOs() {
    return os;
  }

  /** Returns the version of the operating system of the device. */
  @Nullable
  public String getOsVersion() {
    return osVersion;
  }

  /** Returns the Companion SDK version running on the device. */
  @Nullable
  public String getCompanionSdkVersion() {
    return companionSdkVersion;
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
    return Objects.equals(id, associatedDevice.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(id);
    dest.writeString(address);
    dest.writeString(name);
    dest.writeBoolean(isConnectionEnabled);
    dest.writeInt(userId);
    dest.writeInt(os.getNumber());
    dest.writeString(osVersion);
    dest.writeString(companionSdkVersion);
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
