package com.google.android.connecteddevice.trust.storage;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/** Database for trusted device feature. */
@Database(
    entities = {TrustedDeviceEntity.class},
    version = 1,
    exportSchema = false)
public abstract class TrustedDeviceDatabase extends RoomDatabase {

  /** Name of trusted device database. */
  public static final String DATABASE_NAME = "trusted-device-database";

  /** Return the DAO for the trusted device table. */
  public abstract TrustedDeviceDao trustedDeviceDao();
}
