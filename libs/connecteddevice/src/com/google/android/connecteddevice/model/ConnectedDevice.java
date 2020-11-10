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

  private final boolean belongsToActiveUser;

  private final boolean hasSecureChannel;

  /**
   * Create a new connected device.
   *
   * @param deviceId Id of the connected device.
   * @param deviceName Name of the connected device. {@code null} if not known.
   * @param belongsToActiveUser User associated with this device is currently in the foreground.
   * @param hasSecureChannel {@code true} if a secure channel is available for this device.
   */
  public ConnectedDevice(
      @NonNull String deviceId,
      @Nullable String deviceName,
      boolean belongsToActiveUser,
      boolean hasSecureChannel) {
    this.deviceId = deviceId;
    this.deviceName = deviceName;
    this.belongsToActiveUser = belongsToActiveUser;
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
    dest.writeBoolean(belongsToActiveUser);
    dest.writeBoolean(hasSecureChannel);
  }

  /**
   * Returns {@code true} if this device is associated with the user currently in the foreground.
   */
  public boolean isAssociatedWithActiveUser() {
    return belongsToActiveUser;
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
    return Objects.equals(deviceId, connectedDevice.deviceId)
        && Objects.equals(deviceName, connectedDevice.deviceName)
        && belongsToActiveUser == connectedDevice.belongsToActiveUser
        && hasSecureChannel == connectedDevice.hasSecureChannel;
  }

  @Override
  public int hashCode() {
    return Objects.hash(deviceId, deviceName, belongsToActiveUser, hasSecureChannel);
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
