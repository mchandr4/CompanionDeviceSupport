package com.google.android.connecteddevice.trust.storage;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import com.google.android.connecteddevice.trust.api.TrustedDevice;
import java.util.Objects;

/** Table entity representing a trusted device. */
@Entity(tableName = "trusted_devices")
public final class TrustedDeviceEntity {
  /** Device id of trusted device. */
  @PrimaryKey
  @NonNull public final String id;

  /** Id of user associated with trusted device. */
  public final int userId;

  /** Handle assigned to this device. */
  public final long handle;

  /** {@code true} if the trusted device is valid. */
  public boolean isValid;

  public TrustedDeviceEntity(TrustedDevice trustedDevice) {
    this(trustedDevice, /* isValid= */ true);
  }

  public TrustedDeviceEntity(TrustedDevice trustedDevice, boolean isValid) {
    this(
        trustedDevice.getDeviceId(), trustedDevice.getUserId(), trustedDevice.getHandle(), isValid);
  }

  @Ignore
  public TrustedDeviceEntity(@NonNull String id, int userId, long handle) {
    this(id, userId, handle, /* isValid= */ true);
  }

  public TrustedDeviceEntity(@NonNull String id, int userId, long handle, boolean isValid) {
    this.id = id;
    this.userId = userId;
    this.handle = handle;
    this.isValid = isValid;
  }

  /** Return a new {@link TrustedDevice} of this entity. */
  public TrustedDevice toTrustedDevice() {
    return new TrustedDevice(id, userId, handle);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (!(obj instanceof TrustedDeviceEntity)) {
      return false;
    }

    TrustedDeviceEntity other = (TrustedDeviceEntity) obj;
    return id.equals(other.id)
        && userId == other.userId
        && handle == other.handle
        && isValid == other.isValid;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, userId, handle, isValid);
  }
}
