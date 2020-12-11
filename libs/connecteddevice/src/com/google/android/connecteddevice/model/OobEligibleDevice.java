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

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import java.lang.annotation.Retention;
import java.util.Objects;

/** Device that may be used for an out-of-band channel. */
public class OobEligibleDevice implements Parcelable {

  /** Device type */
  @Retention(SOURCE)
  @IntDef(value = {OOB_TYPE_BLUETOOTH})
  public @interface OobType {}

  public static final int OOB_TYPE_BLUETOOTH = 0;

  private final String deviceAddress;

  @OobType private final int oobType;

  public OobEligibleDevice(@NonNull String deviceAddress, @OobType int oobType) {
    this.deviceAddress = deviceAddress;
    this.oobType = oobType;
  }

  private OobEligibleDevice(Parcel in) {
    this(in.readString(), in.readInt());
  }

  @NonNull
  public String getDeviceAddress() {
    return deviceAddress;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(deviceAddress);
    dest.writeInt(oobType);
  }

  @OobType
  public int getOobType() {
    return oobType;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof OobEligibleDevice)) {
      return false;
    }
    OobEligibleDevice device = (OobEligibleDevice) obj;
    return Objects.equals(device.deviceAddress, deviceAddress) && device.oobType == oobType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(deviceAddress, oobType);
  }

  public static final Parcelable.Creator<OobEligibleDevice> CREATOR =
      new Parcelable.Creator<OobEligibleDevice>() {
        @Override
        public OobEligibleDevice createFromParcel(Parcel source) {
          return new OobEligibleDevice(source);
        }

        @Override
        public OobEligibleDevice[] newArray(int size) {
          return new OobEligibleDevice[size];
        }
      };
}
