package com.google.android.connecteddevice.trust.storage;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/** Provider of the common database within this library. */
public class TrustedDeviceDatabaseProvider {
  private static final String DATABASE_NAME = "trusted-device-database";

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

  private static TrustedDeviceDatabaseProvider instance;

  private final TrustedDeviceDatabase database;

  private TrustedDeviceDatabaseProvider(@NonNull Context context) {
    database =
        Room.databaseBuilder(context, TrustedDeviceDatabase.class, DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .build();
  }

  /** Returns an instance of the {@link TrustedDeviceDatabase} to run queries off of. */
  @NonNull
  public static synchronized TrustedDeviceDatabase get(@NonNull Context context) {
    if (instance == null) {
      instance = new TrustedDeviceDatabaseProvider(
        context.getApplicationContext().createDeviceProtectedStorageContext());
    }
    return instance.database;
  }
}
