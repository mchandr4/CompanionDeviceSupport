package com.google.android.connecteddevice.trust.storage;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/** Database for trusted device feature. */
@Database(
    entities = {
      TrustedDeviceEntity.class,
      FeatureStateEntity.class,
      TrustedDeviceTokenEntity.class
    },
    version = 4,
    exportSchema = true)
public abstract class TrustedDeviceDatabase extends RoomDatabase {
  /** Return the DAO for the trusted device table. */
  public abstract TrustedDeviceDao trustedDeviceDao();
}
