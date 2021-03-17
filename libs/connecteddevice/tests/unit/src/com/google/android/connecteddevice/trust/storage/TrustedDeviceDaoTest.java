package com.google.android.connecteddevice.trust.storage;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.room.Room;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class TrustedDeviceDaoTest {
  private static final String DEFAULT_ID = "id";
  private static final int DEFAULT_USER_ID = 11;
  private static final long DEFAULT_HANDLE = 11L;
  private static final byte[] DEFAULT_FEATURE_STATE = "state".getBytes(UTF_8);

  private static final String OTHER_ID = "id2";
  private static final int OTHER_USER_ID = 12;
  private static final long OTHER_HANDLE = 12L;
  private static final byte[] OTHER_FEATURE_STATE = "other".getBytes(UTF_8);

  private TrustedDeviceDatabase database;
  private TrustedDeviceDao trustedDeviceDao;

  @Before
  public void setUp() {
    Context context = ApplicationProvider.getApplicationContext();

    database = Room.inMemoryDatabaseBuilder(context, TrustedDeviceDatabase.class)
      .allowMainThreadQueries()
      .setQueryExecutor(directExecutor())
      .build();

    trustedDeviceDao = database.trustedDeviceDao();
  }

  @After
  public void tearDown() {
    database.close();
  }

  @Test
  public void testStoreTrustedDevice_retrievableByDeviceId() {
    TrustedDeviceEntity entity =
        new TrustedDeviceEntity(DEFAULT_ID, DEFAULT_USER_ID, DEFAULT_HANDLE);

    trustedDeviceDao.addOrReplaceTrustedDevice(entity);

    TrustedDeviceEntity storedTrustedDevice = trustedDeviceDao.getTrustedDevice(entity.id);
    assertThat(storedTrustedDevice).isEqualTo(entity);
  }

  @Test
  public void testStoreTrustedDevice_retrievableByUserId() {
    TrustedDeviceEntity entity =
        new TrustedDeviceEntity(DEFAULT_ID, DEFAULT_USER_ID, DEFAULT_HANDLE);
    TrustedDeviceEntity other =
        new TrustedDeviceEntity(OTHER_ID, OTHER_USER_ID, OTHER_HANDLE);

    trustedDeviceDao.addOrReplaceTrustedDevice(entity);
    trustedDeviceDao.addOrReplaceTrustedDevice(other);

    List<TrustedDeviceEntity> storedDevices =
        trustedDeviceDao.getValidTrustedDevicesForUser(entity.userId);
    assertThat(storedDevices).containsExactly(entity);
  }

  @Test
  public void testAddOrReplaceTrustedDevice() {
    TrustedDeviceEntity entity =
        new TrustedDeviceEntity(DEFAULT_ID, DEFAULT_USER_ID, DEFAULT_HANDLE);

    trustedDeviceDao.addOrReplaceTrustedDevice(entity);

    TrustedDeviceEntity updated =
        new TrustedDeviceEntity(DEFAULT_ID, OTHER_USER_ID, OTHER_HANDLE);
    trustedDeviceDao.addOrReplaceTrustedDevice(updated);

    TrustedDeviceEntity storedTrustedDevice = trustedDeviceDao.getTrustedDevice(DEFAULT_ID);
    assertThat(storedTrustedDevice).isEqualTo(updated);
  }

  @Test
  public void testRemoveTrustedDevice_removesCorrectlyForDeviceId() {
    TrustedDeviceEntity entity =
        new TrustedDeviceEntity(DEFAULT_ID, DEFAULT_USER_ID, DEFAULT_HANDLE);
    TrustedDeviceEntity other =
        new TrustedDeviceEntity(OTHER_ID, OTHER_USER_ID, OTHER_HANDLE);

    trustedDeviceDao.addOrReplaceTrustedDevice(entity);
    trustedDeviceDao.addOrReplaceTrustedDevice(other);

    trustedDeviceDao.removeTrustedDevice(entity);

    assertThat(trustedDeviceDao.getTrustedDevice(entity.id)).isNull();

    TrustedDeviceEntity storedTrustedDevice = trustedDeviceDao.getTrustedDevice(other.id);
    assertThat(storedTrustedDevice).isEqualTo(other);
  }

  @Test
  public void testRemoveTrustedDevice_removesCorrectlyForUser() {
    TrustedDeviceEntity entity =
        new TrustedDeviceEntity(DEFAULT_ID, DEFAULT_USER_ID, DEFAULT_HANDLE);
    TrustedDeviceEntity other =
        new TrustedDeviceEntity(OTHER_ID, OTHER_USER_ID, OTHER_HANDLE);

    trustedDeviceDao.addOrReplaceTrustedDevice(entity);
    trustedDeviceDao.addOrReplaceTrustedDevice(other);

    trustedDeviceDao.removeTrustedDevice(entity);

    assertThat(trustedDeviceDao.getValidTrustedDevicesForUser(entity.userId)).isEmpty();
    assertThat(trustedDeviceDao.getValidTrustedDevicesForUser(other.userId)).containsExactly(other);
  }

  @Test
  public void testStoreFeatureState() {
    FeatureStateEntity entity = new FeatureStateEntity(DEFAULT_ID, DEFAULT_FEATURE_STATE);

    trustedDeviceDao.addOrReplaceFeatureState(entity);

    FeatureStateEntity storedFeatureState = trustedDeviceDao.getFeatureState(entity.id);
    assertThat(storedFeatureState).isEqualTo(entity);
  }

  @Test
  public void testAddOrReplaceFeatureState() {
    FeatureStateEntity entity = new FeatureStateEntity(DEFAULT_ID, DEFAULT_FEATURE_STATE);
    trustedDeviceDao.addOrReplaceFeatureState(entity);

    FeatureStateEntity updated = new FeatureStateEntity(DEFAULT_ID, OTHER_FEATURE_STATE);
    trustedDeviceDao.addOrReplaceFeatureState(updated);

    FeatureStateEntity storedState = trustedDeviceDao.getFeatureState(DEFAULT_ID);
    assertThat(storedState).isEqualTo(updated);
  }

  @Test
  public void testRemoveFeatureState() {
    FeatureStateEntity entity = new FeatureStateEntity(DEFAULT_ID, DEFAULT_FEATURE_STATE);
    FeatureStateEntity other = new FeatureStateEntity(OTHER_ID, OTHER_FEATURE_STATE);

    trustedDeviceDao.addOrReplaceFeatureState(entity);
    trustedDeviceDao.addOrReplaceFeatureState(other);

    trustedDeviceDao.removeFeatureState(entity.id);

    assertThat(trustedDeviceDao.getFeatureState(entity.id)).isNull();

    FeatureStateEntity storedTrustedDevice = trustedDeviceDao.getFeatureState(other.id);
    assertThat(storedTrustedDevice).isEqualTo(other);
  }

  @Test
  public void testGetValidTrustedDevicesForUser_onlyValidDeviceRetrievableByUserId() {
    TrustedDeviceEntity entity =
        new TrustedDeviceEntity(DEFAULT_ID, DEFAULT_USER_ID, DEFAULT_HANDLE, /* isValid= */ true);
    TrustedDeviceEntity other =
        new TrustedDeviceEntity(OTHER_ID, DEFAULT_USER_ID, OTHER_HANDLE, /* isValid= */ false);

    trustedDeviceDao.addOrReplaceTrustedDevice(entity);
    trustedDeviceDao.addOrReplaceTrustedDevice(other);

    List<TrustedDeviceEntity> storedDevices =
        trustedDeviceDao.getValidTrustedDevicesForUser(entity.userId);
    assertThat(storedDevices).containsExactly(entity);
  }

  @Test
  public void testGetInvalidTrustedDevicesForUser_onlyInvalidDeviceRetrievableByUserId() {
    TrustedDeviceEntity entity =
        new TrustedDeviceEntity(DEFAULT_ID, DEFAULT_USER_ID, DEFAULT_HANDLE, /* isValid= */ true);
    TrustedDeviceEntity other =
        new TrustedDeviceEntity(OTHER_ID, DEFAULT_USER_ID, OTHER_HANDLE, /* isValid= */ false);

    trustedDeviceDao.addOrReplaceTrustedDevice(entity);
    trustedDeviceDao.addOrReplaceTrustedDevice(other);

    List<TrustedDeviceEntity> storedDevices =
        trustedDeviceDao.getInvalidTrustedDevicesForUser(entity.userId);
    assertThat(storedDevices).containsExactly(other);
  }
}
