package com.google.android.connecteddevice.transport.spp;

import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
import java.util.Objects;

/** A {@code Parcelable} object that represents a {@code BluetoothSocket} */
public class Connection implements Parcelable {
  private static final String TAG = "Connection";

  private final ParcelUuid serviceUuid;
  private final BluetoothDevice remoteDevice;
  private final boolean isSecure;
  private final String remoteDeviceName;

  public Connection(
      ParcelUuid serviceUuid,
      BluetoothDevice remoteDevice,
      boolean isSecure,
      String remoteDeviceName) {
    this.serviceUuid = serviceUuid;
    this.remoteDevice = remoteDevice;
    this.isSecure = isSecure;
    // {@code BluetoothDevice} did not write device name to parcel.
    this.remoteDeviceName = remoteDeviceName;
  }

  public ParcelUuid getServiceUuid() {
    return serviceUuid;
  }

  public BluetoothDevice getRemoteDevice() {
    return remoteDevice;
  }

  public boolean isSecure() {
    return isSecure;
  }

  public String getRemoteDeviceName() {
    return remoteDeviceName;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(serviceUuid, /* parcelableFlags= */ 0);
    dest.writeParcelable(remoteDevice, /* parcelableFlags= */ 0);
    dest.writeBoolean(isSecure);
    // Need to save the Bluetooth name separately since remoteDevice does not save its name to the
    // Parcel.
    dest.writeString(remoteDeviceName);
  }

  public static final Parcelable.Creator<Connection> CREATOR =
      new Creator<Connection>() {
        @Override
        public Connection createFromParcel(Parcel source) {
          ParcelUuid serviceUuid = source.readParcelable(ParcelUuid.class.getClassLoader());
          BluetoothDevice remoteDevice =
              source.readParcelable(BluetoothDevice.class.getClassLoader());
          boolean isSecure = source.readBoolean();
          String remoteDeviceName = source.readString();
          return new Connection(serviceUuid, remoteDevice, isSecure, remoteDeviceName);
        }

        @Override
        public Connection[] newArray(int size) {
          return new Connection[0];
        }
      };

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Connection)) {
      return false;
    }
    Connection that = (Connection) o;
    return isSecure == that.isSecure
        && Objects.equals(serviceUuid, that.serviceUuid)
        && Objects.equals(remoteDevice, that.remoteDevice);
  }

  @Override
  public int hashCode() {
    return Objects.hash(serviceUuid, remoteDevice, isSecure);
  }
}
