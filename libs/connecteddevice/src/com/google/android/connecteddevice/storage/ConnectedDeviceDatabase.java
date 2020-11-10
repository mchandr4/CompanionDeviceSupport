package com.google.android.connecteddevice.storage;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/** Database for connected devices. */
@Database(
    entities = {
      AssociatedDeviceEntity.class,
      AssociatedDeviceKeyEntity.class,
      AssociatedDeviceChallengeSecretEntity.class
    },
    version = 2,
    exportSchema = false)
public abstract class ConnectedDeviceDatabase extends RoomDatabase {

  /** Return the DAO for the associated device table. */
  public abstract AssociatedDeviceDao associatedDeviceDao();
}
