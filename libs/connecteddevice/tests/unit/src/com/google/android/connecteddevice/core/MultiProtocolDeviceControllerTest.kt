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
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Base64
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.api.IAssociationCallback
import com.google.android.connecteddevice.connection.MultiProtocolSecureChannel
import com.google.android.connecteddevice.connection.ProtocolStream
import com.google.android.connecteddevice.core.DeviceController.Callback
import com.google.android.connecteddevice.core.util.mockToBeAlive
import com.google.android.connecteddevice.core.util.mockToBeDead
import com.google.android.connecteddevice.model.AssociatedDevice
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.model.DeviceMessage.OperationType
import com.google.android.connecteddevice.model.Errors
import com.google.android.connecteddevice.model.OobData
import com.google.android.connecteddevice.model.StartAssociationResponse
import com.google.android.connecteddevice.oob.OobRunner
import com.google.android.connecteddevice.storage.ConnectedDeviceDatabase
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage.Companion.CHALLENGE_SECRET_BYTES
import com.google.android.connecteddevice.storage.CryptoHelper
import com.google.android.connecteddevice.transport.ConnectChallenge
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.IDataSendCallback
import com.google.android.connecteddevice.transport.IDeviceDisconnectedListener
import com.google.android.connecteddevice.transport.IDiscoveryCallback
import com.google.android.connecteddevice.transport.ProtocolDelegate
import com.google.android.connecteddevice.util.ByteUtils
import com.google.android.encryptionrunner.EncryptionRunnerFactory
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import java.security.InvalidParameterException
import java.util.UUID
import kotlin.test.fail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.validateMockitoUsage
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private val TEST_CHALLENGE = "test Challenge".toByteArray()
private val TEST_OOB_DATA =
  OobData("key".toByteArray(), "serverIv".toByteArray(), "clientIv".toByteArray())

