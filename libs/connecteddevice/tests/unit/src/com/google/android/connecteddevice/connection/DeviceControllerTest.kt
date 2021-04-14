package com.google.android.connecteddevice.connection

import androidx.room.Room
import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.storage.ConnectedDeviceDatabase
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.storage.CryptoHelper
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import java.util.UUID
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val DEVICE_NAME = "TestDeviceName"
private const val REMOTE_DEVICE_NAME = "TestRemoteDeviceName"

@RunWith(AndroidJUnit4::class)
class DeviceControllerTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val mockConnectionProtocol: ConnectionProtocol = mock()
  private val mockCallback: DeviceController.Callback = mock()
  private val mockAssociationCallback: AssociationCallback = mock()
  private val protocols = setOf(mockConnectionProtocol)
  private lateinit var deviceController: DeviceController
  private lateinit var testConnectedDevice: DeviceController.ConnectedRemoteDevice
  private val testUuid = UUID.randomUUID()
  private val testRecipientUuid = UUID.randomUUID()
  private val testProtocolId = UUID.randomUUID()
  private val testDeviceMessage =
    DeviceMessage(testRecipientUuid, true, "test message".toByteArray())
  private val mockSecureChannel: SecureChannel = mock()
  private var spyStorage: ConnectedDeviceStorage? = null

  @Before
  fun setUp() {
    val database = Room.inMemoryDatabaseBuilder(
      context,
      ConnectedDeviceDatabase::class.java
    )
      .allowMainThreadQueries()
      .setQueryExecutor(directExecutor())
      .build()
      .associatedDeviceDao()
    val storage = spy(ConnectedDeviceStorage(context, Base64CryptoHelper(), database))
    spyStorage = storage
    deviceController = DeviceController(protocols, storage)
    deviceController.registerCallback(mockCallback, directExecutor())
    testConnectedDevice = DeviceController.ConnectedRemoteDevice(testUuid)
  }

  @Test
  fun startAssociation_startedSuccessfully() {
    deviceController.startAssociation(DEVICE_NAME, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(mockConnectionProtocol).startAssociationDiscovery(eq(DEVICE_NAME), capture())
      firstValue.onDiscoveryStartedSuccessfully()
      verify(mockAssociationCallback).onAssociationStartSuccess(DEVICE_NAME)
    }
  }

  @Test
  fun startAssociation_startedFailed() {
    deviceController.startAssociation(DEVICE_NAME, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(mockConnectionProtocol).startAssociationDiscovery(eq(DEVICE_NAME), capture())
      firstValue.onDiscoveryFailedToStart()
      verify(mockAssociationCallback).onAssociationStartFailure()
    }
  }

  @Test
  fun initiateConnectionToDevice_invokeStartConnectionDiscovery() {
    deviceController.initiateConnectionToDevice(testUuid)
    verify(mockConnectionProtocol).startConnectionDiscovery(eq(testUuid), any())
  }

  @Test
  fun stop_invokeConnectionProtocolReset() {
    deviceController.stop()
    verify(mockConnectionProtocol).reset()
  }

  @Test
  fun sendMessage_sendMessageFailed() {
    assertThat(deviceController.sendMessage(testUuid, testDeviceMessage)).isFalse()
  }

  @Test
  fun sendMessage_sendMessageSucceed() {
    deviceController.initiateConnectionToDevice(testUuid)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(mockConnectionProtocol).startConnectionDiscovery(any(), capture())
      firstValue.onDeviceConnected(testProtocolId.toString())
    }
    assertThat(deviceController.connectedDevices).hasSize(1)
    // TODO(b/180743873) Test the secure channel created inside code ratheer than mock.
    deviceController.connectedDevices.elementAt(0).secureChannel = mockSecureChannel

    assertThat(deviceController.sendMessage(testUuid, testDeviceMessage)).isTrue()
    verify(mockSecureChannel).sendClientMessage(testDeviceMessage)
  }

  @Test
  fun isReadyToSendMessage_returnsFalse() {
    deviceController.initiateConnectionToDevice(testUuid)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(mockConnectionProtocol).startConnectionDiscovery(any(), capture())
      firstValue.onDeviceConnected(testProtocolId.toString())
    }
    assertThat(deviceController.connectedDevices).hasSize(1)

    assertThat(deviceController.sendMessage(testUuid, testDeviceMessage)).isFalse()
  }

  @Test
  fun isReadyToSendMessage_returnsTrue() {
    deviceController.initiateConnectionToDevice(testUuid)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(mockConnectionProtocol).startConnectionDiscovery(any(), capture())
      firstValue.onDeviceConnected(testProtocolId.toString())
    }
    assertThat(deviceController.connectedDevices).hasSize(1)
    deviceController.connectedDevices.elementAt(0).secureChannel = mockSecureChannel

    assertThat(deviceController.sendMessage(testUuid, testDeviceMessage)).isTrue()
  }

  @Test
  fun onDeviceNameRetrieved_storageMethodTriggered() {
    deviceController.startAssociation(DEVICE_NAME, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(mockConnectionProtocol).startAssociationDiscovery(eq(DEVICE_NAME), capture())
      firstValue.onDeviceNameRetrieved(testProtocolId.toString(), REMOTE_DEVICE_NAME)
      verify(spyStorage)?.updateAssociatedDeviceName(any(), eq(REMOTE_DEVICE_NAME))
    }
  }

  private class Base64CryptoHelper : CryptoHelper {
    override fun encrypt(value: ByteArray?): String? = Base64.encodeToString(value, Base64.DEFAULT)

    override fun decrypt(value: String?): ByteArray? = Base64.decode(value, Base64.DEFAULT)
  }
}
