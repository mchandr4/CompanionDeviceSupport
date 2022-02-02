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

  /** Get a {@link TrustedDeviceEntity} based on device id only if it is valid. */
  @Query("SELECT * FROM trusted_devices WHERE id LIKE :deviceId AND isValid = 1 LIMIT 1")
  TrustedDeviceEntity getTrustedDeviceIfValid(String deviceId);

  /** Get a {@link FeatureStateEntity} based on device id. */
  @Query("SELECT * FROM feature_state WHERE id = :deviceId")
  FeatureStateEntity getFeatureState(String deviceId);

  /** Get all {@link TrustedDeviceEntity}s associated with a user. */
  @Query("SELECT * FROM trusted_devices WHERE userId LIKE :userId AND isValid = 1")
  List<TrustedDeviceEntity> getValidTrustedDevicesForUser(int userId);

  /** Get all invalid {@link TrustedDeviceEntity}s associated with a user. */
  @Query("SELECT * FROM trusted_devices WHERE userId LIKE :userId AND isValid = 0")
  List<TrustedDeviceEntity> getInvalidTrustedDevicesForUser(int userId);

  /**
   * Add a {@link TrustedDeviceEntity}. Replace if a device already exists with the same device id.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void addOrReplaceTrustedDevice(TrustedDeviceEntity trustedDevice);

  /**
   * Add a {@link FeatureStateEntity}. Replaces any stored feature states if the device id is the
   * same.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void addOrReplaceFeatureState(FeatureStateEntity featureState);

  /** Remove a {@link TrustedDeviceEntity}. */
  @Delete
  void removeTrustedDevice(TrustedDeviceEntity trustedDevice);

  /** Remove any stored feature statue for a car with the given {@code deviceId}. */
  @Query("DELETE FROM feature_state WHERE id = :deviceId")
  void removeFeatureState(String deviceId);

  /** Get a {@link TrustedDeviceTokenEntity} based on device id. */
  @Query("SELECT * FROM trusted_device_tokens WHERE id = :deviceId LIMIT 1")
  TrustedDeviceTokenEntity getTrustedDeviceHashedToken(String deviceId);

  /**
   * Add a {@link TrustedDeviceTokenEntity}. Replaces any previously stored hashed token with a
   * matching device id.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void addOrReplaceTrustedDeviceHashedToken(TrustedDeviceTokenEntity hashedToken);

  /** Remove a {@link TrustedDeviceTokenEntity} belonging with the given {@code deviceId}. */
  @Query("DELETE FROM trusted_device_tokens WHERE id = :deviceId")
  void removeTrustedDeviceHashedToken(String deviceId);
}