@RunWith(AndroidJUnit4::class)
class MultiProtocolDeviceControllerTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  // Logic launched under the Android lifecycle scope defaults to the Main dispatcher. To advance
  // the `delay` calls in those coroutines, set this testDispatcher as the Main dispatcher.
  private val testDispatcher = StandardTestDispatcher()
  private val testConnectionProtocol: TestConnectionProtocol = spy(TestConnectionProtocol())
  private val mockCallback = mock<Callback>()
  private val mockStream = mock<ProtocolStream>()
  private val mockOobRunner = mock<OobRunner> { on { sendOobData() } doReturn TEST_OOB_DATA }
  private val mockAssociationCallback = mockToBeAlive<IAssociationCallback>()
  private val mockDeadAssociationCallback = mockToBeDead<IAssociationCallback>()
  private val protocolDelegate = ProtocolDelegate().apply { addProtocol(testConnectionProtocol) }
  private val testAssociationServiceUuid = ParcelUuid(UUID.randomUUID())
  private lateinit var deviceController: MultiProtocolDeviceController
  private lateinit var secureChannel: MultiProtocolSecureChannel
  private lateinit var mockStorage: ConnectedDeviceStorage
  private lateinit var connectedDeviceDatabase: ConnectedDeviceDatabase

  @Before
  fun setUp() {
    connectedDeviceDatabase =
      Room.inMemoryDatabaseBuilder(context, ConnectedDeviceDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(directExecutor())
        .build()

    mockStorage = mock()
    whenever(mockStorage.uniqueId).thenReturn(UUID.randomUUID())
    mockStorage.stub {
      onBlocking { hashWithChallengeSecret(any(), any()) } doReturn TEST_CHALLENGE
      onBlocking { getAllAssociatedDevices() } doReturn emptyList()
      onBlocking { getDriverAssociatedDevices() } doReturn emptyList()
      onBlocking { getPassengerAssociatedDevices() } doReturn emptyList()
    }
    deviceController =
      MultiProtocolDeviceController(
        context,
        TestLifecycleOwner(),
        protocolDelegate,
        mockStorage,
        mockOobRunner,
        testAssociationServiceUuid.uuid,
        enablePassenger = false,
      )
    deviceController.registerCallback(mockCallback, directExecutor())
    secureChannel =
      spy(
        MultiProtocolSecureChannel(mockStream, mockStorage, EncryptionRunnerFactory.newFakeRunner())
      )
  }

  @After
  fun cleanUp() {
    connectedDeviceDatabase.close()
    // Validate after each test to get accurate indication of Mockito misuse.
    validateMockitoUsage()
  }

  @Test
  fun start_retriesStorageOnException() {
    Dispatchers.setMain(testDispatcher)

    val transientErrorStorage =
      object :
        ConnectedDeviceStorage(
          context,
          Base64CryptoHelper(),
          connectedDeviceDatabase.associatedDeviceDao(),
          directExecutor(),
        ) {
        var attempts = 0

        override suspend fun getAllAssociatedDevices(): List<AssociatedDevice> {
          attempts++
          if (attempts == 1) {
            throw SQLiteCantOpenDatabaseException()
          }
          return super.getAllAssociatedDevices()
        }
      }

    MultiProtocolDeviceController(
        context,
        TestLifecycleOwner(),
        protocolDelegate,
        transientErrorStorage,
        mockOobRunner,
        testAssociationServiceUuid.uuid,
        enablePassenger = false,
      )
      .start()

    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(transientErrorStorage.attempts).isEqualTo(2)

    Dispatchers.resetMain()
  }

  @Test
  fun start_accessDatabaseViaTheSameThreadToAvoidException() {
    val transientErrorStorage =
      object :
        ConnectedDeviceStorage(
          context,
          Base64CryptoHelper(),
          connectedDeviceDatabase.associatedDeviceDao(),
          directExecutor(),
        ) {
        var attempts = 0

        override suspend fun getAllAssociatedDevices(): List<AssociatedDevice> {
          attempts++
          if (attempts < 10) {
            throw SQLiteCantOpenDatabaseException()
          }
          return super.getAllAssociatedDevices()
        }

        override suspend fun getDriverAssociatedDevices(): List<AssociatedDevice> {
          attempts++
          // Throws exception if this method get called before [getAllAssociatedDevices()]
          // finishes.
          if (attempts != 10) throw SQLiteCantOpenDatabaseException()
          return super.getDriverAssociatedDevices()
        }
      }
    try {
      MultiProtocolDeviceController(
          context,
          TestLifecycleOwner(),
          protocolDelegate,
          transientErrorStorage,
          mockOobRunner,
          testAssociationServiceUuid.uuid,
          enablePassenger = false,
        )
        .start()
    } catch (e: SQLiteCantOpenDatabaseException) {
      fail("Should not have thrown any exception")
    }
  }

  @Test
  fun start_connectsToPassengerDevicesWhenPassengerEnabled() =
    runBlocking<Unit> {
      val driverId = ParcelUuid(UUID.randomUUID())
      val driverDevice =
        AssociatedDevice(
          driverId.toString(),
          /* address= */ "",
          /* name= */ null,
          /* isConnectionEnabled= */ true,
        )
      val passengerId = ParcelUuid(UUID.randomUUID())
      val passengerDevice =
        AssociatedDevice(
          passengerId.toString(),
          /* address= */ "",
          /* name= */ null,
          /* isConnectionEnabled= */ true,
        )
      mockStorage.stub {
        onBlocking { getDriverAssociatedDevices() } doReturn listOf(driverDevice)
        onBlocking { getPassengerAssociatedDevices() } doReturn listOf(passengerDevice)
        onBlocking { getAllAssociatedDevices() } doReturn listOf(driverDevice, passengerDevice)
      }
      deviceController =
        MultiProtocolDeviceController(
          context,
          TestLifecycleOwner(),
          protocolDelegate,
          mockStorage,
          mockOobRunner,
          testAssociationServiceUuid.uuid,
          enablePassenger = true,
        )

      deviceController.start()

      verify(testConnectionProtocol).startConnectionDiscovery(eq(driverId), any(), any())
      verify(testConnectionProtocol).startConnectionDiscovery(eq(passengerId), any(), any())
    }

  @Test
  fun start_doesNotConnectToPassengerDevicesWhenPassengerDisabled() {
    val driverId = ParcelUuid(UUID.randomUUID())
    val driverDevice =
      AssociatedDevice(
        driverId.uuid.toString(),
        /* address= */ "",
        /* name= */ null,
        /* isConnectionEnabled= */ true,
      )
    val passengerId = ParcelUuid(UUID.randomUUID())
    val passengerDevice =
      AssociatedDevice(
        passengerId.toString(),
        /* address= */ "",
        /* name= */ null,
        /* isConnectionEnabled= */ true,
      )
    mockStorage.stub {
      onBlocking { getDriverAssociatedDevices() } doReturn listOf(driverDevice)
      onBlocking { getPassengerAssociatedDevices() } doReturn listOf(passengerDevice)
      onBlocking { getAllAssociatedDevices() } doReturn listOf(driverDevice, passengerDevice)
    }
    deviceController =
      MultiProtocolDeviceController(
        context,
        TestLifecycleOwner(),
        protocolDelegate,
        mockStorage,
        mockOobRunner,
        testAssociationServiceUuid.uuid,
        enablePassenger = false,
      )

    deviceController.start()

    verify(testConnectionProtocol).startConnectionDiscovery(eq(driverId), any(), any())
    verify(testConnectionProtocol, never()).startConnectionDiscovery(eq(passengerId), any(), any())
  }

  @Test
  fun startAssociation_startedWithoutIdentifier() {
    deviceController.startAssociation(TEST_DEVICE_NAME, mockAssociationCallback)

    verify(testConnectionProtocol)
      .startAssociationDiscovery(eq(TEST_DEVICE_NAME), eq(testAssociationServiceUuid), any())
  }

  @Test
  fun startAssociation_startedSuccessfully() {
    val testIdentifier = ParcelUuid(UUID.randomUUID())

    deviceController.startAssociation(
      TEST_DEVICE_NAME,
      mockAssociationCallback,
      testIdentifier.uuid,
    )
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol)
        .startAssociationDiscovery(eq(TEST_DEVICE_NAME), eq(testIdentifier), capture())
      firstValue.onDiscoveryStartedSuccessfully()
    }

    verify(mockOobRunner).sendOobData()
    val response =
      argumentCaptor<StartAssociationResponse>().run {
        verify(mockAssociationCallback).onAssociationStartSuccess(capture())
        firstValue
      }
    val expectedResponse =
      StartAssociationResponse(
        TEST_OOB_DATA,
        ByteUtils.hexStringToByteArray(TEST_DEVICE_NAME),
        TEST_DEVICE_NAME,
      )
    assertThat(response.hashCode()).isEqualTo(expectedResponse.hashCode())
    assertThat(expectedResponse).isEqualTo(response)
  }

  @Test
  fun startAssociation_callbackBinderIsDead_DoNotThrow() {
    val testIdentifier = ParcelUuid(UUID.randomUUID())

    deviceController.startAssociation(
      TEST_DEVICE_NAME,
      mockDeadAssociationCallback,
      testIdentifier.uuid,
    )
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol)
        .startAssociationDiscovery(eq(TEST_DEVICE_NAME), eq(testIdentifier), capture())
      firstValue.onDiscoveryStartedSuccessfully()
    }
    verify(mockAssociationCallback, never())
      .onAssociationStartSuccess(
        StartAssociationResponse(
          TEST_OOB_DATA,
          ByteUtils.hexStringToByteArray(TEST_DEVICE_NAME),
          TEST_DEVICE_NAME,
        )
      )
  }

  @Test
  fun startAssociation_onDiscoveryFailedToStartInvokesOnAssociationStartFailure() {
    val testIdentifier = ParcelUuid(UUID.randomUUID())

    deviceController.startAssociation(
      TEST_DEVICE_NAME,
      mockAssociationCallback,
      testIdentifier.uuid,
    )
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol)
        .startAssociationDiscovery(eq(TEST_DEVICE_NAME), eq(testIdentifier), capture())
      firstValue.onDiscoveryFailedToStart()
    }

    verify(mockAssociationCallback).onAssociationStartFailure()
  }

  @Test
  fun startAssociation_onDiscoveryFailedToStart_callbackBinderDeadDoNotThrow() {
    val testIdentifier = ParcelUuid(UUID.randomUUID())

    deviceController.startAssociation(
      TEST_DEVICE_NAME,
      mockDeadAssociationCallback,
      testIdentifier.uuid,
    )
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol)
        .startAssociationDiscovery(eq(TEST_DEVICE_NAME), eq(testIdentifier), capture())
      firstValue.onDiscoveryFailedToStart()
    }

    verify(mockDeadAssociationCallback, never()).onAssociationStartFailure()
  }

  @Test
  fun initiateConnectionToDevice_invokesStartConnectionDiscovery() {
    val testUuid = ParcelUuid(UUID.randomUUID())

    deviceController.initiateConnectionToDevice(testUuid.uuid)

    verify(testConnectionProtocol).startConnectionDiscovery(eq(testUuid), any(), any())
  }

  @Test
  fun reset_invokesConnectionProtocolReset() {
    deviceController.reset()

    verify(testConnectionProtocol).reset()
  }

  @Test
  fun reset_invokesDisconnectCallbacks() {
    val deviceId = UUID.randomUUID()
    mockStorage.stub {
      onBlocking { getAllAssociatedDevices() } doReturn
        listOf(
          AssociatedDevice(
            deviceId.toString(),
            "address",
            TEST_DEVICE_NAME,
            /* isConnectionEnabled= */ true,
          )
        )
    }
    deviceController =
      MultiProtocolDeviceController(
        context,
        TestLifecycleOwner(),
        protocolDelegate,
        mockStorage,
        mockOobRunner,
        testAssociationServiceUuid.uuid,
        enablePassenger = false,
      )
    deviceController.registerCallback(mockCallback, directExecutor())
    deviceController.start()
    deviceController.initiateConnectionToDevice(deviceId)
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
    }

    deviceController.reset()

    argumentCaptor<ConnectedDevice>().apply {
      verify(mockCallback).onDeviceDisconnected(capture())
      assertThat(firstValue.deviceId).isEqualTo(deviceId.toString())
    }
  }

  @Test
  fun onDeviceConnected_registerDeviceDisconnectedListener() {
    val testUuid = UUID.randomUUID()
    deviceController.initiateConnectionToDevice(testUuid)
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
    }

    assertThat(testConnectionProtocol.deviceDisconnectedListenerList).hasSize(1)
  }

  @Test
  fun onDeviceDisconnected_invokesDisconnectCallback() {
    val deviceId = UUID.randomUUID()
    val testProtocolId = UUID.randomUUID()
    mockStorage.stub {
      onBlocking { getAllAssociatedDevices() } doReturn
        listOf(
          AssociatedDevice(
            deviceId.toString(),
            "address",
            TEST_DEVICE_NAME,
            /* isConnectionEnabled= */ true,
          )
        )
    }
    deviceController.initiateConnectionToDevice(deviceId)
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(testProtocolId.toString())
    }

    val listeners =
      testConnectionProtocol.deviceDisconnectedListenerList[testProtocolId.toString()]
        ?: fail("Failed to find listeners.")
    listeners.invoke { listener -> listener.onDeviceDisconnected(testProtocolId.toString()) }

    verify(mockCallback).onDeviceDisconnected(any())
  }

  @Test
  fun onDeviceDisconnected_duringAssociation_invokesAssociationErrorCallback() {
    val testProtocolId = UUID.randomUUID()
    val testIdentifier = ParcelUuid(UUID.randomUUID())
    deviceController.startAssociation(
      TEST_DEVICE_NAME,
      mockAssociationCallback,
      testIdentifier.uuid,
    )
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(any(), eq(testIdentifier), capture())
      firstValue.onDeviceConnected(testProtocolId.toString())
    }

    val listeners =
      testConnectionProtocol.deviceDisconnectedListenerList[testProtocolId.toString()]
        ?: fail("Failed to find listeners.")
    listeners.invoke { listener -> listener.onDeviceDisconnected(testProtocolId.toString()) }

    verify(mockAssociationCallback).onAssociationError(Errors.DEVICE_ERROR_UNEXPECTED_DISCONNECTION)
    verify(testConnectionProtocol).stopAssociationDiscovery()
  }

  @Test
  fun onDeviceDisconnected_afterAssociationCompleted_doNotInvokesAssociationErrorCallback() {
    val deviceId = UUID.randomUUID()
    val testIdentifier = ParcelUuid(UUID.randomUUID())
    val testProtocolId = UUID.randomUUID()
    val secret = ByteUtils.randomBytes(CHALLENGE_SECRET_BYTES)
    val testDeviceMessage =
      DeviceMessage.createOutgoingMessage(
        null,
        true,
        OperationType.CLIENT_MESSAGE,
        ByteUtils.uuidToBytes(deviceId) + secret,
      )

    deviceController.startAssociation(
      TEST_DEVICE_NAME,
      mockAssociationCallback,
      testIdentifier.uuid,
    )
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(any(), eq(testIdentifier), capture())
      firstValue.onDeviceConnected(testProtocolId.toString())
    }

    deviceController.handleSecureChannelMessage(
      testDeviceMessage,
      deviceController.getConnectedDevice(
        deviceController.associationPendingDeviceId.get() ?: fail("Null device id.")
      ) ?: fail("Failed to find the device."),
    )

    val listeners =
      testConnectionProtocol.deviceDisconnectedListenerList[testProtocolId.toString()]
        ?: fail("Failed to find listeners.")
    listeners.invoke { listener -> listener.onDeviceDisconnected(testProtocolId.toString()) }

    verify(mockAssociationCallback, never())
      .onAssociationError(Errors.DEVICE_ERROR_UNEXPECTED_DISCONNECTION)
  }

  @Test
  fun onDeviceDisconnected_attemptsReconnectIfDeviceIsEnabled() =
    runBlocking<Unit> {
      val deviceId = ParcelUuid(UUID.randomUUID())
      val testProtocolId = UUID.randomUUID()
      val associatedDevice =
        AssociatedDevice(
          deviceId.toString(),
          "address",
          TEST_DEVICE_NAME,
          /* isConnectionEnabled= */ true,
        )
      mockStorage.stub { onBlocking { getAssociatedDevice(any()) } doReturn associatedDevice }
      deviceController.initiateConnectionToDevice(deviceId.uuid)
      argumentCaptor<IDiscoveryCallback>().apply {
        verify(testConnectionProtocol).startConnectionDiscovery(eq(deviceId), any(), capture())
        firstValue.onDeviceConnected(testProtocolId.toString())
      }

      val listeners =
        testConnectionProtocol.deviceDisconnectedListenerList[testProtocolId.toString()]
          ?: fail("Failed to find listeners.")
      listeners.invoke { listener -> listener.onDeviceDisconnected(testProtocolId.toString()) }

      verify(testConnectionProtocol, times(2)).startConnectionDiscovery(eq(deviceId), any(), any())
    }

  @Test
  fun onDeviceDisconnected_doesNotAttemptReconnectForDisabledDevice() =
    runBlocking<Unit> {
      val deviceId = ParcelUuid(UUID.randomUUID())
      val testProtocolId = UUID.randomUUID()

      deviceController.initiateConnectionToDevice(deviceId.uuid)
      argumentCaptor<IDiscoveryCallback>().apply {
        verify(testConnectionProtocol).startConnectionDiscovery(eq(deviceId), any(), capture())
        firstValue.onDeviceConnected(testProtocolId.toString())
      }

      val listeners =
        testConnectionProtocol.deviceDisconnectedListenerList[testProtocolId.toString()]
          ?: fail("Failed to find listeners.")
      listeners.invoke { listener -> listener.onDeviceDisconnected(testProtocolId.toString()) }

      verify(testConnectionProtocol).startConnectionDiscovery(eq(deviceId), any(), any())
    }

  @Test
  fun sendMessage_sendMessageFailsWhenDeviceIsNotReady() {
    val testUuid = UUID.randomUUID()
    val testDeviceMessage =
      DeviceMessage.createOutgoingMessage(
        UUID.randomUUID(),
        true,
        OperationType.CLIENT_MESSAGE,
        "test message".toByteArray(),
      )

    assertThat(deviceController.sendMessage(testUuid, testDeviceMessage)).isFalse()
  }

  @Test
  fun sendMessage_sendsMessageToChannel() {
    val deviceId = UUID.randomUUID()
    val testProtocolId = UUID.randomUUID()
    val testDeviceMessage =
      DeviceMessage.createOutgoingMessage(
        UUID.randomUUID(),
        true,
        OperationType.CLIENT_MESSAGE,
        "test message".toByteArray(),
      )
    deviceController.initiateConnectionToDevice(deviceId)
    argumentCaptor<IDiscoveryCallback>().apply {
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
      DeviceMessage.createOutgoingMessage(
        UUID.randomUUID(),
        true,
        OperationType.CLIENT_MESSAGE,
        "test message".toByteArray(),
      )

    deviceController.initiateConnectionToDevice(testUuid)
    argumentCaptor<IDiscoveryCallback>().apply {
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
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(testProtocolId.toString())
    }

    deviceController.getConnectedDevice(testUuid)?.secureChannel = secureChannel

    assertThat(deviceController.isReadyToSendMessage(testUuid)).isTrue()
  }

  @Test
  fun notifyVerificationCodeAccepted_notifiesChannel() {
    val testIdentifier = ParcelUuid(UUID.randomUUID())

    deviceController.startAssociation(
      TEST_DEVICE_NAME,
      mockAssociationCallback,
      testIdentifier.uuid,
    )
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol)
        .startAssociationDiscovery(eq(TEST_DEVICE_NAME), eq(testIdentifier), capture())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
    }
    val device =
      deviceController.getConnectedDevice(
        deviceController.associationPendingDeviceId.get() ?: fail("Null device id.")
      ) ?: fail("Failed to find the device.")
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
    val testIdentifier = ParcelUuid(UUID.randomUUID())

    deviceController.startAssociation(
      TEST_DEVICE_NAME,
      mockAssociationCallback,
      testIdentifier.uuid,
    )
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol)
        .startAssociationDiscovery(eq(TEST_DEVICE_NAME), eq(testIdentifier), capture())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
    }

    deviceController.notifyVerificationCodeAccepted()
  }

  @Test
  fun handleSecureChannelMessage_firstAssociationMessageSavesIdAndSecretAndIssuesDeviceConnected() =
    runBlocking<Unit> {
      val deviceId = UUID.randomUUID()
      val testIdentifier = ParcelUuid(UUID.randomUUID())
      val secret = ByteUtils.randomBytes(CHALLENGE_SECRET_BYTES)
      val testDeviceMessage =
        DeviceMessage.createOutgoingMessage(
          null,
          true,
          OperationType.CLIENT_MESSAGE,
          ByteUtils.uuidToBytes(deviceId) + secret,
        )

      deviceController.startAssociation(
        TEST_DEVICE_NAME,
        mockAssociationCallback,
        testIdentifier.uuid,
      )
      argumentCaptor<IDiscoveryCallback>().apply {
        verify(testConnectionProtocol)
          .startAssociationDiscovery(eq(TEST_DEVICE_NAME), eq(testIdentifier), capture())
        firstValue.onDeviceConnected(UUID.randomUUID().toString())
      }

      deviceController.handleSecureChannelMessage(
        testDeviceMessage,
        deviceController.getConnectedDevice(
          deviceController.associationPendingDeviceId.get() ?: fail("Null device id.")
        ) ?: fail("Failed to find the device."),
      )

      verify(mockStorage).saveChallengeSecret(deviceId.toString(), secret)
      assertThat(deviceController.getConnectedDevice(deviceId)).isNotNull()
      verify(mockAssociationCallback).onAssociationCompleted()
    }

  @Test
  fun onAssociatedDeviceAdded_issuesDeviceConnectedCallback() =
    runBlocking<Unit> {
      val deviceId = UUID.randomUUID()
      val associatedDevice =
        AssociatedDevice(
          deviceId.toString(),
          "address",
          TEST_DEVICE_NAME,
          /* isConnectionEnabled= */ true,
        )
      argumentCaptor<ConnectedDeviceStorage.AssociatedDeviceCallback>().apply {
        verify(mockStorage).registerAssociatedDeviceCallback(capture())
        firstValue.onAssociatedDeviceAdded(associatedDevice)
      }
      argumentCaptor<ConnectedDevice>().apply {
        verify(mockCallback).onDeviceConnected(capture())
        assertThat(firstValue.deviceId).isEqualTo(associatedDevice.id)
        assertThat(firstValue.hasSecureChannel()).isFalse()
      }
      verify(mockCallback).onSecureChannelEstablished(any())
      verify(mockStorage).getAllAssociatedDevices()
    }

  @Test
  fun handleSecureChannelMessage_associationStorageErrorInvokesOnAssociationErrorCallback() {
    val deviceId = UUID.randomUUID()
    val testIdentifier = ParcelUuid(UUID.randomUUID())
    // Secret is short of a byte.
    // We are mocking the response so it doesn't actually matter.
    val secret = ByteUtils.randomBytes(CHALLENGE_SECRET_BYTES - 1)
    val testDeviceMessage =
      DeviceMessage.createOutgoingMessage(
        null,
        true,
        OperationType.CLIENT_MESSAGE,
        ByteUtils.uuidToBytes(deviceId) + secret,
      )
    mockStorage.stub {
      onBlocking { saveChallengeSecret(any(), any()) } doAnswer
        {
          throw InvalidParameterException()
        }
    }

    deviceController.startAssociation(
      TEST_DEVICE_NAME,
      mockAssociationCallback,
      testIdentifier.uuid,
    )
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol)
        .startAssociationDiscovery(eq(TEST_DEVICE_NAME), eq(testIdentifier), capture())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
    }

    deviceController.handleSecureChannelMessage(
      testDeviceMessage,
      deviceController.getConnectedDevice(
        deviceController.associationPendingDeviceId.get() ?: fail("Null device id.")
      ) ?: fail("Failed to find the device."),
    )

    verify(mockAssociationCallback).onAssociationError(any())
    verify(testConnectionProtocol).disconnectDevice(any())
    verify(mockAssociationCallback, never()).onAssociationCompleted()
  }

  @Test
  fun handleSecureChannelMessage_issuesOnMessageReceived() {
    val deviceId = UUID.randomUUID()
    val testProtocolId = UUID.randomUUID()
    mockStorage.stub {
      onBlocking { getAllAssociatedDevices() } doReturn
        listOf(
          AssociatedDevice(
            deviceId.toString(),
            "address",
            TEST_DEVICE_NAME,
            /* isConnectionEnabled= */ true,
          )
        )
    }
    val testDeviceMessage =
      DeviceMessage.createOutgoingMessage(
        null,
        true,
        OperationType.CLIENT_MESSAGE,
        ByteUtils.randomBytes(10),
      )
    deviceController.initiateConnectionToDevice(deviceId)
    argumentCaptor<IDiscoveryCallback>().apply {
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
  fun handleSecureChannelMessage_firstMessagePersistsDeviceAsDriverWhenPassengerDisabled() =
    runBlocking<Unit> {
      val deviceId = UUID.randomUUID()
      val testIdentifier = ParcelUuid(UUID.randomUUID())
      val secret = ByteUtils.randomBytes(CHALLENGE_SECRET_BYTES)
      val testDeviceMessage =
        DeviceMessage.createOutgoingMessage(
          null,
          true,
          OperationType.CLIENT_MESSAGE,
          ByteUtils.uuidToBytes(deviceId) + secret,
        )
      deviceController =
        MultiProtocolDeviceController(
          context,
          TestLifecycleOwner(),
          protocolDelegate,
          mockStorage,
          mockOobRunner,
          testAssociationServiceUuid.uuid,
          enablePassenger = false,
        )

      deviceController.startAssociation(
        TEST_DEVICE_NAME,
        mockAssociationCallback,
        testIdentifier.uuid,
      )
      argumentCaptor<IDiscoveryCallback>().apply {
        verify(testConnectionProtocol)
          .startAssociationDiscovery(eq(TEST_DEVICE_NAME), eq(testIdentifier), capture())
        firstValue.onDeviceConnected(UUID.randomUUID().toString())
      }

      deviceController.handleSecureChannelMessage(
        testDeviceMessage,
        deviceController.getConnectedDevice(
          deviceController.associationPendingDeviceId.get() ?: fail("Null device id.")
        ) ?: fail("Failed to find the device."),
      )

      argumentCaptor<AssociatedDevice>().apply {
        verify(mockStorage).addAssociatedDeviceForDriver(capture())
        assertThat(firstValue.id).isEqualTo(deviceId.toString())
      }
    }

  @Test
  fun handleSecureChannelMessage_firstMessagePersistsDeviceAsUnclaimedWhenPassengerEnabled() =
    runBlocking<Unit> {
      val deviceId = ParcelUuid(UUID.randomUUID())
      val testIdentifier = ParcelUuid(UUID.randomUUID())
      val secret = ByteUtils.randomBytes(CHALLENGE_SECRET_BYTES)
      val testDeviceMessage =
        DeviceMessage.createOutgoingMessage(
          null,
          true,
          OperationType.CLIENT_MESSAGE,
          ByteUtils.uuidToBytes(deviceId.uuid) + secret,
        )
      deviceController =
        MultiProtocolDeviceController(
          context,
          TestLifecycleOwner(),
          protocolDelegate,
          mockStorage,
          mockOobRunner,
          testAssociationServiceUuid.uuid,
          enablePassenger = true,
        )

      deviceController.startAssociation(
        TEST_DEVICE_NAME,
        mockAssociationCallback,
        testIdentifier.uuid,
      )
      argumentCaptor<IDiscoveryCallback>().apply {
        verify(testConnectionProtocol)
          .startAssociationDiscovery(eq(TEST_DEVICE_NAME), eq(testIdentifier), capture())
        firstValue.onDeviceConnected(UUID.randomUUID().toString())
      }

      deviceController.handleSecureChannelMessage(
        testDeviceMessage,
        deviceController.getConnectedDevice(
          deviceController.associationPendingDeviceId.get() ?: fail("Null device id.")
        ) ?: fail("Failed to find the device."),
      )

      argumentCaptor<AssociatedDevice>().apply {
        verify(mockStorage)
          .addAssociatedDeviceForUser(eq(AssociatedDevice.UNCLAIMED_USER_ID), capture())
        assertThat(firstValue.id).isEqualTo(deviceId.toString())
      }
    }

  @Test
  fun connectedDevices_returnsAllConnectedDevices() {
    val activeUserDeviceId = ParcelUuid(UUID.randomUUID())
    val activeUserDevice =
      AssociatedDevice(
        activeUserDeviceId.toString(),
        "userAddress",
        "userName",
        /* isConnectionEnabled= */ true,
      )
    val otherUserDeviceId = ParcelUuid(UUID.randomUUID())
    val otherUserDevice =
      AssociatedDevice(
        otherUserDeviceId.toString(),
        "otherUserAddress",
        "otherUserName",
        /* isConnectionEnabled= */ true,
      )
    val disconnectedDeviceId = UUID.randomUUID()
    val disconnectedDevice =
      AssociatedDevice(
        disconnectedDeviceId.toString(),
        "otherUserAddress",
        "otherUserName",
        /* isConnectionEnabled= */ true,
      )
    mockStorage.stub {
      onBlocking { getDriverAssociatedDevices() } doReturn listOf(activeUserDevice)
      onBlocking { getAllAssociatedDevices() } doReturn
        listOf(activeUserDevice, otherUserDevice, disconnectedDevice)
    }
    // Recreate controller after registering mock returns since they are used in the constructor.
    deviceController =
      MultiProtocolDeviceController(
        context,
        TestLifecycleOwner(),
        protocolDelegate,
        mockStorage,
        mockOobRunner,
        testAssociationServiceUuid.uuid,
        enablePassenger = false,
      )
    deviceController.registerCallback(mockCallback, directExecutor())
    deviceController.start()

    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol)
        .startConnectionDiscovery(eq(activeUserDeviceId), any(), capture())
      firstValue.onDeviceConnected(activeUserDeviceId.toString())
    }
    deviceController.initiateConnectionToDevice(otherUserDeviceId.uuid)
    argumentCaptor<IDiscoveryCallback>().apply {
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
    assertThat(activeUserConnectedDevice?.deviceName).isEqualTo(activeUserDevice.name)
    assertThat(activeUserConnectedDevice?.isAssociatedWithDriver).isTrue()
    val otherUserConnectedDevice =
      connectedDevices.firstOrNull { it.deviceId == otherUserDeviceId.toString() }
    assertThat(otherUserConnectedDevice).isNotNull()
    assertThat(otherUserConnectedDevice?.deviceName).isEqualTo(otherUserDevice.name)
    assertThat(otherUserConnectedDevice?.isAssociatedWithDriver).isFalse()
  }

  @Test
  fun connectedDevices_returnsEmptyListWithNoConnectedDevices() =
    runBlocking<Unit> {
      val activeUserDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          "userAddress",
          "userName",
          /* isConnectionEnabled= */ true,
        )
      val otherUserDevice =
        AssociatedDevice(
          UUID.randomUUID().toString(),
          "otherUserAddress",
          "otherUserName",
          /* isConnectionEnabled= */ true,
        )
      mockStorage.stub {
        onBlocking { getDriverAssociatedDevices() } doReturn listOf(activeUserDevice)
        onBlocking { getAllAssociatedDevices() } doReturn listOf(activeUserDevice, otherUserDevice)
      }

      val connectedDevices = deviceController.connectedDevices

      assertThat(connectedDevices).isEmpty()
    }

  @Test
  fun connectedDevices_returnsEmptyListWithNoAssociatedDevices() {
    val activeUserDeviceId = ParcelUuid(UUID.randomUUID())
    val otherUserDeviceId = ParcelUuid(UUID.randomUUID())
    mockStorage.stub {
      onBlocking { getDriverAssociatedDevices() } doReturn emptyList()
      onBlocking { getAllAssociatedDevices() } doReturn emptyList()
    }
    deviceController.initiateConnectionToDevice(activeUserDeviceId.uuid)
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol)
        .startConnectionDiscovery(eq(activeUserDeviceId), any(), capture())
      firstValue.onDeviceConnected(activeUserDeviceId.toString())
    }
    deviceController.initiateConnectionToDevice(otherUserDeviceId.uuid)
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol)
        .startConnectionDiscovery(eq(otherUserDeviceId), any(), capture())
      firstValue.onDeviceConnected(otherUserDeviceId.toString())
    }

    val connectedDevices = deviceController.connectedDevices

    assertThat(connectedDevices).isEmpty()
  }

  @Test
  fun disconnectDevice_stopsDiscoveryAndDisconnectsAllProtocolsForDevice() {
    Dispatchers.setMain(testDispatcher)

    val deviceId = ParcelUuid(UUID.randomUUID())
    val testProtocolId1 = UUID.randomUUID().toString()
    val testProtocolId2 = UUID.randomUUID().toString()
    val protocol2 = spy(TestConnectionProtocol())
    protocolDelegate.addProtocol(protocol2)
    deviceController =
      MultiProtocolDeviceController(
          context,
          TestLifecycleOwner(),
          protocolDelegate,
          mockStorage,
          mockOobRunner,
          testAssociationServiceUuid.uuid,
          enablePassenger = false,
        )
        .apply { registerCallback(mockCallback, directExecutor()) }
    deviceController.initiateConnectionToDevice(deviceId.uuid)
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(testProtocolId1)
    }
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(protocol2).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(testProtocolId2)
    }

    deviceController.disconnectDevice(deviceId.uuid)

    testDispatcher.scheduler.advanceUntilIdle()
    verify(testConnectionProtocol).disconnectDevice(testProtocolId1)
    verify(testConnectionProtocol).stopConnectionDiscovery(deviceId)
    verify(protocol2).disconnectDevice(testProtocolId2)
    verify(protocol2).stopConnectionDiscovery(deviceId)

    Dispatchers.resetMain()
  }

  @Test
  fun disconnectDevice_unrecognizedDeviceDoesNotThrow() {
    deviceController.disconnectDevice(UUID.randomUUID())
  }

  @Test
  fun disconectDevice_requestPhoneToDisconnect() {
    startAssociation()
    val device =
      deviceController.getConnectedDevice(
        deviceController.associationPendingDeviceId.get() ?: fail("Null device id.")
      ) ?: fail("Can not find device.")
    device.secureChannel = secureChannel

    deviceController.disconnectDevice(device.deviceId)

    verify(secureChannel).requestDisconnect()
  }

  @Test
  fun disconnectDevice_phoneDidNotDisconnect_initiateLocalDisconnection() {
    Dispatchers.setMain(testDispatcher)

    val deviceId = ParcelUuid(UUID.randomUUID())
    val testProtocolId = UUID.randomUUID().toString()
    val protocol = spy(TestConnectionProtocol())
    protocolDelegate.addProtocol(protocol)
    deviceController =
      MultiProtocolDeviceController(
          context,
          TestLifecycleOwner(),
          protocolDelegate,
          mockStorage,
          mockOobRunner,
          testAssociationServiceUuid.uuid,
          enablePassenger = false,
        )
        .apply { registerCallback(mockCallback, directExecutor()) }
    deviceController.initiateConnectionToDevice(deviceId.uuid)
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(testProtocolId)
    }
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(protocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(testProtocolId)
    }

    deviceController.disconnectDevice(deviceId.uuid)

    testDispatcher.scheduler.advanceUntilIdle()
    verify(testConnectionProtocol).disconnectDevice(testProtocolId)

    Dispatchers.resetMain()
  }

  @Test
  fun disconnectDevice_phoneDisconnects_cancelScheduledDisconnection() {
    Dispatchers.setMain(testDispatcher)

    val deviceId = ParcelUuid(UUID.randomUUID())
    val testProtocolId = UUID.randomUUID().toString()
    deviceController =
      MultiProtocolDeviceController(
          context,
          TestLifecycleOwner(),
          protocolDelegate,
          mockStorage,
          mockOobRunner,
          testAssociationServiceUuid.uuid,
          enablePassenger = false,
        )
        .apply { registerCallback(mockCallback, directExecutor()) }
    deviceController.initiateConnectionToDevice(deviceId.uuid)
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(testProtocolId)
    }

    deviceController.disconnectDevice(deviceId.uuid)

    // Getting a disconnection callback from the protocol indicates the phone has
    // disconnected per our request.
    argumentCaptor<IDeviceDisconnectedListener>().apply {
      verify(testConnectionProtocol).registerDeviceDisconnectedListener(any(), capture())
      firstValue.onDeviceDisconnected(testProtocolId)
    }

    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(deviceController.disconnectRequestedDevices.values.all { it.isCancelled }).isTrue()
    verify(testConnectionProtocol, never()).disconnectDevice(any())

    Dispatchers.resetMain()
  }

  @Test
  fun stopAssociation_disconnectPendingDeviceAndClearOobRunner() {
    val testIdentifier = ParcelUuid(UUID.randomUUID())
    val testProtocolId = UUID.randomUUID().toString()

    deviceController.startAssociation(
      TEST_DEVICE_NAME,
      mockAssociationCallback,
      testIdentifier.uuid,
    )
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol)
        .startAssociationDiscovery(eq(TEST_DEVICE_NAME), eq(testIdentifier), capture())
      firstValue.onDeviceConnected(testProtocolId)
    }
    deviceController.stopAssociation()

    verify(testConnectionProtocol).disconnectDevice(testProtocolId)
    verify(testConnectionProtocol).stopAssociationDiscovery()
    verify(mockOobRunner).reset()
  }

  @Test
  fun stopAssociation_secureChannelIsCanceled() {
    startAssociation()
    val device =
      deviceController.getConnectedDevice(
        deviceController.associationPendingDeviceId.get() ?: fail("Null device id.")
      ) ?: fail("Can not find device.")
    device.secureChannel = secureChannel

    deviceController.stopAssociation()

    verify(secureChannel).cancel()
  }

  @Test
  fun start_initiatesConnectionToAllEnabledActiveUserDevices() {
    val enabledDeviceId = ParcelUuid(UUID.randomUUID())
    val disabledDeviceId = ParcelUuid(UUID.randomUUID())
    val enabledDevice =
      AssociatedDevice(
        enabledDeviceId.toString(),
        /* address= */ "",
        /* name= */ null,
        /* isConnectionEnabled= */ true,
      )
    val disabledDevice =
      AssociatedDevice(
        disabledDeviceId.toString(),
        /* address= */ "",
        /* name= */ null,
        /* isConnectionEnabled= */ false,
      )
    mockStorage.stub {
      onBlocking { getDriverAssociatedDevices() } doReturn listOf(disabledDevice, enabledDevice)
    }

    deviceController.start()

    verify(testConnectionProtocol).startConnectionDiscovery(eq(enabledDeviceId), any(), any())
    verify(testConnectionProtocol, never())
      .startConnectionDiscovery(eq(disabledDeviceId), any(), any())
  }

  @Test
  fun generateSecureChannelCallback_onSecureChannelEstablishedDuringAssociation_sendDeviceId() {
    startAssociation()
    val device =
      deviceController.getConnectedDevice(
        deviceController.associationPendingDeviceId.get() ?: fail("Null device id.")
      ) ?: fail("Can not find device.")
    device.secureChannel = secureChannel

    val callback = deviceController.generateSecureChannelCallback(device)
    callback.onSecureChannelEstablished()

    argumentCaptor<DeviceMessage>().apply {
      verify(secureChannel).sendClientMessage(capture())
      assertThat(firstValue.message).isEqualTo(ByteUtils.uuidToBytes(mockStorage.uniqueId))
    }
    verify(mockCallback).onSecureChannelEstablished(any())
  }

  @Test
  fun generateSecureChannelCallback_failedDuringAssociation_issueErrorCallback() {
    startAssociation()
    val device =
      deviceController.getConnectedDevice(
        deviceController.associationPendingDeviceId.get() ?: fail("Null device id.")
      ) ?: fail("Can not find device.")
    device.secureChannel = secureChannel
    val error = MultiProtocolSecureChannel.ChannelError.CHANNEL_ERROR_INVALID_ENCRYPTION_KEY
    val callback = deviceController.generateSecureChannelCallback(device)
    callback.onEstablishSecureChannelFailure(error)

    verify(mockAssociationCallback).onAssociationError(error.ordinal)
    verify(testConnectionProtocol).disconnectDevice(any())
  }

  @Test
  fun generateSecureChannelCallback_onMessageReceivedErrorDuringAssociation_issueErrorCallback() {
    startAssociation()
    val device =
      deviceController.getConnectedDevice(
        deviceController.associationPendingDeviceId.get() ?: fail("Null device id.")
      ) ?: fail("Can not find device.")
    device.secureChannel = secureChannel
    val callback = deviceController.generateSecureChannelCallback(device)
    callback.onMessageReceivedError(
      MultiProtocolSecureChannel.MessageError.MESSAGE_ERROR_DECRYPTION_FAILURE
    )

    verify(mockAssociationCallback).onAssociationError(Errors.DEVICE_ERROR_INVALID_HANDSHAKE)
    verify(testConnectionProtocol).disconnectDevice(any())
  }

  private fun startAssociation() {
    val testIdentifier = ParcelUuid(UUID.randomUUID())

    deviceController.startAssociation(
      TEST_DEVICE_NAME,
      mockAssociationCallback,
      testIdentifier.uuid,
    )
    argumentCaptor<IDiscoveryCallback>().apply {
      verify(testConnectionProtocol)
        .startAssociationDiscovery(eq(TEST_DEVICE_NAME), eq(testIdentifier), capture())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
    }
  }

  private class Base64CryptoHelper : CryptoHelper {
    override fun encrypt(value: ByteArray?): String? = Base64.encodeToString(value, Base64.DEFAULT)

    override fun decrypt(value: String?): ByteArray? = Base64.decode(value, Base64.DEFAULT)
  }

  open class TestConnectionProtocol : ConnectionProtocol() {
    val deviceDisconnectedListenerList = deviceDisconnectedListeners

    override fun isDeviceVerificationRequired() = false

    override fun startAssociationDiscovery(
      name: String,
      identifier: ParcelUuid,
      callback: IDiscoveryCallback,
    ) {}

    override fun startConnectionDiscovery(
      id: ParcelUuid,
      challenge: ConnectChallenge,
      callback: IDiscoveryCallback,
    ) {}

    override fun stopAssociationDiscovery() {}

    override fun stopConnectionDiscovery(id: ParcelUuid) {}

    override fun sendData(protocolId: String, data: ByteArray, callback: IDataSendCallback?) {}

    override fun disconnectDevice(protocolId: String) {}

    override fun reset() {}

    override fun getMaxWriteSize(protocolId: String): Int {
      return 0
    }

    override fun asBinder(): IBinder {
      val mockBinder = mock<IBinder>()
      whenever(mockBinder.isBinderAlive).thenReturn(true)
      return mockBinder
    }
  }

  companion object {
    private const val TEST_DEVICE_NAME = "TestDevice"
  }
}
