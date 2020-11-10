package com.google.android.connecteddevice.storage;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.model.AssociatedDevice;

/** Table entity representing an associated device. */
@Entity(tableName = "associated_devices")
public class AssociatedDeviceEntity {

  /** Id of the device. */
  @PrimaryKey
  @NonNull
  public String id;

  /** Id of user associated with this device. */
  public int userId;

  /** Bluetooth address of the device. */
  @Nullable
  public String address;

  /** Bluetooth device name. */
  @Nullable
  public String name;

  /** {@code true} if the connection is enabled for this device. */
  public boolean isConnectionEnabled;

  public AssociatedDeviceEntity() {}

  public AssociatedDeviceEntity(
      int userId, AssociatedDevice associatedDevice, boolean isConnectionEnabled) {
    this.userId = userId;
    id = associatedDevice.getDeviceId();
    address = associatedDevice.getDeviceAddress();
    name = associatedDevice.getDeviceName();
    this.isConnectionEnabled = isConnectionEnabled;
  }

  /** Return a new {@link AssociatedDevice} of this entity. */
  public AssociatedDevice toAssociatedDevice() {
    return new AssociatedDevice(id, address, name, isConnectionEnabled);
  }
}
