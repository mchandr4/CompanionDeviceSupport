package com.google.android.connecteddevice.trust.storage;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

/** Queries for trusted device table. */
@Dao
public interface TrustedDeviceDao {

  /** Get a {@link TrustedDeviceEntity} based on device id. */
  @Query("SELECT * FROM trusted_devices WHERE id LIKE :deviceId LIMIT 1")
  TrustedDeviceEntity getTrustedDevice(String deviceId);

  /** Get all {@link TrustedDeviceEntity}s associated with a user. */
  @Query("SELECT * FROM trusted_devices WHERE userId LIKE :userId")
  List<TrustedDeviceEntity> getTrustedDevicesForUser(int userId);

  /**
   * Add a {@link TrustedDeviceEntity}. Replace if a device already exists with the same device id.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void addOrReplaceTrustedDevice(TrustedDeviceEntity trustedDevice);

  /** Remove a {@link TrustedDeviceEntity}. */
  @Delete
  void removeTrustedDevice(TrustedDeviceEntity trustedDevice);
}
