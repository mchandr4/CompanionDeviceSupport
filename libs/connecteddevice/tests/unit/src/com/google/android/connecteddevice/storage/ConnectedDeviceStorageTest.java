package com.google.android.connecteddevice.storage;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static org.junit.Assert.assertThrows;

import androidx.room.Room;
import android.content.Context;
import android.util.Base64;
import android.util.Pair;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.util.ByteUtils;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ConnectedDeviceStorageTest {
  private static final int ACTIVE_USER_ID = 10;

  private final Context context = ApplicationProvider.getApplicationContext();

  private ConnectedDeviceStorage connectedDeviceStorage;

  private List<Pair<Integer, AssociatedDevice>> addedAssociatedDevices;

  @Before
  public void setUp() {
    AssociatedDeviceDao database =
        Room.inMemoryDatabaseBuilder(context, ConnectedDeviceDatabase.class)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor())
            .build()
            .associatedDeviceDao();

    connectedDeviceStorage = new ConnectedDeviceStorage(context, new FakeCryptoHelper(), database);
    addedAssociatedDevices = new ArrayList<>();
  }

  @After
  public void tearDown() {
    // Clear any associated devices added during tests.
    for (Pair<Integer, AssociatedDevice> device : addedAssociatedDevices) {
      connectedDeviceStorage.removeAssociatedDevice(device.first, device.second.getDeviceId());
    }
  }

  @Test
  public void getAssociatedDeviceIdsForUser_includesNewlyAddedDevice() {
    AssociatedDevice addedDevice = addRandomAssociatedDevice(ACTIVE_USER_ID);
    List<String> associatedDevices =
        connectedDeviceStorage.getAssociatedDeviceIdsForUser(ACTIVE_USER_ID);
    assertThat(associatedDevices).containsExactly(addedDevice.getDeviceId());
  }

  @Test
  public void getAssociatedDeviceIdsForUser_excludesDeviceAddedForOtherUser() {
    addRandomAssociatedDevice(ACTIVE_USER_ID);
    List<String> associatedDevices =
        connectedDeviceStorage.getAssociatedDeviceIdsForUser(ACTIVE_USER_ID + 1);
    assertThat(associatedDevices).isEmpty();
  }

  @Test
  public void getAssociatedDeviceIdsForUser_excludesRemovedDevice() {
    AssociatedDevice addedDevice = addRandomAssociatedDevice(ACTIVE_USER_ID);
    connectedDeviceStorage.removeAssociatedDevice(ACTIVE_USER_ID, addedDevice.getDeviceId());
    List<String> associatedDevices =
        connectedDeviceStorage.getAssociatedDeviceIdsForUser(ACTIVE_USER_ID);
    assertThat(associatedDevices).isEmpty();
  }

  @Test
  public void getAssociatedDevicesForUser_includesNewlyAddedDevice() {
    AssociatedDevice addedDevice = addRandomAssociatedDevice(ACTIVE_USER_ID);
    List<AssociatedDevice> associatedDevices =
        connectedDeviceStorage.getAssociatedDevicesForUser(ACTIVE_USER_ID);
    assertThat(associatedDevices).containsExactly(addedDevice);
  }

  @Test
  public void getAssociatedDevicesForUser_excludesDeviceAddedForOtherUser() {
    addRandomAssociatedDevice(ACTIVE_USER_ID);
    List<String> associatedDevices =
        connectedDeviceStorage.getAssociatedDeviceIdsForUser(ACTIVE_USER_ID + 1);
    assertThat(associatedDevices).isEmpty();
  }

  @Test
  public void getAssociatedDevicesForUser_excludesRemovedDevice() {
    AssociatedDevice addedDevice = addRandomAssociatedDevice(ACTIVE_USER_ID);
    connectedDeviceStorage.removeAssociatedDevice(ACTIVE_USER_ID, addedDevice.getDeviceId());
    List<AssociatedDevice> associatedDevices =
        connectedDeviceStorage.getAssociatedDevicesForUser(ACTIVE_USER_ID);
    assertThat(associatedDevices).isEmpty();
  }

  @Test
  public void getEncryptionKey_returnsSavedKey() {
    String deviceId = addRandomAssociatedDevice(ACTIVE_USER_ID).getDeviceId();
    byte[] key = ByteUtils.randomBytes(16);
    connectedDeviceStorage.saveEncryptionKey(deviceId, key);
    assertThat(connectedDeviceStorage.getEncryptionKey(deviceId)).isEqualTo(key);
  }

  @Test
  public void getEncryptionKey_returnsNullForUnrecognizedDeviceId() {
    String deviceId = addRandomAssociatedDevice(ACTIVE_USER_ID).getDeviceId();
    connectedDeviceStorage.saveEncryptionKey(deviceId, ByteUtils.randomBytes(16));
    assertThat(connectedDeviceStorage.getEncryptionKey(UUID.randomUUID().toString())).isNull();
  }

  @Test
  public void saveChallengeSecret_throwsForInvalidLengthSecret() {
    byte[] invalidSecret = ByteUtils.randomBytes(ConnectedDeviceStorage.CHALLENGE_SECRET_BYTES - 1);
    assertThrows(
        InvalidParameterException.class,
        () ->
            connectedDeviceStorage.saveChallengeSecret(
                UUID.randomUUID().toString(), invalidSecret));
  }

  private AssociatedDevice addRandomAssociatedDevice(int userId) {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            "00:00:00:00:00:00",
            "Test Device",
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(userId, device, ByteUtils.randomBytes(16));
    return device;
  }

  private void addAssociatedDevice(int userId, AssociatedDevice device, byte[] encryptionKey) {
    connectedDeviceStorage.addAssociatedDeviceForUser(userId, device);
    connectedDeviceStorage.saveEncryptionKey(device.getDeviceId(), encryptionKey);
    addedAssociatedDevices.add(new Pair<>(userId, device));
  }

  /** A {@link CryptoHelper} that does base64 de/encoding to simulate encryption. */
  private static class FakeCryptoHelper implements CryptoHelper {
    @Override
    public String encrypt(byte[] value) {
      return Base64.encodeToString(value, Base64.DEFAULT);
    }

    @Override
    public byte[] decrypt(String value) {
      return Base64.decode(value, Base64.DEFAULT);
    }
  }
}
