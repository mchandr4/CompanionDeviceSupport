package com.google.android.connecteddevice.model;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Objects;

/** Contains basic info of an associated device. */
public class AssociatedDevice implements Parcelable {

  private final String deviceId;

  private final String deviceAddress;

  private final String deviceName;

  private final boolean isConnectionEnabled;

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
      boolean isConnectionEnabled) {
    this.deviceId = deviceId;
    this.deviceAddress = deviceAddress;
    this.deviceName = deviceName;
    this.isConnectionEnabled = isConnectionEnabled;
  }

  private AssociatedDevice(Parcel in) {
    this(in.readString(), in.readString(), in.readString(), in.readBoolean());
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

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof AssociatedDevice)) {
      return false;
    }
    AssociatedDevice associatedDevice = (AssociatedDevice) obj;
    return Objects.equals(deviceId, associatedDevice.deviceId)
        && Objects.equals(deviceAddress, associatedDevice.deviceAddress)
        && Objects.equals(deviceName, associatedDevice.deviceName)
        && isConnectionEnabled == associatedDevice.isConnectionEnabled;
  }

  @Override
  public int hashCode() {
    return Objects.hash(deviceId, deviceAddress, deviceName, isConnectionEnabled);
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
  }

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
