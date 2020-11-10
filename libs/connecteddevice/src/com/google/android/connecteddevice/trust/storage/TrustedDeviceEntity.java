package com.google.android.connecteddevice.trust.storage;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import com.google.android.connecteddevice.trust.api.TrustedDevice;

/** Table entity representing a trusted device. */
@Entity(tableName = "trusted_devices")
public class TrustedDeviceEntity {
  /** Device id of trusted device. */
  @PrimaryKey @NonNull public String id;

  /** Id of user associated with trusted device. */
  public int userId;

  /** Handle assigned to this device. */
  public long handle;

  public TrustedDeviceEntity() {}

  public TrustedDeviceEntity(TrustedDevice trustedDevice) {
    id = trustedDevice.getDeviceId();
    userId = trustedDevice.getUserId();
    handle = trustedDevice.getHandle();
  }

  /** Return a new {@link TrustedDevice} of this entity. */
  public TrustedDevice toTrustedDevice() {
    return new TrustedDevice(id, userId, handle);
  }
}
