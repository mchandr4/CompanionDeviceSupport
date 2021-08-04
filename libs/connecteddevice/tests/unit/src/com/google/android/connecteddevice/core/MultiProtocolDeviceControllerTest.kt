/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.android.connecteddevice.core

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.api.IAssociationCallback
import com.google.android.connecteddevice.connection.MultiProtocolSecureChannel
import com.google.android.connecteddevice.connection.ProtocolStream
import com.google.android.connecteddevice.core.DeviceController.Callback
import com.google.android.connecteddevice.core.util.mockToBeAlive
import com.google.android.connecteddevice.model.AssociatedDevice
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.model.DeviceMessage.OperationType
import com.google.android.connecteddevice.model.Errors
import com.google.android.connecteddevice.oob.OobChannelFactory
import com.google.android.connecteddevice.oob.OobConnectionManager
import com.google.android.connecteddevice.storage.ConnectedDeviceDatabase
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage.CHALLENGE_SECRET_BYTES
import com.google.android.connecteddevice.storage.CryptoHelper
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.util.ByteUtils
import com.google.android.connecteddevice.util.ThreadSafeCallbacks
import com.google.android.encryptionrunner.EncryptionRunnerFactory
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.validateMockitoUsage
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.fail
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private val TEST_CHALLENGE = "test Challenge".toByteArray()

