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

package com.google.android.connecteddevice.trust.api;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import java.util.Objects;

/** Representation of a trusted device. */
public final class TrustedDevice implements Parcelable {

  private static final String TAG = "TrustedDevice";

  private final String deviceId;

  private final int userId;

  private final long handle;

  public TrustedDevice(@NonNull String deviceId, int userId, long handle) {
    this.deviceId = deviceId;
    this.userId = userId;
    this.handle = handle;
  }

  public TrustedDevice(Parcel in) {
    this(in.readString(), in.readInt(), in.readLong());
  }

  /** Returns the id of the device. */
  @NonNull
  public String getDeviceId() {
    return deviceId;
  }

  /** Returns the user's id associated with this device. */
  public int getUserId() {
    return userId;
  }

  /** Returns the handle registered with this device. */
  public long getHandle() {
    return handle;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof TrustedDevice)) {
      return false;
    }
    TrustedDevice device = (TrustedDevice) obj;

    // Must maintain a 1:1 mapping of physical device to trusted device. A phyiscal device
    // can never represent multiple trusted devices at once.
    return Objects.equals(deviceId, device.deviceId);
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
    dest.writeInt(userId);
    dest.writeLong(handle);
  }

  public static final Parcelable.Creator<TrustedDevice> CREATOR =
      new Parcelable.Creator<TrustedDevice>() {
        @Override
        public TrustedDevice createFromParcel(Parcel source) {
          return new TrustedDevice(source);
        }

        @Override
        public TrustedDevice[] newArray(int size) {
          return new TrustedDevice[size];
        }
      };
}
