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
import com.google.android.companionprotos.DeviceOS;
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
  private static final String TEST_ADDRESS = "00:00:00:00:00:00";

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

    connectedDeviceStorage =
        new ConnectedDeviceStorage(context, new FakeCryptoHelper(), database, directExecutor());
    addedAssociatedDevices = new ArrayList<>();
  }

  @After
  public void tearDown() {
    // Clear any associated devices added during tests.
    for (Pair<Integer, AssociatedDevice> device : addedAssociatedDevices) {
      connectedDeviceStorage.removeAssociatedDevice(device.second.getId());
    }
    connectedDeviceDatabase.close();
  }

  @Test
  public void getAssociatedDeviceIdsForUser_includesNewlyAddedDevice() {
    AssociatedDevice addedDevice = addRandomAssociatedDevice(ACTIVE_USER_ID);
    List<String> associatedDevices =
        connectedDeviceStorage.getAssociatedDeviceIdsForUser(ACTIVE_USER_ID);
    assertThat(associatedDevices).containsExactly(addedDevice.getId());
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
    connectedDeviceStorage.removeAssociatedDevice(addedDevice.getId());
    List<String> associatedDevices =
        connectedDeviceStorage.getAssociatedDeviceIdsForUser(ACTIVE_USER_ID);
    assertThat(associatedDevices).isEmpty();
  }

  @Test
  public void getAssociatedDeviceIdsForUser_returnsEmptyListIfNoDevicesFound() {
    assertThat(connectedDeviceStorage.getAssociatedDeviceIdsForUser(ACTIVE_USER_ID)).isEmpty();
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
    connectedDeviceStorage.removeAssociatedDevice(addedDevice.getId());
    List<AssociatedDevice> associatedDevices =
        connectedDeviceStorage.getAssociatedDevicesForUser(ACTIVE_USER_ID);
    assertThat(associatedDevices).isEmpty();
  }

  @Test
  public void getAssociatedDevicesForUser_returnsEmptyListIfNoDevicesFound() {
    assertThat(connectedDeviceStorage.getAssociatedDevicesForUser(ACTIVE_USER_ID)).isEmpty();
  }

  @Test
  public void getAllAssociatedDevices_returnsDevicesForAllUsers() {
    AssociatedDevice activeUserDevice = addRandomAssociatedDevice(ACTIVE_USER_ID);
    AssociatedDevice otherUserDevice = addRandomAssociatedDevice(ACTIVE_USER_ID + 1);

    List<AssociatedDevice> associatedDevices = connectedDeviceStorage.getAllAssociatedDevices();

    assertThat(associatedDevices).containsExactly(activeUserDevice, otherUserDevice);
  }

  @Test
  public void getAllAssociatedDevices_returnsEmptyListIfNoDevicesFound() {
    assertThat(connectedDeviceStorage.getAllAssociatedDevices()).isEmpty();
  }

  @Test
  public void getEncryptionKey_returnsSavedKey() {
    String deviceId = addRandomAssociatedDevice(ACTIVE_USER_ID).getId();
    byte[] key = ByteUtils.randomBytes(16);
    connectedDeviceStorage.saveEncryptionKey(deviceId, key);
    assertThat(connectedDeviceStorage.getEncryptionKey(deviceId)).isEqualTo(key);
  }

  @Test
  public void getEncryptionKey_returnsNullForUnrecognizedDeviceId() {
    String deviceId = addRandomAssociatedDevice(ACTIVE_USER_ID).getId();
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
            TEST_ADDRESS,
            /* name= */ null,
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    String newName = "NewDeviceName";
    connectedDeviceStorage.setAssociatedDeviceName(device.getId(), newName);
    AssociatedDevice updatedDevice = connectedDeviceStorage.getAssociatedDevice(device.getId());

    assertThat(updatedDevice).isNotNull();
    assertThat(updatedDevice.getName()).isEqualTo(newName);
  }

  @Test
  public void setAssociatedDeviceName_doesNotModifyNameIfDeviceAlreadyHasAName() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            TEST_ADDRESS,
            "TestName",
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    connectedDeviceStorage.setAssociatedDeviceName(device.getId(), "NewDeviceName");
    AssociatedDevice updatedDevice = connectedDeviceStorage.getAssociatedDevice(device.getId());

    assertThat(updatedDevice).isNotNull();
    assertThat(updatedDevice.getName()).isEqualTo(device.getName());
  }

  @Test
  public void setAssociatedDeviceName_doesNotModifyNameIfNewNameIsEmpty() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            TEST_ADDRESS,
            /* name= */ null,
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    connectedDeviceStorage.setAssociatedDeviceName(device.getId(), "");
    AssociatedDevice updatedDevice = connectedDeviceStorage.getAssociatedDevice(device.getId());

    assertThat(updatedDevice).isNotNull();
    assertThat(updatedDevice.getName()).isNull();
  }

  @Test
  public void setAssociatedDeviceName_issuesCallbackOnNameChange() {
    AssociatedDeviceCallback callback = mock(AssociatedDeviceCallback.class);
    connectedDeviceStorage.registerAssociatedDeviceCallback(callback);
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            TEST_ADDRESS,
            /* name= */ null,
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    String newName = "NewDeviceName";
    connectedDeviceStorage.setAssociatedDeviceName(device.getId(), newName);
    ArgumentCaptor<AssociatedDevice> captor = ArgumentCaptor.forClass(AssociatedDevice.class);
    verify(callback).onAssociatedDeviceUpdated(captor.capture());
    AssociatedDevice callbackDevice = captor.getValue();

    assertThat(callbackDevice.getId()).isEqualTo(device.getId());
    assertThat(callbackDevice.getAddress()).isEqualTo(device.getAddress());
    assertThat(callbackDevice.isConnectionEnabled()).isEqualTo(device.isConnectionEnabled());
    assertThat(callbackDevice.getName()).isEqualTo(newName);
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
            TEST_ADDRESS,
            "OldDeviceName",
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    String newName = "NewDeviceName";
    connectedDeviceStorage.updateAssociatedDeviceName(device.getId(), newName);
    AssociatedDevice updatedDevice = connectedDeviceStorage.getAssociatedDevice(device.getId());

    assertThat(updatedDevice).isNotNull();
    assertThat(updatedDevice.getName()).isEqualTo(newName);
  }

  @Test
  public void updateAssociatedDeviceName_doesNotUpdateNameIfNewNameIsEmpty() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            TEST_ADDRESS,
            "OldDeviceName",
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    connectedDeviceStorage.updateAssociatedDeviceName(device.getId(), "");
    AssociatedDevice updatedDevice = connectedDeviceStorage.getAssociatedDevice(device.getId());

    assertThat(updatedDevice).isNotNull();
    assertThat(updatedDevice.getName()).isEqualTo(device.getName());
  }

  @Test
  public void updateAssociatedDeviceName_issuesCallbackOnNameChange() {
    AssociatedDeviceCallback callback = mock(AssociatedDeviceCallback.class);
    connectedDeviceStorage.registerAssociatedDeviceCallback(callback);
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            TEST_ADDRESS,
            /* name= */ null,
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    String newName = "NewDeviceName";
    connectedDeviceStorage.updateAssociatedDeviceName(device.getId(), newName);
    ArgumentCaptor<AssociatedDevice> captor = ArgumentCaptor.forClass(AssociatedDevice.class);
    verify(callback).onAssociatedDeviceUpdated(captor.capture());
    AssociatedDevice callbackDevice = captor.getValue();

    assertThat(callbackDevice.getId()).isEqualTo(device.getId());
    assertThat(callbackDevice.getAddress()).isEqualTo(device.getAddress());
    assertThat(callbackDevice.isConnectionEnabled()).isEqualTo(device.isConnectionEnabled());
    assertThat(callbackDevice.getName()).isEqualTo(newName);
  }

  @Test
  public void updateAssociatedDeviceName_doesNotThrowOnUnrecognizedDeviceId() {
    connectedDeviceStorage.updateAssociatedDeviceName(UUID.randomUUID().toString(), "name");
  }

  @Test
  public void updateAssociatedDeviceOs_doesNotRemoveDeviceFromStorage() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            TEST_ADDRESS,
            /* name= */ null,
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    DeviceOS os = DeviceOS.ANDROID;
    connectedDeviceStorage.updateAssociatedDeviceOs(device.getId(), os);
    AssociatedDevice updatedDevice = connectedDeviceStorage.getAssociatedDevice(device.getId());

    assertThat(updatedDevice).isNotNull();
  }

  @Test
  public void deviceOsDefaultSetToUnknown() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            TEST_ADDRESS,
            "DeviceName",
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));
    AssociatedDevice retrievedDevice = connectedDeviceStorage.getAssociatedDevice(device.getId());

    assertThat(retrievedDevice.getOs()).isEqualTo(DeviceOS.DEVICE_OS_UNKNOWN);
  }

  @Test
  public void updateAssociatedDeviceOs_updatesWithNewOs() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            TEST_ADDRESS,
            "DeviceName",
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    DeviceOS os = DeviceOS.ANDROID;
    connectedDeviceStorage.updateAssociatedDeviceOs(device.getId(), os);
    AssociatedDevice updatedDevice = connectedDeviceStorage.getAssociatedDevice(device.getId());

    assertThat(updatedDevice.getOs()).isEqualTo(os);
  }

  @Test
  public void updateAssociatedDeviceOs_doesNotUpdateIfNoDevice() {
    String deviceId = UUID.randomUUID().toString();
    AssociatedDevice originalDevice = connectedDeviceStorage.getAssociatedDevice(deviceId);

    DeviceOS os = DeviceOS.IOS;
    connectedDeviceStorage.updateAssociatedDeviceOs(deviceId, os);
    AssociatedDevice updatedDevice = connectedDeviceStorage.getAssociatedDevice(deviceId);

    assertThat(originalDevice).isNull();
    assertThat(updatedDevice).isNull();
  }
  
  @Test
  public void updateAssociatedDeviceOsVersion_doesNotRemoveDeviceFromStorage() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            TEST_ADDRESS,
            /* name= */ null,
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    String osVersion = "TestCake";
    connectedDeviceStorage.updateAssociatedDeviceOsVersion(device.getId(), osVersion);
    AssociatedDevice updatedDevice = connectedDeviceStorage.getAssociatedDevice(device.getId());

    assertThat(updatedDevice).isNotNull();
  }

  @Test
  public void deviceOsVersionDefaultSetToNull() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            TEST_ADDRESS,
            /* name= */ null,
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));
    AssociatedDevice retrievedDevice = connectedDeviceStorage.getAssociatedDevice(device.getId());

    assertThat(retrievedDevice.getOsVersion()).isEqualTo(null);
  }

  @Test
  public void updateAssociatedDeviceOsVersion_updatesWithNewOsVersion() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            TEST_ADDRESS,
            "DeviceName",
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    String osVersion = "TestCake";
    connectedDeviceStorage.updateAssociatedDeviceOsVersion(device.getId(), osVersion);
    AssociatedDevice updatedDevice = connectedDeviceStorage.getAssociatedDevice(device.getId());

    assertThat(updatedDevice.getOsVersion()).isEqualTo(osVersion);
  }

  @Test
  public void updateAssociatedDeviceOsVersion_doesNotUpdateIfNoDevice() {
    String deviceId = UUID.randomUUID().toString();
    AssociatedDevice originalDevice = connectedDeviceStorage.getAssociatedDevice(deviceId);

    String osVersion = "TestCake";
    connectedDeviceStorage.updateAssociatedDeviceOsVersion(deviceId, osVersion);
    AssociatedDevice updatedDevice = connectedDeviceStorage.getAssociatedDevice(deviceId);

    assertThat(originalDevice).isNull();
    assertThat(updatedDevice).isNull();
  }

  @Test
  public void updateAssociatedDeviceOsVersion_updatesToEmptyVersion() {
    String originalOsVersion = "originalOSVersion";
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            TEST_ADDRESS,
            "DeviceName",
            /* isConnectionEnabled= */ true,
            /* userId= */ ACTIVE_USER_ID,
            DeviceOS.ANDROID,
            originalOsVersion,
            "originalSdkVersion");
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    String newOsVersion = "";
    connectedDeviceStorage.updateAssociatedDeviceOsVersion(device.getId(), newOsVersion);
    AssociatedDevice updatedDevice = connectedDeviceStorage.getAssociatedDevice(device.getId());

    assertThat(updatedDevice.getOsVersion()).isEqualTo(newOsVersion);
  }
  
  @Test
  public void updateAssociatedDeviceCompanionSdkVersion_doesNotRemoveDeviceFromStorage() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            TEST_ADDRESS,
            /* name= */ null,
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    String companionSdkVersion = "TestCake";
    connectedDeviceStorage.updateAssociatedDeviceCompanionSdkVersion(device.getId(), companionSdkVersion);
    AssociatedDevice updatedDevice = connectedDeviceStorage.getAssociatedDevice(device.getId());

    assertThat(updatedDevice).isNotNull();
  }

  @Test
  public void companionSdkVersionDefaultSetToNull() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            TEST_ADDRESS,
            /* name= */ null,
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));
    AssociatedDevice retrievedDevice = connectedDeviceStorage.getAssociatedDevice(device.getId());

    assertThat(retrievedDevice.getCompanionSdkVersion()).isEqualTo(null);
  }

  @Test
  public void updateAssociatedDeviceCompanionSdkVersion_updatesWithNewSdkVersion() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            TEST_ADDRESS,
            "DeviceName",
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    String companionSdkVersion = "TestCake";
    connectedDeviceStorage.updateAssociatedDeviceCompanionSdkVersion(
        device.getId(), companionSdkVersion);
    AssociatedDevice updatedDevice = connectedDeviceStorage.getAssociatedDevice(device.getId());

    assertThat(updatedDevice.getCompanionSdkVersion()).isEqualTo(companionSdkVersion);
  }

  @Test
  public void updateAssociatedDeviceCompanionSdkVersion_doesNotUpdateIfNoDevice() {
    String deviceId = UUID.randomUUID().toString();
    AssociatedDevice originalDevice = connectedDeviceStorage.getAssociatedDevice(deviceId);
    String sdkVersion = "TestCake";
    connectedDeviceStorage.updateAssociatedDeviceCompanionSdkVersion(deviceId, sdkVersion);
    AssociatedDevice updatedDevice = connectedDeviceStorage.getAssociatedDevice(deviceId);

    assertThat(originalDevice).isNull();
    assertThat(updatedDevice).isNull();
  }

  @Test
  public void updateAssociatedDeviceCompanionSdkVersion_updatesToEmptyVersion() {
    String originalSdkVersion = "originalSdkVersion";
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            TEST_ADDRESS,
            "DeviceName",
            /* isConnectionEnabled= */ true,
            /* userId= */ -1,
            DeviceOS.ANDROID,
            "originalOsVersion",
            originalSdkVersion);
    addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16));

    String newSdkVersion = "";
    connectedDeviceStorage.updateAssociatedDeviceCompanionSdkVersion(device.getId(), newSdkVersion);
    AssociatedDevice updatedDevice = connectedDeviceStorage.getAssociatedDevice(device.getId());

    assertThat(updatedDevice.getCompanionSdkVersion()).isEqualTo(newSdkVersion);
  }

  @Test
  public void getAssociatedDevicesNotBelongingToUser_includesDevicesNotMatchingUser() {
    AssociatedDevice device = addRandomAssociatedDevice(ACTIVE_USER_ID + 1);
    List<AssociatedDevice> associatedDevices =
        connectedDeviceStorage.getAssociatedDevicesNotBelongingToUser(ACTIVE_USER_ID);
    assertThat(associatedDevices).containsExactly(device);
  }

  @Test
  public void getAssociatedDevicesNotBelongingToUser_excludesDevicesMatchingUser() {
    addRandomAssociatedDevice(ACTIVE_USER_ID);
    List<AssociatedDevice> associatedDevices =
        connectedDeviceStorage.getAssociatedDevicesNotBelongingToUser(ACTIVE_USER_ID);
    assertThat(associatedDevices).isEmpty();
  }

  @Test
  public void getAssociatedDevicesNotBelongingToUser_returnsEmptyListIfNoDevicesFound() {
    assertThat(connectedDeviceStorage.getAssociatedDevicesNotBelongingToUser(ACTIVE_USER_ID))
        .isEmpty();
  }

  @Test
  public void getAssociatedDeviceIdsNotBelongingToUser_includesDevicesNotMatchingUser() {
    AssociatedDevice device = addRandomAssociatedDevice(ACTIVE_USER_ID + 1);
    List<String> associatedDevices =
        connectedDeviceStorage.getAssociatedDeviceIdsNotBelongingToUser(ACTIVE_USER_ID);
    assertThat(associatedDevices).containsExactly(device.getId());
  }

  @Test
  public void getAssociatedDeviceIdsNotBelongingToUser_excludesDevicesMatchingUser() {
    addRandomAssociatedDevice(ACTIVE_USER_ID);
    List<String> associatedDevices =
        connectedDeviceStorage.getAssociatedDeviceIdsNotBelongingToUser(ACTIVE_USER_ID);
    assertThat(associatedDevices).isEmpty();
  }

  @Test
  public void getAssociatedDeviceIdsNotBelongingToUser_returnsEmptyListIfNoDevicesFound() {
    assertThat(connectedDeviceStorage.getAssociatedDeviceIdsNotBelongingToUser(ACTIVE_USER_ID))
        .isEmpty();
  }

  @Test
  public void addAssociatedDeviceForUser_invokesCallback() {
    AssociatedDeviceCallback callback = mock(AssociatedDeviceCallback.class);
    connectedDeviceStorage.registerAssociatedDeviceCallback(callback);

    AssociatedDevice device = addRandomAssociatedDevice(ACTIVE_USER_ID);

    verify(callback).onAssociatedDeviceAdded(device);
  }

  @Test
  public void removeAssociatedDeviceForUser_invokesCallback() {
    AssociatedDeviceCallback callback = mock(AssociatedDeviceCallback.class);
    connectedDeviceStorage.registerAssociatedDeviceCallback(callback);
    AssociatedDevice device = addRandomAssociatedDevice(ACTIVE_USER_ID);

    connectedDeviceStorage.removeAssociatedDevice(device.getId());

    verify(callback).onAssociatedDeviceRemoved(device);
  }

  @Test
  public void updateAssociatedDeviceName_invokesCallback() {
    AssociatedDeviceCallback callback = mock(AssociatedDeviceCallback.class);
    connectedDeviceStorage.registerAssociatedDeviceCallback(callback);
    AssociatedDevice device = addRandomAssociatedDevice(ACTIVE_USER_ID);
    String newName = "New Name";

    connectedDeviceStorage.updateAssociatedDeviceName(device.getId(), newName);

    ArgumentCaptor<AssociatedDevice> captor = ArgumentCaptor.forClass(AssociatedDevice.class);
    verify(callback).onAssociatedDeviceUpdated(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo(newName);
  }

  @Test
  public void claimAssociatedDevice_setsCurrentUserIdOnAssociatedDevice() {
    AssociatedDeviceCallback callback = mock(AssociatedDeviceCallback.class);
    connectedDeviceStorage.registerAssociatedDeviceCallback(callback);
    AssociatedDevice device = addRandomAssociatedDevice(AssociatedDevice.UNCLAIMED_USER_ID);

    connectedDeviceStorage.claimAssociatedDevice(device.getId());

    ArgumentCaptor<AssociatedDevice> captor = ArgumentCaptor.forClass(AssociatedDevice.class);
    verify(callback).onAssociatedDeviceUpdated(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(0);
  }

  @Test
  public void claimAssociatedDevice_updatesAssociatedDeviceInStorage() {
    AssociatedDevice device = addRandomAssociatedDevice(AssociatedDevice.UNCLAIMED_USER_ID);
    connectedDeviceStorage.claimAssociatedDevice(device.getId());
    AssociatedDevice updatedDevice = connectedDeviceStorage.getAssociatedDevice(device.getId());

    assertThat(updatedDevice.getUserId()).isEqualTo(0);
  }

  @Test
  public void claimAssociatedDevice_unknownDeviceDoesNotThrow() {
    connectedDeviceStorage.claimAssociatedDevice(UUID.randomUUID().toString());
  }

  @Test
  public void removeAssociatedDeviceClaim_setsUnclaimedUserIdOnAssociatedDevice() {
    AssociatedDeviceCallback callback = mock(AssociatedDeviceCallback.class);
    connectedDeviceStorage.registerAssociatedDeviceCallback(callback);
    AssociatedDevice device = addRandomAssociatedDevice(AssociatedDevice.UNCLAIMED_USER_ID);

    connectedDeviceStorage.removeAssociatedDeviceClaim(device.getId());

    ArgumentCaptor<AssociatedDevice> captor = ArgumentCaptor.forClass(AssociatedDevice.class);
    verify(callback).onAssociatedDeviceUpdated(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(AssociatedDevice.UNCLAIMED_USER_ID);
  }

  @Test
  public void removeAssociatedDeviceClaim_unknownDeviceDoesNotThrow() {
    connectedDeviceStorage.removeAssociatedDeviceClaim(UUID.randomUUID().toString());
  }

  private AssociatedDevice addRandomAssociatedDevice(int userId) {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            TEST_ADDRESS,
            "Test Device",
            /* isConnectionEnabled= */ true);
    addAssociatedDevice(userId, device, ByteUtils.randomBytes(16));
    return device;
  }

  private void addAssociatedDevice(int userId, AssociatedDevice device, byte[] encryptionKey) {
    connectedDeviceStorage.addAssociatedDeviceForUser(userId, device);
    connectedDeviceStorage.saveEncryptionKey(device.getId(), encryptionKey);
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
