package com.google.android.companiondevicesupport;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.model.AssociatedDevice;

/** Class that contains the details of an associated device. */
class AssociatedDeviceDetails {
  private final String deviceId;

  private final String deviceAddress;

  private final String deviceName;

  private final boolean isConnectionEnabled;

  private final boolean isConnected;

  AssociatedDeviceDetails(@NonNull AssociatedDevice device, boolean isConnected) {
    deviceId = device.getDeviceId();
    deviceAddress = device.getDeviceAddress();
    deviceName = device.getDeviceName();
    isConnectionEnabled = device.isConnectionEnabled();
    this.isConnected = isConnected;
  }

  /** Get the device id. */
  @NonNull
  String getDeviceId() {
    return deviceId;
  }

  /** Get the name of the associated device. */
  @Nullable
  String getDeviceName() {
    return deviceName;
  }

  /** Get the device address. */
  @NonNull
  String getDeviceAddress() {
    return deviceAddress;
  }

  /** {@code true} if the connection is enabled for the device. */
  boolean isConnectionEnabled() {
    return isConnectionEnabled;
  }

  /** {@code true} if the device is connected. */
  boolean isConnected() {
    return isConnected;
  }

  /** Get {@link AssociatedDevice}. */
  @NonNull
  AssociatedDevice getAssociatedDevice() {
    return new AssociatedDevice(deviceId, deviceAddress, deviceName, isConnectionEnabled);
  }
}
