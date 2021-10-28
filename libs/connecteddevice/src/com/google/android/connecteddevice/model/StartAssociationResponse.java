/*
 * Copyright (C) 2021 The Android Open Source Project
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
import java.util.Arrays;
import java.util.Objects;

/** Contains details of the response to association request. */
public class StartAssociationResponse implements Parcelable {

  private final OobData oobData;

  private final byte[] deviceIdentifier;

  private final String deviceName;

  /**
   * Create a new StartAssociationResponse.
   *
   * @param oobData OOB data which is generated per association request.
   * @param deviceIdentifier Unique representative of current device and will be refreshed with each
   *     association request.
   * @param deviceName The readable name of current device.
   */
  public StartAssociationResponse(
      @NonNull OobData oobData, @NonNull byte[] deviceIdentifier, @NonNull String deviceName) {
    this.oobData = oobData;
    this.deviceIdentifier = deviceIdentifier;
    this.deviceName = deviceName;
  }

  private StartAssociationResponse(Parcel in) {
    this(in.readParcelable(OobData.class.getClassLoader()), in.createByteArray(), in.readString());
  }

  /** Returns the OOB data of the response. */
  @NonNull
  public OobData getOobData() {
    return oobData;
  }

  /** Returns the device identifier of the response. */
  @NonNull
  public byte[] getDeviceIdentifier() {
    return deviceIdentifier;
  }

  /** Returns the name of the response. */
  @NonNull
  public String getDeviceName() {
    return deviceName;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof StartAssociationResponse)) {
      return false;
    }
    StartAssociationResponse response = (StartAssociationResponse) obj;
    return Objects.equals(oobData, response.getOobData())
        && Arrays.equals(deviceIdentifier, response.getDeviceIdentifier())
        && deviceName.equals(response.getDeviceName());
  }

  @Override
  public int hashCode() {
    int result = oobData.hashCode();
    result = 31 * result + Arrays.hashCode(deviceIdentifier);
    result = 31 * result + deviceName.hashCode();
    return result;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(oobData, flags);
    dest.writeByteArray(deviceIdentifier);
    dest.writeString(deviceName);
  }

  public static final Parcelable.Creator<StartAssociationResponse> CREATOR =
      new Parcelable.Creator<StartAssociationResponse>() {
        @Override
        public StartAssociationResponse createFromParcel(Parcel source) {
          return new StartAssociationResponse(source);
        }

        @Override
        public StartAssociationResponse[] newArray(int size) {
          return new StartAssociationResponse[size];
        }
      };
}
