package com.google.android.connecteddevice.storage;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/** Table entity representing a key for an associated device. */
@Entity(tableName = "associated_device_keys")
public class AssociatedDeviceKeyEntity {

  /** Id of the device. */
  @PrimaryKey
  @NonNull
  public String id;

  @NonNull
  public String encryptedKey;

  public AssociatedDeviceKeyEntity() {}

  public AssociatedDeviceKeyEntity(String deviceId, String encryptedKey) {
    id = deviceId;
    this.encryptedKey = encryptedKey;
  }
}