@RunWith(AndroidJUnit4::class)
class MultiProtocolDeviceControllerTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val testConnectionProtocol: TestConnectionProtocol = spy(TestConnectionProtocol())
  private val mockCallback: Callback = mock()
  private val mockStream: ProtocolStream = mock()
  private val mockSecureChannel: MultiProtocolSecureChannel = mock()
  private val mockOobManager: OobConnectionManager = mock()
  private val mockOobChannelFactory: OobChannelFactory = mock()
  private val mockAssociationCallback: IAssociationCallback = mockToBeAlive()
  private val protocols = setOf(testConnectionProtocol)
  private lateinit var deviceController: MultiProtocolDeviceController
  private lateinit var secureChannel: MultiProtocolSecureChannel
  private lateinit var spyStorage: ConnectedDeviceStorage
  private lateinit var connectedDeviceDatabase: ConnectedDeviceDatabase

  @Before
  fun setUp() {
    connectedDeviceDatabase =
      Room.inMemoryDatabaseBuilder(context, ConnectedDeviceDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(directExecutor())
        .build()
    val database = connectedDeviceDatabase.associatedDeviceDao()
    spyStorage = spy(ConnectedDeviceStorage(context, Base64CryptoHelper(), database))
    whenever(spyStorage.hashWithChallengeSecret(any(), any())).thenReturn(TEST_CHALLENGE)
    deviceController =
      MultiProtocolDeviceController(
        protocols,
        spyStorage,
        mockOobChannelFactory,
        mockOobManager,
        directExecutor()
      )
    deviceController.registerCallback(mockCallback, directExecutor())
    secureChannel =
      spy(
        MultiProtocolSecureChannel(mockStream, spyStorage, EncryptionRunnerFactory.newFakeRunner())
      )
  }

  @After
  fun cleanUp() {
    connectedDeviceDatabase.close()
    // Validate after each test to get accurate indication of Mockito misuse.
    validateMockitoUsage()
  }

  @Test
  fun startAssociation_startedSuccessfully() {
    val deviceName = "TestDeviceName"
    deviceController.startAssociation(deviceName, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(eq(deviceName), capture())
      firstValue.onDiscoveryStartedSuccessfully()
    }
    verify(mockAssociationCallback).onAssociationStartSuccess(deviceName)
  }

  @Test
  fun startAssociation_onDiscoveryFailedToStartInvokesOnAssociationStartFailure() {
    val deviceName = "TestDeviceName"

    deviceController.startAssociation(deviceName, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(eq(deviceName), capture())
      firstValue.onDiscoveryFailedToStart()
    }

    verify(mockAssociationCallback).onAssociationStartFailure()
  }

  @Test
  fun initiateConnectionToDevice_invokesStartConnectionDiscovery() {
    val testUuid = UUID.randomUUID()

    deviceController.initiateConnectionToDevice(testUuid)

    verify(testConnectionProtocol).startConnectionDiscovery(eq(testUuid), any(), any())
  }

  @Test
  fun reset_invokesConnectionProtocolReset() {
    deviceController.reset()

    verify(testConnectionProtocol).reset()
  }

  @Test
  fun onDeviceConnected_registerCallback() {
    val testUuid = UUID.randomUUID()
    deviceController.initiateConnectionToDevice(testUuid)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
    }

    assertThat(testConnectionProtocol.callbackList).hasSize(1)
  }

  @Test
  fun onDeviceDisconnected_invokesCallback() {
    val deviceId = UUID.randomUUID()
    val testProtocolId = UUID.randomUUID()
    whenever(spyStorage.allAssociatedDevices)
      .thenReturn(
        listOf(
          AssociatedDevice(
            deviceId.toString(),
            "deviceAddress",
            "deviceName",
            /* isConnectionEnabled= */ true
          )
        )
      )
    deviceController.initiateConnectionToDevice(deviceId)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(testProtocolId.toString())
    }

    val callbacks =
      testConnectionProtocol.callbackList[testProtocolId.toString()]
        ?: fail("Failed to find callbacks.")
    callbacks.invoke { callback -> callback.onDeviceDisconnected(testProtocolId.toString()) }

    verify(mockCallback).onDeviceDisconnected(any())
  }

  @Test
  fun onDeviceDisconnected_attemptsReconnectIfDeviceIsEnabled() {
    val deviceId = UUID.randomUUID()
    val testProtocolId = UUID.randomUUID()
    val associatedDevice =
      AssociatedDevice(
        deviceId.toString(),
        "deviceAddress",
        "deviceName",
        /* isConnectionEnabled= */ true
      )
    spyStorage.addAssociatedDeviceForActiveUser(associatedDevice)
    deviceController.initiateConnectionToDevice(deviceId)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(eq(deviceId), any(), capture())
      firstValue.onDeviceConnected(testProtocolId.toString())
    }

    val callbacks =
      testConnectionProtocol.callbackList[testProtocolId.toString()]
        ?: fail("Failed to find callbacks.")
    callbacks.invoke { callback -> callback.onDeviceDisconnected(testProtocolId.toString()) }

    verify(testConnectionProtocol, times(2)).startConnectionDiscovery(eq(deviceId), any(), any())
  }

  @Test
  fun onDeviceDisconnected_doesNotAttemptReconnectForDisabledDevice() {
    val deviceId = UUID.randomUUID()
    val testProtocolId = UUID.randomUUID()
    val associatedDevice =
      AssociatedDevice(
        deviceId.toString(),
        "deviceAddress",
        "deviceName",
        /* isConnectionEnabled= */ true
      )
    spyStorage.addAssociatedDeviceForActiveUser(associatedDevice)
    deviceController.initiateConnectionToDevice(deviceId)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(eq(deviceId), any(), capture())
      firstValue.onDeviceConnected(testProtocolId.toString())
    }

    spyStorage.updateAssociatedDeviceConnectionEnabled(
      deviceId.toString(),
      /* isConnectionEnabled= */ false
    )
    val callbacks =
      testConnectionProtocol.callbackList[testProtocolId.toString()]
        ?: fail("Failed to find callbacks.")
    callbacks.invoke { callback -> callback.onDeviceDisconnected(testProtocolId.toString()) }

    verify(testConnectionProtocol).startConnectionDiscovery(eq(deviceId), any(), any())
  }

  @Test
  fun sendMessage_sendMessageFailsWhenDeviceIsNotReady() {
    val testUuid = UUID.randomUUID()
    val testDeviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        true,
        OperationType.CLIENT_MESSAGE,
        "test message".toByteArray()
      )

    assertThat(deviceController.sendMessage(testUuid, testDeviceMessage)).isFalse()
  }

  @Test
  fun sendMessage_sendsMessageToChannel() {
    val deviceId = UUID.randomUUID()
    val testProtocolId = UUID.randomUUID()
    val testDeviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        true,
        OperationType.CLIENT_MESSAGE,
        "test message".toByteArray()
      )
    deviceController.initiateConnectionToDevice(deviceId)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(testProtocolId.toString())
    }

    val device = deviceController.getConnectedDevice(deviceId) ?: fail("Failed to find the device.")
    device.secureChannel = secureChannel
    deviceController.sendMessage(deviceId, testDeviceMessage)

    verify(secureChannel).sendClientMessage(testDeviceMessage)
  }

  @Test
  fun isReadyToSendMessage_returnsFalseIfChannelIsNotResolved() {
    val testUuid = UUID.randomUUID()
    val testDeviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        true,
        OperationType.CLIENT_MESSAGE,
        "test message".toByteArray()
      )

    deviceController.initiateConnectionToDevice(testUuid)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
    }

    assertThat(deviceController.sendMessage(testUuid, testDeviceMessage)).isFalse()
  }

  @Test
  fun isReadyToSendMessage_returnsTrueWithResolvedChannel() {
    val testUuid = UUID.randomUUID()
    val testProtocolId = UUID.randomUUID()

    deviceController.initiateConnectionToDevice(testUuid)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(testProtocolId.toString())
    }

    deviceController.getConnectedDevice(testUuid)?.secureChannel = secureChannel

    assertThat(deviceController.isReadyToSendMessage(testUuid)).isTrue()
  }

  @Test
  fun notifyVerificationCodeAccepted_sendsDeviceId() {
    val deviceName = "TestDeviceName"
    deviceController.startAssociation(deviceName, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(eq(deviceName), capture())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
    }
    val device =
      deviceController.getConnectedDevice(mockAssociationCallback)
        ?: fail("Failed to find the device.")
    device.secureChannel = secureChannel
    deviceController.notifyVerificationCodeAccepted()

    argumentCaptor<DeviceMessage>().apply {
      verify(secureChannel).sendClientMessage(capture())
      assertThat(firstValue.message).isEqualTo(ByteUtils.uuidToBytes(spyStorage.uniqueId))
    }
  }

  @Test
  fun notifyVerificationCodeAccepted_notifiesChannel() {
    val deviceName = "TestDeviceName"
    deviceController.startAssociation(deviceName, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(eq(deviceName), capture())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
    }
    val device =
      deviceController.getConnectedDevice(mockAssociationCallback)
        ?: fail("Failed to find the device.")
    device.secureChannel = secureChannel
    deviceController.notifyVerificationCodeAccepted()

    verify(secureChannel).notifyVerificationCodeAccepted()
  }

  @Test
  fun notifyVerificationCodeAccepted_unrecognizedDeviceDoesNotThrow() {
    deviceController.notifyVerificationCodeAccepted()
  }

  @Test
  fun notifyVerificationCodeAccepted_deviceWithoutChannelDoesNotThrow() {
    val deviceName = "TestDeviceName"
    deviceController.startAssociation(deviceName, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(eq(deviceName), capture())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
    }

    deviceController.notifyVerificationCodeAccepted()
  }

  @Test
  fun handleSecureChannelMessage_firstAssociationMessageSavesIdAndSecretAndIssuesDeviceConnected() {
    val deviceName = "TestDeviceName"
    val deviceId = UUID.randomUUID()
    val secret = ByteUtils.randomBytes(CHALLENGE_SECRET_BYTES)
    val testDeviceMessage =
      DeviceMessage(
        null,
        true,
        OperationType.CLIENT_MESSAGE,
        ByteUtils.uuidToBytes(deviceId) + secret
      )
    deviceController.startAssociation(deviceName, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(eq(deviceName), capture())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
    }

    deviceController.handleSecureChannelMessage(
      testDeviceMessage,
      deviceController.getConnectedDevice(mockAssociationCallback)
        ?: fail("Failed to find the device.")
    )

    verify(spyStorage).saveChallengeSecret(deviceId.toString(), secret)
    verify(mockCallback).onDeviceConnected(any())
    verify(mockCallback).onSecureChannelEstablished(any())
  }

  @Test
  fun handleSecureChannelMessage_associationStorageErrorInvokesOnAssociationErrorCallback() {
    val deviceName = "TestDeviceName"
    val deviceId = UUID.randomUUID()
    val secret = ByteUtils.randomBytes(CHALLENGE_SECRET_BYTES - 1)
    val testDeviceMessage =
      DeviceMessage(
        null,
        true,
        OperationType.CLIENT_MESSAGE,
        ByteUtils.uuidToBytes(deviceId) + secret
      )
    deviceController.startAssociation(deviceName, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(eq(deviceName), capture())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
    }

    deviceController.handleSecureChannelMessage(
      testDeviceMessage,
      deviceController.getConnectedDevice(mockAssociationCallback)
        ?: fail("Failed to find the device.")
    )

    verify(mockAssociationCallback).onAssociationError(any())
  }

  @Test
  fun handleSecureChannelMessage_issuesOnMessageReceived() {
    val deviceId = UUID.randomUUID()
    val testProtocolId = UUID.randomUUID()
    whenever(spyStorage.allAssociatedDevices)
      .thenReturn(
        listOf(
          AssociatedDevice(
            deviceId.toString(),
            "deviceAddress",
            "deviceName",
            /* isConnectionEnabled= */ true
          )
        )
      )
    val testDeviceMessage =
      DeviceMessage(null, true, OperationType.CLIENT_MESSAGE, ByteUtils.randomBytes(10))
    deviceController.initiateConnectionToDevice(deviceId)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(testProtocolId.toString())
    }
    val device = deviceController.getConnectedDevice(deviceId) ?: fail("Failed to find the device.")

    deviceController.handleSecureChannelMessage(testDeviceMessage, device)

    argumentCaptor<ConnectedDevice>().apply {
      verify(mockCallback).onMessageReceived(capture(), eq(testDeviceMessage))
      assertThat(firstValue.deviceId).isEqualTo(deviceId.toString())
    }
  }

  @Test
  fun connectedDevices_returnsAllConnectedDevices() {
    val activeUserDeviceId = UUID.randomUUID()
    val activeUserDevice =
      AssociatedDevice(
        activeUserDeviceId.toString(),
        "userDeviceAddress",
        "userDeviceName",
        /* isConnectionEnabled= */ true
      )
    val otherUserDeviceId = UUID.randomUUID()
    val otherUserDevice =
      AssociatedDevice(
        otherUserDeviceId.toString(),
        "otherUserDeviceAddress",
        "otherUserDeviceName",
        /* isConnectionEnabled= */ true
      )
    val disconnectedDeviceId = UUID.randomUUID()
    val disconnectedDevice =
      AssociatedDevice(
        disconnectedDeviceId.toString(),
        "otherUserDeviceAddress",
        "otherUserDeviceName",
        /* isConnectionEnabled= */ true
      )
    whenever(spyStorage.activeUserAssociatedDevices).thenReturn(listOf(activeUserDevice))
    whenever(spyStorage.allAssociatedDevices)
      .thenReturn(listOf(activeUserDevice, otherUserDevice, disconnectedDevice))
    // Recreate controller after registering mock returns since they are used in the constructor.
    deviceController =
      MultiProtocolDeviceController(
        protocols,
        spyStorage,
        mockOobChannelFactory,
        mockOobManager,
        directExecutor()
      )
    deviceController.registerCallback(mockCallback, directExecutor())

    deviceController.initiateConnectionToDevice(activeUserDeviceId)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol)
        .startConnectionDiscovery(eq(activeUserDeviceId), any(), capture())
      firstValue.onDeviceConnected(activeUserDeviceId.toString())
    }
    deviceController.initiateConnectionToDevice(otherUserDeviceId)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol)
        .startConnectionDiscovery(eq(otherUserDeviceId), any(), capture())
      firstValue.onDeviceConnected(otherUserDeviceId.toString())
    }
    deviceController.initiateConnectionToDevice(disconnectedDeviceId)

    val connectedDevices = deviceController.connectedDevices

    assertThat(connectedDevices).hasSize(2)
    val activeUserConnectedDevice =
      connectedDevices.firstOrNull { it.deviceId == activeUserDeviceId.toString() }
    assertThat(activeUserConnectedDevice).isNotNull()
    assertThat(activeUserConnectedDevice?.deviceName).isEqualTo(activeUserDevice.deviceName)
    assertThat(activeUserConnectedDevice?.isAssociatedWithDriver).isTrue()
    val otherUserConnectedDevice =
      connectedDevices.firstOrNull { it.deviceId == otherUserDeviceId.toString() }
    assertThat(otherUserConnectedDevice).isNotNull()
    assertThat(otherUserConnectedDevice?.deviceName).isEqualTo(otherUserDevice.deviceName)
    assertThat(otherUserConnectedDevice?.isAssociatedWithDriver).isFalse()
  }

  @Test
  fun connectedDevices_returnsEmptyListWithNoConnectedDevices() {
    val activeUserDevice =
      AssociatedDevice(
        UUID.randomUUID().toString(),
        "userDeviceAddress",
        "userDeviceName",
        /* isConnectionEnabled= */ true
      )
    val otherUserDevice =
      AssociatedDevice(
        UUID.randomUUID().toString(),
        "otherUserDeviceAddress",
        "otherUserDeviceName",
        /* isConnectionEnabled= */ true
      )
    whenever(spyStorage.activeUserAssociatedDevices).thenReturn(listOf(activeUserDevice))
    whenever(spyStorage.allAssociatedDevices).thenReturn(listOf(activeUserDevice, otherUserDevice))

    val connectedDevices = deviceController.connectedDevices

    assertThat(connectedDevices).isEmpty()
  }

  @Test
  fun connectedDevices_returnsEmptyListWithNoAssociatedDevices() {
    val activeUserDeviceId = UUID.randomUUID()
    val otherUserDeviceId = UUID.randomUUID()
    whenever(spyStorage.activeUserAssociatedDevices).thenReturn(listOf())
    whenever(spyStorage.allAssociatedDevices).thenReturn(listOf())
    deviceController.initiateConnectionToDevice(activeUserDeviceId)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol)
        .startConnectionDiscovery(eq(activeUserDeviceId), any(), capture())
      firstValue.onDeviceConnected(activeUserDeviceId.toString())
    }
    deviceController.initiateConnectionToDevice(otherUserDeviceId)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol)
        .startConnectionDiscovery(eq(otherUserDeviceId), any(), capture())
      firstValue.onDeviceConnected(otherUserDeviceId.toString())
    }

    val connectedDevices = deviceController.connectedDevices

    assertThat(connectedDevices).isEmpty()
  }

  @Test
  fun disconnectDevice_stopsDiscoveryAndDisconnectsAllProtocolsForDevice() {
    val deviceId = UUID.randomUUID()
    val testProtocolId1 = UUID.randomUUID().toString()
    val testProtocolId2 = UUID.randomUUID().toString()
    val protocol1 = spy(TestConnectionProtocol())
    val protocol2 = spy(TestConnectionProtocol())
    deviceController =
      MultiProtocolDeviceController(
          setOf(protocol1, protocol2),
          spyStorage,
          mockOobChannelFactory,
          mockOobManager,
          directExecutor()
        )
        .apply { registerCallback(mockCallback, directExecutor()) }
    deviceController.initiateConnectionToDevice(deviceId)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(protocol1).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(testProtocolId1)
    }
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(protocol2).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(testProtocolId2)
    }

    deviceController.disconnectDevice(deviceId)

    verify(protocol1).disconnectDevice(testProtocolId1)
    verify(protocol1).stopConnectionDiscovery(deviceId)
    verify(protocol2).disconnectDevice(testProtocolId2)
    verify(protocol2).stopConnectionDiscovery(deviceId)
  }

  @Test
  fun disconnectDevice_unrecognizedDeviceDoesNotThrow() {
    deviceController.disconnectDevice(UUID.randomUUID())
  }

  @Test
  fun disconnectDevice_invokesOnDeviceDisconnected() {
    val deviceId = UUID.randomUUID()
    val testProtocolId = UUID.randomUUID().toString()
    val protocol = spy(TestConnectionProtocol())
    deviceController =
      MultiProtocolDeviceController(
          setOf(protocol),
          spyStorage,
          mockOobChannelFactory,
          mockOobManager,
          directExecutor()
        )
        .apply { registerCallback(mockCallback, directExecutor()) }
    deviceController.initiateConnectionToDevice(deviceId)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(protocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(testProtocolId)
    }

    deviceController.disconnectDevice(deviceId)

    argumentCaptor<ConnectedDevice>().apply {
      verify(mockCallback).onDeviceDisconnected(capture())
      assertThat(firstValue.deviceId).isEqualTo(deviceId.toString())
    }
  }

  @Test
  fun disconnectDevice_removesDeviceFromConnectedDevices() {
    val deviceId = UUID.randomUUID()
    val testProtocolId = UUID.randomUUID().toString()
    val protocol = spy(TestConnectionProtocol())
    val associatedDevice =
      AssociatedDevice(
        deviceId.toString(),
        "userDeviceAddress",
        "userDeviceName",
        /* isConnectionEnabled= */ true
      )
    whenever(spyStorage.activeUserAssociatedDevices).thenReturn(listOf(associatedDevice))
    whenever(spyStorage.allAssociatedDevices).thenReturn(listOf(associatedDevice))
    deviceController =
      MultiProtocolDeviceController(
          setOf(protocol),
          spyStorage,
          mockOobChannelFactory,
          mockOobManager,
          directExecutor()
        )
        .apply { registerCallback(mockCallback, directExecutor()) }
    deviceController.initiateConnectionToDevice(deviceId)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(protocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(testProtocolId)
    }
    assertThat(deviceController.connectedDevices).isNotEmpty()

    deviceController.disconnectDevice(deviceId)

    assertThat(deviceController.connectedDevices).isEmpty()
  }

  @Test
  fun encryptAndSendOobVerificationCode_encryptedSuccessfully_notifySecureChannel() {
    val testConnectedDevice = MultiProtocolDeviceController.ConnectedRemoteDevice()
    val testOobCode = "testCode".toByteArray()
    val encryptedCode = "encryptedCode".toByteArray()
    whenever(mockOobManager.encryptVerificationCode(testOobCode)).thenReturn(encryptedCode)
    testConnectedDevice.secureChannel = mockSecureChannel

    deviceController.encryptAndSendOobVerificationCode(testOobCode, testConnectedDevice)

    verify(mockSecureChannel).sendOobEncryptedCode(encryptedCode)
  }

  @Test
  fun encryptAndSendOobVerificationCode_encryptedFailed_notifyAssociationError() {
    val testConnectedDevice = MultiProtocolDeviceController.ConnectedRemoteDevice()
    val testOobCode = "testCode".toByteArray()
    testConnectedDevice.secureChannel = mockSecureChannel
    testConnectedDevice.callback = mockAssociationCallback
    whenever(mockOobManager.encryptVerificationCode(testOobCode)).then { throw Exception() }

    deviceController.encryptAndSendOobVerificationCode(testOobCode, testConnectedDevice)

    verify(mockAssociationCallback).onAssociationError(Errors.DEVICE_ERROR_INVALID_VERIFICATION)
  }

  @Test
  fun confirmOobVerificationCode_decryptedAndConfirmedSuccessfully_notifySecureChannel() {
    val testConnectedDevice = MultiProtocolDeviceController.ConnectedRemoteDevice()
    val testOobCode = "encryptedTestCode".toByteArray()
    val decryptedTestOobCode = "decryptedTestCode".toByteArray()
    testConnectedDevice.secureChannel = mockSecureChannel
    whenever(mockOobManager.decryptVerificationCode(testOobCode)).thenReturn(decryptedTestOobCode)
    deviceController.encryptAndSendOobVerificationCode(decryptedTestOobCode, testConnectedDevice)

    deviceController.confirmOobVerificationCode(testOobCode, testConnectedDevice)

    verify(mockSecureChannel).notifyVerificationCodeAccepted()
  }

  @Test
  fun confirmOobVerificationCode_decryptedFailed_notifyAssociationError() {
    val testConnectedDevice = MultiProtocolDeviceController.ConnectedRemoteDevice()
    val testOobCode = "encryptedTestCode".toByteArray()
    testConnectedDevice.secureChannel = mockSecureChannel
    testConnectedDevice.callback = mockAssociationCallback
    whenever(mockOobManager.decryptVerificationCode(testOobCode)).then { throw Exception() }
    deviceController.confirmOobVerificationCode(testOobCode, testConnectedDevice)

    verify(mockAssociationCallback).onAssociationError(Errors.DEVICE_ERROR_INVALID_VERIFICATION)
  }

  @Test
  fun confirmOobVerificationCode_recordDidNotMatch_notifyAssociationError() {
    val testConnectedDevice = MultiProtocolDeviceController.ConnectedRemoteDevice()
    val originalOobCode = "testCode".toByteArray()
    val encryptedTestOobCode = "encryptedTestCode".toByteArray()
    testConnectedDevice.secureChannel = mockSecureChannel
    testConnectedDevice.callback = mockAssociationCallback
    whenever(mockOobManager.encryptVerificationCode(any())).thenReturn(encryptedTestOobCode)
    whenever(mockOobManager.decryptVerificationCode(encryptedTestOobCode))
      .thenReturn(originalOobCode)
    // Set an incorrect oob code.
    deviceController.encryptAndSendOobVerificationCode(encryptedTestOobCode, testConnectedDevice)

    deviceController.confirmOobVerificationCode(encryptedTestOobCode, testConnectedDevice)

    verify(mockAssociationCallback).onAssociationError(Errors.DEVICE_ERROR_INVALID_VERIFICATION)
  }

  @Test
  fun stopAssociation_disconnectPendingDevice() {
    val deviceName = "TestDeviceName"
    val testProtocolId = UUID.randomUUID().toString()
    deviceController.startAssociation(deviceName, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(eq(deviceName), capture())
      firstValue.onDeviceConnected(testProtocolId)
    }
    deviceController.stopAssociation()
    verify(testConnectionProtocol).disconnectDevice(testProtocolId)
    verify(testConnectionProtocol).stopAssociationDiscovery()
  }

  @Test
  fun start_initiatesConnectionToAllEnabledActiveUserDevices() {
    val enabledDeviceId = UUID.randomUUID()
    val disabledDeviceId = UUID.randomUUID()
    val enabledDevice =
      AssociatedDevice(
        enabledDeviceId.toString(),
        /* deviceAddress= */ "",
        /* deviceName= */ null,
        /* isConnectionEnabled= */ true
      )
    val disabledDevice =
      AssociatedDevice(
        disabledDeviceId.toString(),
        /* deviceAddress= */ "",
        /* deviceName= */ null,
        /* isConnectionEnabled= */ false
      )
    whenever(spyStorage.activeUserAssociatedDevices)
      .thenReturn(listOf(disabledDevice, enabledDevice))

    deviceController.start()

    verify(testConnectionProtocol).startConnectionDiscovery(eq(enabledDeviceId), any(), any())
    verify(testConnectionProtocol, never())
      .startConnectionDiscovery(eq(disabledDeviceId), any(), any())
  }

  private class Base64CryptoHelper : CryptoHelper {
    override fun encrypt(value: ByteArray?): String? = Base64.encodeToString(value, Base64.DEFAULT)

    override fun decrypt(value: String?): ByteArray? = Base64.decode(value, Base64.DEFAULT)
  }

  open class TestConnectionProtocol : ConnectionProtocol() {
    val callbackList: ConcurrentHashMap<String, ThreadSafeCallbacks<DeviceCallback>> =
      deviceCallbacks

    override val isDeviceVerificationRequired = false

    override fun startAssociationDiscovery(name: String, callback: DiscoveryCallback) {}

    override fun startConnectionDiscovery(
      id: UUID,
      challenge: ConnectChallenge,
      callback: DiscoveryCallback
    ) {}

    override fun stopAssociationDiscovery() {}

    override fun stopConnectionDiscovery(id: UUID) {}

    override fun sendData(protocolId: String, data: ByteArray, callback: DataSendCallback?) {}

    override fun disconnectDevice(protocolId: String) {}

    override fun reset() {}

    override fun getMaxWriteSize(protocolId: String): Int {
      return 0
    }
  }
}
