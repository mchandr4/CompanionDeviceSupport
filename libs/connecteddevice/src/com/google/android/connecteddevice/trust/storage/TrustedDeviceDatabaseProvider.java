package com.google.android.connecteddevice.trust.storage;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/** Provider of the common database within this library. */
public class TrustedDeviceDatabaseProvider {
  @VisibleForTesting static final String DATABASE_NAME = "trusted-device-database";

  /**
   * Performs the database migration from version 1 to 2.
   *
   * <p>This migration requires the creation of a new table to store feature state messages that
   * need to be synced to the phone.
   */
  @VisibleForTesting
  static final Migration MIGRATION_1_2 = new Migration(1, 2) {
      @Override
      public void migrate(SupportSQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `feature_state` "
            + "(`id` TEXT NOT NULL, `state` BLOB NOT NULL, PRIMARY KEY(`id`))");
      }
    };

  /**
   * Performs the database migration from version 1 to 2.
   *
   * <p>This migration requires adding a new column in to store if a trusted device is valid.
   */
  @VisibleForTesting
  static final Migration MIGRATION_2_3 =
      new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
          database.execSQL(
              "ALTER TABLE trusted_devices ADD COLUMN isValid INTEGER NOT NULL DEFAULT 1");
        }
      };

  /**
   * Performs the database migration from 3 to 4.
   *
   * <p>This migration creates a new table for hashed tokens and invalidates any existing trusted
   * device entities for removal on next startup.
   */
  @VisibleForTesting
  static final Migration MIGRATION_3_4 =
      new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
          database.execSQL(
              "CREATE TABLE IF NOT EXISTS trusted_device_tokens "
                  + "(id TEXT NOT NULL, hashed_token TEXT NOT NULL, PRIMARY KEY(id))");
          database.execSQL("UPDATE trusted_devices SET isValid = 0");
        }
      };

  private static TrustedDeviceDatabaseProvider instance;

  @VisibleForTesting final TrustedDeviceDatabase database;

  @VisibleForTesting
  TrustedDeviceDatabaseProvider(@NonNull Context context, boolean allowMainThreadQueries) {
    RoomDatabase.Builder<TrustedDeviceDatabase> builder =
        Room.databaseBuilder(context, TrustedDeviceDatabase.class, DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .enableMultiInstanceInvalidation();
    if (allowMainThreadQueries) {
      builder.allowMainThreadQueries();
    }
    database = builder.build();
  }

  /** Returns an instance of the {@link TrustedDeviceDatabase} to run queries off of. */
  @NonNull
  public static synchronized TrustedDeviceDatabase get(@NonNull Context context) {
    if (instance == null) {
      instance =
          new TrustedDeviceDatabaseProvider(
              context.getApplicationContext().createDeviceProtectedStorageContext(),
              /* allowMainThreadQueries= */ false);
    }
    return instance.database;
  }
}
