package com.google.android.connecteddevice.trust.storage;

import static com.google.android.connecteddevice.trust.storage.TrustedDeviceDatabaseProvider.DATABASE_NAME;
import static com.google.common.truth.Truth.assertThat;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.migration.Migration;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.IOException;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MigrationTest {
  private static final int CURRENT_VERSION = 4;

  private static final String TRUSTED_DEVICES_TABLE = "trusted_devices";

  private static final String TRUSTED_DEVICES_ID_COLUMN = "id";
  private static final String TRUSTED_DEVICES_USER_ID_COLUMN = "userId";
  private static final String TRUSTED_DEVICES_HANDLE_COLUMN = "handle";
  private static final String TRUSTED_DEVICES_IS_VALID_COLUMN = "isValid";

  private static final String DEFAULT_DEVICE_ID = UUID.randomUUID().toString();
  private static final int DEFAULT_USER_ID = 11;
  private static final long DEFAULT_HANDLE = 11L;
  private static final boolean DEFAULT_IS_VALID = true;

  private static final int VERSION_IS_VALID_ADDED = 3;

  @Rule
  public MigrationTestHelper helper =
      new MigrationTestHelper(
          InstrumentationRegistry.getInstrumentation(),
          TrustedDeviceDatabase.class.getCanonicalName(),
          new FrameworkSQLiteOpenHelperFactory());

  @Test
  public void testMigrate1To4() throws IOException {
    int startingVersion = 1;
    // Create the database in version 1
    try (SupportSQLiteDatabase db = helper.createDatabase(DATABASE_NAME, startingVersion)) {
      insertTrustedDevice(db, startingVersion);
    }

    TrustedDeviceEntity dbEntity =
        getMigratedRoomDatabase().trustedDeviceDao().getTrustedDevice(DEFAULT_DEVICE_ID);
    TrustedDeviceEntity expectedDevice =
        new TrustedDeviceEntity(
            DEFAULT_DEVICE_ID, DEFAULT_USER_ID, DEFAULT_HANDLE, /* isValid= */ false);
    assertThat(dbEntity).isEqualTo(expectedDevice);
  }

  @Test
  public void testMigrate2To4() throws IOException {
    int startingVersion = 2;
    // Create the database in version 2
    try (SupportSQLiteDatabase db = helper.createDatabase(DATABASE_NAME, startingVersion)) {
      insertTrustedDevice(db, startingVersion);
    }

    TrustedDeviceEntity dbEntity =
        getMigratedRoomDatabase().trustedDeviceDao().getTrustedDevice(DEFAULT_DEVICE_ID);

    TrustedDeviceEntity expectedDevice =
        new TrustedDeviceEntity(
            DEFAULT_DEVICE_ID, DEFAULT_USER_ID, DEFAULT_HANDLE, /* isValid= */ false);
    assertThat(dbEntity).isEqualTo(expectedDevice);
  }

  @Test
  public void testMigrate3To4() throws IOException {
    int startingVersion = 3;
    // Create the database in version 3
    try (SupportSQLiteDatabase db = helper.createDatabase(DATABASE_NAME, startingVersion)) {
      insertTrustedDevice(db, startingVersion);
    }

    TrustedDeviceEntity dbEntity =
        getMigratedRoomDatabase().trustedDeviceDao().getTrustedDevice(DEFAULT_DEVICE_ID);

    TrustedDeviceEntity expectedDevice =
        new TrustedDeviceEntity(
            DEFAULT_DEVICE_ID, DEFAULT_USER_ID, DEFAULT_HANDLE, /* isValid= */ false);
    assertThat(dbEntity).isEqualTo(expectedDevice);
  }

  @Test
  public void validateSchemaChanges() throws IOException {
    int startingVersion = 1;
    try (SupportSQLiteDatabase db = helper.createDatabase(DATABASE_NAME, startingVersion)) {
      insertTrustedDevice(db, startingVersion);
    }

    for (int version = startingVersion + 1; version <= CURRENT_VERSION; version++) {
      helper.runMigrationsAndValidate(
          DATABASE_NAME, version, /* validateDroppedTables= */ true, ALL_MIGRATIONS);
    }
  }

  private TrustedDeviceDatabase getMigratedRoomDatabase() {
    TrustedDeviceDatabase database =
        new TrustedDeviceDatabaseProvider(
                ApplicationProvider.getApplicationContext(), /* allowMainThreadQueries= */ true)
            .database;
    // close the database and release any stream resources when the test finishes
    helper.closeWhenFinished(database);
    return database;
  }

  private void insertTrustedDevice(SupportSQLiteDatabase db, int version) {
    ContentValues values = new ContentValues();
    values.put(TRUSTED_DEVICES_ID_COLUMN, DEFAULT_DEVICE_ID);
    values.put(TRUSTED_DEVICES_USER_ID_COLUMN, DEFAULT_USER_ID);
    values.put(TRUSTED_DEVICES_HANDLE_COLUMN, DEFAULT_HANDLE);
    if (version >= VERSION_IS_VALID_ADDED) {
      values.put(TRUSTED_DEVICES_IS_VALID_COLUMN, DEFAULT_IS_VALID);
    }
    db.insert(TRUSTED_DEVICES_TABLE, SQLiteDatabase.CONFLICT_REPLACE, values);
  }

  private static final Migration[] ALL_MIGRATIONS =
      new Migration[] {
        TrustedDeviceDatabaseProvider.MIGRATION_1_2,
        TrustedDeviceDatabaseProvider.MIGRATION_2_3,
        TrustedDeviceDatabaseProvider.MIGRATION_3_4
      };
}
