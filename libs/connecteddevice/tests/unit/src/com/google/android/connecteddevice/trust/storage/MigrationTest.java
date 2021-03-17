package com.google.android.connecteddevice.trust.storage;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.room.testing.MigrationTestHelper;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MigrationTest {
  private static final String TEST_DB_NAME = "migration_test";
  private static final String TRUSTED_DEVICES_TABLE = "trusted_devices";

  private static final String TRUSTED_DEVICES_ID_COLUMN = "id";
  private static final String TRUSTED_DEVICES_USER_ID_COLUMN = "userId";
  private static final String TRUSTED_DEVICES_HANDLE_COLUMN = "handle";

  private static final String DEFAULT_DEVICE_ID = "id";
  private static final int DEFAULT_USER_ID = 11;
  private static final long DEFAULT_HANDLE = 11L;
  private static final boolean DEFAULT_IS_VALID = true;

  @Rule
  public MigrationTestHelper helper =
      new MigrationTestHelper(
          InstrumentationRegistry.getInstrumentation(),
          TrustedDeviceDatabase.class.getCanonicalName(),
          new FrameworkSQLiteOpenHelperFactory());

  @Test
  public void testMigrate1To3() throws IOException {
    // Create the database in version 1
    SupportSQLiteDatabase db = helper.createDatabase(TEST_DB_NAME, /* version= */ 1);
    // Insert test data
    insertTrustedDevice(db);
    // Prepare for the next version
    db.close();

    // Re-open the database with version 3 and provide MIGRATION_1_2 and
    // MIGRATION_2_3 as the migration process.
    helper.runMigrationsAndValidate(
        TEST_DB_NAME, /* version= */ 3, /* validateDroppedTables= */ true, ALL_MIGRATIONS);

    // MigrationTestHelper automatically verifies the schema changes
    TrustedDeviceEntity dbEntity =
        getMigratedRoomDatabase().trustedDeviceDao().getTrustedDevice(DEFAULT_DEVICE_ID);

    TrustedDeviceEntity expectedDevice =
        new TrustedDeviceEntity(
            DEFAULT_DEVICE_ID, DEFAULT_USER_ID, DEFAULT_HANDLE, DEFAULT_IS_VALID);
    assertThat(dbEntity).isEqualTo(expectedDevice);
  }

  @Test
  public void testMigrate2To3() throws IOException {
    // Create the database in version 2
    SupportSQLiteDatabase db = helper.createDatabase(TEST_DB_NAME, /* version= */ 2);
    // Insert test data
    insertTrustedDevice(db);
    // Prepare for the next version
    db.close();

    // Re-open the database with version 3 and provide MIGRATION_1_2 and
    // MIGRATION_2_3 as the migration process.
    helper.runMigrationsAndValidate(
        TEST_DB_NAME, /* version= */ 3, /* validateDroppedTables= */ true, ALL_MIGRATIONS);

    // MigrationTestHelper automatically verifies the schema changes
    TrustedDeviceEntity dbEntity =
        getMigratedRoomDatabase().trustedDeviceDao().getTrustedDevice(DEFAULT_DEVICE_ID);

    TrustedDeviceEntity expectedDevice =
        new TrustedDeviceEntity(
            DEFAULT_DEVICE_ID, DEFAULT_USER_ID, DEFAULT_HANDLE, DEFAULT_IS_VALID);
    assertThat(dbEntity).isEqualTo(expectedDevice);
  }

  private TrustedDeviceDatabase getMigratedRoomDatabase() {
    TrustedDeviceDatabase database =
        Room.databaseBuilder(
                ApplicationProvider.getApplicationContext(),
                TrustedDeviceDatabase.class,
                TEST_DB_NAME)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor())
            .build();
    // close the database and release any stream resources when the test finishes
    helper.closeWhenFinished(database);
    return database;
  }

  private void insertTrustedDevice(SupportSQLiteDatabase db) {
    ContentValues values = new ContentValues();
    values.put(TRUSTED_DEVICES_ID_COLUMN, DEFAULT_DEVICE_ID);
    values.put(TRUSTED_DEVICES_USER_ID_COLUMN, DEFAULT_USER_ID);
    values.put(TRUSTED_DEVICES_HANDLE_COLUMN, DEFAULT_HANDLE);
    db.insert(TRUSTED_DEVICES_TABLE, SQLiteDatabase.CONFLICT_REPLACE, values);
  }

  private static final Migration[] ALL_MIGRATIONS =
      new Migration[] {
        TrustedDeviceDatabaseProvider.MIGRATION_1_2, TrustedDeviceDatabaseProvider.MIGRATION_2_3
      };
}
