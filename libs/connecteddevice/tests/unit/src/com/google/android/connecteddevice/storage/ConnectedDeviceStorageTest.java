/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.connecteddevice.storage;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.util.Base64;
import android.util.Pair;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage.AssociatedDeviceCallback;
import com.google.android.connecteddevice.util.ByteUtils;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

@RunWith(AndroidJUnit4.class)
public final class ConnectedDeviceStorageTest {
  private static final int ACTIVE_USER_ID = 10;

  private final Context context = ApplicationProvider.getApplicationContext();

  private ConnectedDeviceStorage connectedDeviceStorage;

  private List<Pair<Integer, AssociatedDevice>> addedAssociatedDevices;

  private ConnectedDeviceDatabase connectedDeviceDatabase;

  @Before
  public void setUp() {
    connectedDeviceDatabase =
        Room.inMemoryDatabaseBuilder(context, ConnectedDeviceDatabase.class)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor())
            .build();
    AssociatedDeviceDao database = connectedDeviceDatabase.associatedDeviceDao();

    connectedDeviceStorage = new ConnectedDeviceStorage(context, new FakeCryptoHelper(), database);
    addedAssociatedDevices = new ArrayList<>();
  }

  @After
  public void tearDown() {
    // Clear any associated devices added during tests.
    for (Pair<Integer, AssociatedDevice> device : addedAssociatedDevices) {
      connectedDeviceStorage.removeAssociatedDevice(device.first, device.second.getDeviceId());
    }
    connectedDeviceDatabase.close();
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
  public void getAllAssociatedDevices_returnsDevicesForAllUsers() {
    AssociatedDevice activeUserDevice = addRandomAssociatedDevice(ACTIVE_USER_ID);
    AssociatedDevice otherUserDevice = addRandomAssociatedDevice(ACTIVE_USER_ID + 1);

    List<AssociatedDevice> associatedDevices = connectedDeviceStorage.getAllAssociatedDevices();

    assertThat(associatedDevices).containsExactly(activeUserDevice, otherUserDevice);
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

  @Test
  public void setAssociatedDeviceName_setsNameIfNull() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            "00:00:00:00:00:00",
            /* deviceName= */ null,
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    String newName = "NewDeviceName";
    connectedDeviceStorage.setAssociatedDeviceName(device.getDeviceId(), newName);
    AssociatedDevice updatedDevice =
        connectedDeviceStorage.getAssociatedDevice(device.getDeviceId());

    assertThat(updatedDevice).isNotNull();
    assertThat(updatedDevice.getDeviceName()).isEqualTo(newName);
  }

  @Test
  public void setAssociatedDeviceName_doesNotModifyNameIfDeviceAlreadyHasAName() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            "00:00:00:00:00:00",
            "TestName",
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    connectedDeviceStorage.setAssociatedDeviceName(device.getDeviceId(), "NewDeviceName");
    AssociatedDevice updatedDevice =
        connectedDeviceStorage.getAssociatedDevice(device.getDeviceId());

    assertThat(updatedDevice).isNotNull();
    assertThat(updatedDevice.getDeviceName()).isEqualTo(device.getDeviceName());
  }

  @Test
  public void setAssociatedDeviceName_doesNotModifyNameIfNewNameIsEmpty() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            "00:00:00:00:00:00",
            /* deviceName= */ null,
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    connectedDeviceStorage.setAssociatedDeviceName(device.getDeviceId(), "");
    AssociatedDevice updatedDevice =
        connectedDeviceStorage.getAssociatedDevice(device.getDeviceId());

    assertThat(updatedDevice).isNotNull();
    assertThat(updatedDevice.getDeviceName()).isNull();
  }

  @Test
  public void setAssociatedDeviceName_issuesCallbackOnNameChange() {
    AssociatedDeviceCallback callback = mock(AssociatedDeviceCallback.class);
    connectedDeviceStorage.setAssociatedDeviceCallback(callback);
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            "00:00:00:00:00:00",
            /* deviceName= */ null,
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    String newName = "NewDeviceName";
    connectedDeviceStorage.setAssociatedDeviceName(device.getDeviceId(), newName);
    ArgumentCaptor<AssociatedDevice> captor = ArgumentCaptor.forClass(AssociatedDevice.class);
    verify(callback).onAssociatedDeviceUpdated(captor.capture());
    AssociatedDevice callbackDevice = captor.getValue();

    assertThat(callbackDevice.getDeviceId()).isEqualTo(device.getDeviceId());
    assertThat(callbackDevice.getDeviceAddress()).isEqualTo(device.getDeviceAddress());
    assertThat(callbackDevice.isConnectionEnabled()).isEqualTo(device.isConnectionEnabled());
    assertThat(callbackDevice.getDeviceName()).isEqualTo(newName);
  }

  @Test
  public void setAssociatedDeviceName_doesNotThrowOnUnrecognizedDeviceId() {
    connectedDeviceStorage.setAssociatedDeviceName(UUID.randomUUID().toString(), "name");
  }

  @Test
  public void updateAssociatedDeviceName_updatesWithNewName() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            "00:00:00:00:00:00",
            "OldDeviceName",
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    String newName = "NewDeviceName";
    connectedDeviceStorage.updateAssociatedDeviceName(device.getDeviceId(), newName);
    AssociatedDevice updatedDevice =
        connectedDeviceStorage.getAssociatedDevice(device.getDeviceId());

    assertThat(updatedDevice).isNotNull();
    assertThat(updatedDevice.getDeviceName()).isEqualTo(newName);
  }

  @Test
  public void updateAssociatedDeviceName_doesNotUpdateNameIfNewNameIsEmpty() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            "00:00:00:00:00:00",
            "OldDeviceName",
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    connectedDeviceStorage.updateAssociatedDeviceName(device.getDeviceId(), "");
    AssociatedDevice updatedDevice =
        connectedDeviceStorage.getAssociatedDevice(device.getDeviceId());

    assertThat(updatedDevice).isNotNull();
    assertThat(updatedDevice.getDeviceName()).isEqualTo(device.getDeviceName());
  }

  @Test
  public void updateAssociatedDeviceName_issuesCallbackOnNameChange() {
    AssociatedDeviceCallback callback = mock(AssociatedDeviceCallback.class);
    connectedDeviceStorage.setAssociatedDeviceCallback(callback);
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            "00:00:00:00:00:00",
            /* deviceName= */ null,
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    String newName = "NewDeviceName";
    connectedDeviceStorage.updateAssociatedDeviceName(device.getDeviceId(), newName);
    ArgumentCaptor<AssociatedDevice> captor = ArgumentCaptor.forClass(AssociatedDevice.class);
    verify(callback).onAssociatedDeviceUpdated(captor.capture());
    AssociatedDevice callbackDevice = captor.getValue();

    assertThat(callbackDevice.getDeviceId()).isEqualTo(device.getDeviceId());
    assertThat(callbackDevice.getDeviceAddress()).isEqualTo(device.getDeviceAddress());
    assertThat(callbackDevice.isConnectionEnabled()).isEqualTo(device.isConnectionEnabled());
    assertThat(callbackDevice.getDeviceName()).isEqualTo(newName);
  }

  @Test
  public void updateAssociatedDeviceName_doesNotThrowOnUnrecognizedDeviceId() {
    connectedDeviceStorage.updateAssociatedDeviceName(UUID.randomUUID().toString(), "name");
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
