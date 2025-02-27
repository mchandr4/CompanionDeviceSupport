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

package com.google.android.connecteddevice.storage

import android.content.Context
import android.util.Base64
import android.util.Pair
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.companionprotos.DeviceOS
import com.google.android.connecteddevice.model.AssociatedDevice
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage.AssociatedDeviceCallback
import com.google.android.connecteddevice.util.ByteUtils
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import java.security.InvalidParameterException
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class ConnectedDeviceStorageTest {

  private lateinit var context: Context
  private lateinit var connectedDeviceDatabase: ConnectedDeviceDatabase
  private lateinit var addedAssociatedDevices: MutableList<Pair<Int, AssociatedDevice>>

  private lateinit var connectedDeviceStorage: ConnectedDeviceStorage

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    connectedDeviceDatabase =
      Room.inMemoryDatabaseBuilder(context, ConnectedDeviceDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(directExecutor())
        .build()
    val database: AssociatedDeviceDao = connectedDeviceDatabase.associatedDeviceDao()
    addedAssociatedDevices = mutableListOf()

    connectedDeviceStorage =
      ConnectedDeviceStorage(context, FakeCryptoHelper(), database, directExecutor())
  }

  @After
  fun tearDown() =
    runBlocking<Unit> {
      // Clear any associated devices added during tests.
      for (device: Pair<Int, AssociatedDevice> in addedAssociatedDevices) {
        connectedDeviceStorage.removeAssociatedDevice(device.second.id)
      }
      connectedDeviceDatabase.close()
    }

  @Test
  fun getAssociatedDeviceIdsForUser_includesNewlyAddedDevice() =
    runBlocking<Unit> {
      val addedDevice: AssociatedDevice = addRandomAssociatedDevice(ACTIVE_USER_ID)
      val associatedDevices = connectedDeviceStorage.getAssociatedDeviceIdsForUser(ACTIVE_USER_ID)
      assertThat(associatedDevices).containsExactly(addedDevice.id)
    }

  @Test
  fun getAssociatedDeviceIdsForUser_excludesDeviceAddedForOtherUser() =
    runBlocking<Unit> {
      val unused = addRandomAssociatedDevice(ACTIVE_USER_ID)
      val associatedDevices =
        connectedDeviceStorage.getAssociatedDeviceIdsForUser(ACTIVE_USER_ID + 1)
      assertThat(associatedDevices).isEmpty()
    }

  @Test
  fun getAssociatedDeviceIdsForUser_excludesRemovedDevice() =
    runBlocking<Unit> {
      val addedDevice: AssociatedDevice = addRandomAssociatedDevice(ACTIVE_USER_ID)
      connectedDeviceStorage.removeAssociatedDevice(addedDevice.id)
      val associatedDevices = connectedDeviceStorage.getAssociatedDeviceIdsForUser(ACTIVE_USER_ID)
      assertThat(associatedDevices).isEmpty()
    }

  @Test
  fun getAssociatedDeviceIdsForUser_returnsEmptyListIfNoDevicesFound() =
    runBlocking<Unit> {
      assertThat(connectedDeviceStorage.getAssociatedDeviceIdsForUser(ACTIVE_USER_ID)).isEmpty()
    }

  @Test
  fun getAssociatedDevicesForUser_includesNewlyAddedDevice() =
    runBlocking<Unit> {
      val addedDevice: AssociatedDevice = addRandomAssociatedDevice(ACTIVE_USER_ID)
      val associatedDevices = connectedDeviceStorage.getAssociatedDevicesForUser(ACTIVE_USER_ID)
      assertThat(associatedDevices).containsExactly(addedDevice)
    }

  @Test
  fun getAssociatedDevicesForUser_excludesDeviceAddedForOtherUser() =
    runBlocking<Unit> {
      val unused = addRandomAssociatedDevice(ACTIVE_USER_ID)
      val associatedDevices =
        connectedDeviceStorage.getAssociatedDeviceIdsForUser(ACTIVE_USER_ID + 1)
      assertThat(associatedDevices).isEmpty()
    }

  @Test
  fun getAssociatedDevicesForUser_excludesRemovedDevice() =
    runBlocking<Unit> {
      val addedDevice: AssociatedDevice = addRandomAssociatedDevice(ACTIVE_USER_ID)
      connectedDeviceStorage.removeAssociatedDevice(addedDevice.id)
      val associatedDevices = connectedDeviceStorage.getAssociatedDevicesForUser(ACTIVE_USER_ID)
      assertThat(associatedDevices).isEmpty()
    }

  @Test
  fun getAssociatedDevicesForUser_returnsEmptyListIfNoDevicesFound() =
    runBlocking<Unit> {
      assertThat(connectedDeviceStorage.getAssociatedDevicesForUser(ACTIVE_USER_ID)).isEmpty()
    }

  @Test
  fun getAllAssociatedDevices_returnsDevicesForAllUsers() =
    runBlocking<Unit> {
      val activeUserDevice: AssociatedDevice = addRandomAssociatedDevice(ACTIVE_USER_ID)
      val otherUserDevice: AssociatedDevice = addRandomAssociatedDevice(ACTIVE_USER_ID + 1)

      val associatedDevices = connectedDeviceStorage.getAllAssociatedDevices()

      assertThat(associatedDevices).containsExactly(activeUserDevice, otherUserDevice)
    }

  @Test
  fun getAllAssociatedDevices_returnsEmptyListIfNoDevicesFound() =
    runBlocking<Unit> { assertThat(connectedDeviceStorage.getAllAssociatedDevices()).isEmpty() }

  @Test
  fun getEncryptionKey_returnsSavedKey() =
    runBlocking<Unit> {
      val deviceId: String = addRandomAssociatedDevice(ACTIVE_USER_ID).id
      val key: ByteArray = ByteUtils.randomBytes(16)
      connectedDeviceStorage.saveEncryptionKey(deviceId, key)
      assertThat(connectedDeviceStorage.getEncryptionKey(deviceId)).isEqualTo(key)
    }

  @Test
  fun getEncryptionKey_returnsNullForUnrecognizedDeviceId() =
    runBlocking<Unit> {
      val deviceId: String = addRandomAssociatedDevice(ACTIVE_USER_ID).id
      connectedDeviceStorage.saveEncryptionKey(deviceId, ByteUtils.randomBytes(16))
      assertThat(connectedDeviceStorage.getEncryptionKey(UUID.randomUUID().toString())).isNull()
    }

  @Test
  fun saveChallengeSecret_throwsForInvalidLengthSecret() =
    runBlocking<Unit> {
      val invalidSecret: ByteArray =
        ByteUtils.randomBytes(ConnectedDeviceStorage.CHALLENGE_SECRET_BYTES - 1)
      assertFailsWith<InvalidParameterException> {
        connectedDeviceStorage.saveChallengeSecret(UUID.randomUUID().toString(), invalidSecret)
      }
    }

  @Test
  fun setAssociatedDeviceName_setsNameIfNull() =
    runBlocking<Unit> {
      val device: AssociatedDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          TEST_ADDRESS,
          /* name= */ null,
          /* isConnectionEnabled= */ true,
        )
      addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16))

      val newName: String = "NewDeviceName"
      connectedDeviceStorage.setAssociatedDeviceName(device.id, newName)
      val updatedDevice = assertNotNull(connectedDeviceStorage.getAssociatedDevice(device.id))

      assertThat(updatedDevice.name).isEqualTo(newName)
    }

  @Test
  fun setAssociatedDeviceName_doesNotModifyNameIfDeviceAlreadyHasAName() =
    runBlocking<Unit> {
      val device: AssociatedDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          TEST_ADDRESS,
          "TestName",
          /* isConnectionEnabled= */ true,
        )
      addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16))

      connectedDeviceStorage.setAssociatedDeviceName(device.id, "NewDeviceName")
      val updatedDevice = assertNotNull(connectedDeviceStorage.getAssociatedDevice(device.id))

      assertThat(updatedDevice.name).isEqualTo(device.name)
    }

  @Test
  fun setAssociatedDeviceName_doesNotModifyNameIfNewNameIsEmpty() =
    runBlocking<Unit> {
      val device: AssociatedDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          TEST_ADDRESS,
          /* name= */ null,
          /* isConnectionEnabled= */ true,
        )
      addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16))

      connectedDeviceStorage.setAssociatedDeviceName(device.id, "")
      val updatedDevice = assertNotNull(connectedDeviceStorage.getAssociatedDevice(device.id))

      assertThat(updatedDevice.name).isNull()
    }

  @Test
  fun setAssociatedDeviceName_issuesCallbackOnNameChange() =
    runBlocking<Unit> {
      val callback: AssociatedDeviceCallback = mock<AssociatedDeviceCallback>()
      connectedDeviceStorage.registerAssociatedDeviceCallback(callback)
      val device: AssociatedDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          TEST_ADDRESS,
          /* name= */ null,
          /* isConnectionEnabled= */ true,
        )
      addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16))

      val newName: String = "NewDeviceName"
      connectedDeviceStorage.setAssociatedDeviceName(device.id, newName)

      val updated =
        argumentCaptor<AssociatedDevice>().run {
          verify(callback).onAssociatedDeviceUpdated(capture())
          firstValue
        }
      assertThat(updated.id).isEqualTo(device.id)
      assertThat(updated.address).isEqualTo(device.address)
      assertThat(updated.isConnectionEnabled).isEqualTo(device.isConnectionEnabled)
      assertThat(updated.name).isEqualTo(newName)
    }

  @Test
  fun setAssociatedDeviceName_doesNotThrowOnUnrecognizedDeviceId() =
    runBlocking<Unit> {
      connectedDeviceStorage.setAssociatedDeviceName(UUID.randomUUID().toString(), "name")
    }

  @Test
  fun updateAssociatedDeviceName_updatesWithNewName() =
    runBlocking<Unit> {
      val device: AssociatedDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          TEST_ADDRESS,
          "OldDeviceName",
          /* isConnectionEnabled= */ true,
        )
      addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16))

      val newName: String = "NewDeviceName"
      connectedDeviceStorage.updateAssociatedDeviceName(device.id, newName)
      val updatedDevice = assertNotNull(connectedDeviceStorage.getAssociatedDevice(device.id))

      assertThat(updatedDevice.name).isEqualTo(newName)
    }

  @Test
  fun updateAssociatedDeviceName_doesNotUpdateNameIfNewNameIsEmpty() =
    runBlocking<Unit> {
      val device: AssociatedDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          TEST_ADDRESS,
          "OldDeviceName",
          /* isConnectionEnabled= */ true,
        )
      addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16))

      connectedDeviceStorage.updateAssociatedDeviceName(device.id, "")
      val updatedDevice = assertNotNull(connectedDeviceStorage.getAssociatedDevice(device.id))

      assertThat(updatedDevice.name).isEqualTo(device.name)
    }

  @Test
  fun updateAssociatedDeviceName_issuesCallbackOnNameChange() =
    runBlocking<Unit> {
      val callback: AssociatedDeviceCallback = mock<AssociatedDeviceCallback>()
      connectedDeviceStorage.registerAssociatedDeviceCallback(callback)
      val device: AssociatedDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          TEST_ADDRESS,
          /* name= */ null,
          /* isConnectionEnabled= */ true,
        )
      addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16))

      val newName: String = "NewDeviceName"
      connectedDeviceStorage.updateAssociatedDeviceName(device.id, newName)

      val updated =
        argumentCaptor<AssociatedDevice>().run {
          verify(callback).onAssociatedDeviceUpdated(capture())
          firstValue
        }
      assertThat(updated.id).isEqualTo(device.id)
      assertThat(updated.address).isEqualTo(device.address)
      assertThat(updated.isConnectionEnabled).isEqualTo(device.isConnectionEnabled)
      assertThat(updated.name).isEqualTo(newName)
    }

  @Test
  fun updateAssociatedDeviceName_doesNotThrowOnUnrecognizedDeviceId() =
    runBlocking<Unit> {
      connectedDeviceStorage.updateAssociatedDeviceName(UUID.randomUUID().toString(), "name")
    }

  @Test
  fun updateAssociatedDeviceOs_doesNotRemoveDeviceFromStorage() =
    runBlocking<Unit> {
      val device: AssociatedDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          TEST_ADDRESS,
          /* name= */ null,
          /* isConnectionEnabled= */ true,
        )
      addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16))

      val os: DeviceOS = DeviceOS.ANDROID
      connectedDeviceStorage.updateAssociatedDeviceOs(device.id, os)
      assertNotNull(connectedDeviceStorage.getAssociatedDevice(device.id))
    }

  @Test
  fun deviceOsDefaultSetToUnknown() =
    runBlocking<Unit> {
      val device: AssociatedDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          TEST_ADDRESS,
          "DeviceName",
          /* isConnectionEnabled= */ true,
        )
      addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16))
      val retrievedDevice = assertNotNull(connectedDeviceStorage.getAssociatedDevice(device.id))

      assertThat(retrievedDevice.os).isEqualTo(DeviceOS.DEVICE_OS_UNKNOWN)
    }

  @Test
  fun updateAssociatedDeviceOs_updatesWithNewOs() =
    runBlocking<Unit> {
      val device: AssociatedDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          TEST_ADDRESS,
          "DeviceName",
          /* isConnectionEnabled= */ true,
        )
      addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16))

      val os: DeviceOS = DeviceOS.ANDROID
      connectedDeviceStorage.updateAssociatedDeviceOs(device.id, os)
      val updatedDevice = assertNotNull(connectedDeviceStorage.getAssociatedDevice(device.id))

      assertThat(updatedDevice.os).isEqualTo(os)
    }

  @Test
  fun updateAssociatedDeviceOs_doesNotUpdateIfNoDevice() =
    runBlocking<Unit> {
      val deviceId: String = UUID.randomUUID().toString()
      val originalDevice: AssociatedDevice? = connectedDeviceStorage.getAssociatedDevice(deviceId)

      val os: DeviceOS = DeviceOS.IOS
      connectedDeviceStorage.updateAssociatedDeviceOs(deviceId, os)
      val updatedDevice: AssociatedDevice? = connectedDeviceStorage.getAssociatedDevice(deviceId)

      assertThat(originalDevice).isNull()
      assertThat(updatedDevice).isNull()
    }

  @Test
  fun updateAssociatedDeviceOsVersion_doesNotRemoveDeviceFromStorage() =
    runBlocking<Unit> {
      val device: AssociatedDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          TEST_ADDRESS,
          /* name= */ null,
          /* isConnectionEnabled= */ true,
        )
      addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16))

      val osVersion: String = "TestCake"
      connectedDeviceStorage.updateAssociatedDeviceOsVersion(device.id, osVersion)

      assertNotNull(connectedDeviceStorage.getAssociatedDevice(device.id))
    }

  @Test
  fun deviceOsVersionDefaultSetToNull() =
    runBlocking<Unit> {
      val device: AssociatedDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          TEST_ADDRESS,
          /* name= */ null,
          /* isConnectionEnabled= */ true,
        )
      addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16))
      val retrievedDevice = assertNotNull(connectedDeviceStorage.getAssociatedDevice(device.id))

      assertThat(retrievedDevice.osVersion).isEqualTo(null)
    }

  @Test
  fun updateAssociatedDeviceOsVersion_updatesWithNewOsVersion() =
    runBlocking<Unit> {
      val device: AssociatedDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          TEST_ADDRESS,
          "DeviceName",
          /* isConnectionEnabled= */ true,
        )
      addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16))

      val osVersion: String = "TestCake"
      connectedDeviceStorage.updateAssociatedDeviceOsVersion(device.id, osVersion)
      val updatedDevice = assertNotNull(connectedDeviceStorage.getAssociatedDevice(device.id))

      assertThat(updatedDevice.osVersion).isEqualTo(osVersion)
    }

  @Test
  fun updateAssociatedDeviceOsVersion_doesNotUpdateIfNoDevice() =
    runBlocking<Unit> {
      val deviceId: String = UUID.randomUUID().toString()
      val originalDevice: AssociatedDevice? = connectedDeviceStorage.getAssociatedDevice(deviceId)

      val osVersion: String = "TestCake"
      connectedDeviceStorage.updateAssociatedDeviceOsVersion(deviceId, osVersion)
      val updatedDevice: AssociatedDevice? = connectedDeviceStorage.getAssociatedDevice(deviceId)

      assertThat(originalDevice).isNull()
      assertThat(updatedDevice).isNull()
    }

  @Test
  fun updateAssociatedDeviceOsVersion_updatesToEmptyVersion() =
    runBlocking<Unit> {
      val originalOsVersion: String = "originalOSVersion"
      val device: AssociatedDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          TEST_ADDRESS,
          "DeviceName",
          /* isConnectionEnabled= */ true,
          /* userId= */ ACTIVE_USER_ID,
          DeviceOS.ANDROID,
          originalOsVersion,
          "originalSdkVersion",
        )
      addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16))

      val newOsVersion: String = ""
      connectedDeviceStorage.updateAssociatedDeviceOsVersion(device.id, newOsVersion)
      val updatedDevice = assertNotNull(connectedDeviceStorage.getAssociatedDevice(device.id))

      assertThat(updatedDevice.osVersion).isEqualTo(newOsVersion)
    }

  @Test
  fun updateAssociatedDeviceCompanionSdkVersion_doesNotRemoveDeviceFromStorage() =
    runBlocking<Unit> {
      val device: AssociatedDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          TEST_ADDRESS,
          /* name= */ null,
          /* isConnectionEnabled= */ true,
        )
      addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16))

      val companionSdkVersion: String = "TestCake"
      connectedDeviceStorage.updateAssociatedDeviceCompanionSdkVersion(
        device.id,
        companionSdkVersion,
      )

      assertNotNull(connectedDeviceStorage.getAssociatedDevice(device.id))
    }

  @Test
  fun companionSdkVersionDefaultSetToNull() =
    runBlocking<Unit> {
      val device: AssociatedDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          TEST_ADDRESS,
          /* name= */ null,
          /* isConnectionEnabled= */ true,
        )
      addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16))
      val retrievedDevice = assertNotNull(connectedDeviceStorage.getAssociatedDevice(device.id))

      assertThat(retrievedDevice.companionSdkVersion).isEqualTo(null)
    }

  @Test
  fun updateAssociatedDeviceCompanionSdkVersion_updatesWithNewSdkVersion() =
    runBlocking<Unit> {
      val device: AssociatedDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          TEST_ADDRESS,
          "DeviceName",
          /* isConnectionEnabled= */ true,
        )
      addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16))

      val companionSdkVersion: String = "TestCake"
      connectedDeviceStorage.updateAssociatedDeviceCompanionSdkVersion(
        device.id,
        companionSdkVersion,
      )
      val updatedDevice = assertNotNull(connectedDeviceStorage.getAssociatedDevice(device.id))

      assertThat(updatedDevice.companionSdkVersion).isEqualTo(companionSdkVersion)
    }

  @Test
  fun updateAssociatedDeviceCompanionSdkVersion_doesNotUpdateIfNoDevice() =
    runBlocking<Unit> {
      val deviceId: String = UUID.randomUUID().toString()
      val originalDevice: AssociatedDevice? = connectedDeviceStorage.getAssociatedDevice(deviceId)
      val sdkVersion: String = "TestCake"
      connectedDeviceStorage.updateAssociatedDeviceCompanionSdkVersion(deviceId, sdkVersion)
      val updatedDevice: AssociatedDevice? = connectedDeviceStorage.getAssociatedDevice(deviceId)

      assertThat(originalDevice).isNull()
      assertThat(updatedDevice).isNull()
    }

  @Test
  fun updateAssociatedDeviceCompanionSdkVersion_updatesToEmptyVersion() =
    runBlocking<Unit> {
      val originalSdkVersion: String = "originalSdkVersion"
      val device: AssociatedDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          TEST_ADDRESS,
          "DeviceName",
          /* isConnectionEnabled= */ true,
          /* userId= */ -1,
          DeviceOS.ANDROID,
          "originalOsVersion",
          originalSdkVersion,
        )
      addAssociatedDevice(ACTIVE_USER_ID, device, ByteUtils.randomBytes(16))

      val newSdkVersion: String = ""
      connectedDeviceStorage.updateAssociatedDeviceCompanionSdkVersion(device.id, newSdkVersion)
      val updatedDevice = assertNotNull(connectedDeviceStorage.getAssociatedDevice(device.id))

      assertThat(updatedDevice.companionSdkVersion).isEqualTo(newSdkVersion)
    }

  @Test
  fun getAssociatedDevicesNotBelongingToUser_includesDevicesNotMatchingUser() =
    runBlocking<Unit> {
      val device: AssociatedDevice = addRandomAssociatedDevice(ACTIVE_USER_ID + 1)
      val associatedDevices =
        connectedDeviceStorage.getAssociatedDevicesNotBelongingToUser(ACTIVE_USER_ID)
      assertThat(associatedDevices).containsExactly(device)
    }

  @Test
  fun getAssociatedDevicesNotBelongingToUser_excludesDevicesMatchingUser() =
    runBlocking<Unit> {
      val unused = addRandomAssociatedDevice(ACTIVE_USER_ID)
      val associatedDevices =
        connectedDeviceStorage.getAssociatedDevicesNotBelongingToUser(ACTIVE_USER_ID)
      assertThat(associatedDevices).isEmpty()
    }

  @Test
  fun getAssociatedDevicesNotBelongingToUser_returnsEmptyListIfNoDevicesFound() =
    runBlocking<Unit> {
      assertThat(connectedDeviceStorage.getAssociatedDevicesNotBelongingToUser(ACTIVE_USER_ID))
        .isEmpty()
    }

  @Test
  fun getAssociatedDeviceIdsNotBelongingToUser_includesDevicesNotMatchingUser() =
    runBlocking<Unit> {
      val device: AssociatedDevice = addRandomAssociatedDevice(ACTIVE_USER_ID + 1)
      val associatedDevices =
        connectedDeviceStorage.getAssociatedDeviceIdsNotBelongingToUser(ACTIVE_USER_ID)
      assertThat(associatedDevices).containsExactly(device.id)
    }

  @Test
  fun getAssociatedDeviceIdsNotBelongingToUser_excludesDevicesMatchingUser() =
    runBlocking<Unit> {
      val unused = addRandomAssociatedDevice(ACTIVE_USER_ID)
      val associatedDevices =
        connectedDeviceStorage.getAssociatedDeviceIdsNotBelongingToUser(ACTIVE_USER_ID)
      assertThat(associatedDevices).isEmpty()
    }

  @Test
  fun getAssociatedDeviceIdsNotBelongingToUser_returnsEmptyListIfNoDevicesFound() =
    runBlocking<Unit> {
      assertThat(connectedDeviceStorage.getAssociatedDeviceIdsNotBelongingToUser(ACTIVE_USER_ID))
        .isEmpty()
    }

  @Test
  fun addAssociatedDeviceForUser_invokesCallback() =
    runBlocking<Unit> {
      val callback: AssociatedDeviceCallback = mock<AssociatedDeviceCallback>()
      connectedDeviceStorage.registerAssociatedDeviceCallback(callback)

      val device: AssociatedDevice = addRandomAssociatedDevice(ACTIVE_USER_ID)

      verify(callback).onAssociatedDeviceAdded(device)
    }

  @Test
  fun removeAssociatedDeviceForUser_invokesCallback() =
    runBlocking<Unit> {
      val callback: AssociatedDeviceCallback = mock<AssociatedDeviceCallback>()
      connectedDeviceStorage.registerAssociatedDeviceCallback(callback)
      val device: AssociatedDevice = addRandomAssociatedDevice(ACTIVE_USER_ID)

      connectedDeviceStorage.removeAssociatedDevice(device.id)

      verify(callback).onAssociatedDeviceRemoved(device)
    }

  @Test
  fun updateAssociatedDeviceName_invokesCallback() =
    runBlocking<Unit> {
      val callback: AssociatedDeviceCallback = mock<AssociatedDeviceCallback>()
      connectedDeviceStorage.registerAssociatedDeviceCallback(callback)
      val device: AssociatedDevice = addRandomAssociatedDevice(ACTIVE_USER_ID)
      val newName: String = "New Name"

      connectedDeviceStorage.updateAssociatedDeviceName(device.id, newName)

      val updated =
        argumentCaptor<AssociatedDevice>().run {
          verify(callback).onAssociatedDeviceUpdated(capture())
          firstValue
        }
      assertThat(updated.name).isEqualTo(newName)
    }

  @Test
  fun claimAssociatedDevice_setsCurrentUserIdOnAssociatedDevice() =
    runBlocking<Unit> {
      val callback: AssociatedDeviceCallback = mock<AssociatedDeviceCallback>()
      connectedDeviceStorage.registerAssociatedDeviceCallback(callback)
      val device: AssociatedDevice = addRandomAssociatedDevice(AssociatedDevice.UNCLAIMED_USER_ID)

      connectedDeviceStorage.claimAssociatedDevice(device.id)

      val updated =
        argumentCaptor<AssociatedDevice>().run {
          verify(callback).onAssociatedDeviceUpdated(capture())
          firstValue
        }
      assertThat(updated.userId).isEqualTo(0)
    }

  @Test
  fun claimAssociatedDevice_updatesAssociatedDeviceInStorage() =
    runBlocking<Unit> {
      val device: AssociatedDevice = addRandomAssociatedDevice(AssociatedDevice.UNCLAIMED_USER_ID)
      connectedDeviceStorage.claimAssociatedDevice(device.id)
      val updatedDevice = assertNotNull(connectedDeviceStorage.getAssociatedDevice(device.id))

      assertThat(updatedDevice.userId).isEqualTo(0)
    }

  @Test
  fun claimAssociatedDevice_unknownDeviceDoesNotThrow() =
    runBlocking<Unit> { connectedDeviceStorage.claimAssociatedDevice(UUID.randomUUID().toString()) }

  @Test
  fun removeAssociatedDeviceClaim_setsUnclaimedUserIdOnAssociatedDevice() =
    runBlocking<Unit> {
      val callback: AssociatedDeviceCallback = mock<AssociatedDeviceCallback>()
      connectedDeviceStorage.registerAssociatedDeviceCallback(callback)
      val device: AssociatedDevice = addRandomAssociatedDevice(AssociatedDevice.UNCLAIMED_USER_ID)

      connectedDeviceStorage.removeAssociatedDeviceClaim(device.id)

      val updated =
        argumentCaptor<AssociatedDevice>().run {
          verify(callback).onAssociatedDeviceUpdated(capture())
          firstValue
        }
      assertThat(updated.userId).isEqualTo(AssociatedDevice.UNCLAIMED_USER_ID)
    }

  @Test
  fun removeAssociatedDeviceClaim_unknownDeviceDoesNotThrow() =
    runBlocking<Unit> {
      connectedDeviceStorage.removeAssociatedDeviceClaim(UUID.randomUUID().toString())
    }

  // This method cannot be annotated with @CanIgnoreReturnValue because the test will be published
  // externally.
  private suspend fun addRandomAssociatedDevice(userId: Int): AssociatedDevice {
    val device: AssociatedDevice =
      AssociatedDevice(
        UUID.randomUUID().toString(),
        TEST_ADDRESS,
        "Test Device",
        /* isConnectionEnabled= */ true,
      )
    addAssociatedDevice(userId, device, ByteUtils.randomBytes(16))
    return device
  }

  private suspend fun addAssociatedDevice(
    userId: Int,
    device: AssociatedDevice,
    encryptionKey: ByteArray,
  ) {
    connectedDeviceStorage.addAssociatedDeviceForUser(userId, device)
    connectedDeviceStorage.saveEncryptionKey(device.id, encryptionKey)
    addedAssociatedDevices.add(Pair(userId, device))
  }

  /** A CryptoHelper that does base64 de/encoding to simulate encryption. */
  private class FakeCryptoHelper : CryptoHelper {

    override fun encrypt(value: ByteArray?): String? {
      return Base64.encodeToString(value, Base64.DEFAULT)
    }

    override fun decrypt(value: String?): ByteArray? {
      return Base64.decode(value, Base64.DEFAULT)
    }
  }

  companion object {
    private const val ACTIVE_USER_ID: Int = 10
    private const val TEST_ADDRESS: String = "00:00:00:00:00:00"
  }
}
