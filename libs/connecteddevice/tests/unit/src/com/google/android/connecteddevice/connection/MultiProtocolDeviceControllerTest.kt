package com.google.android.connecteddevice.connection

import androidx.room.Room
import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.connection.DeviceController.Callback
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.model.DeviceMessage.OperationType
import com.google.android.connecteddevice.storage.ConnectedDeviceDatabase
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.storage.CryptoHelper
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.util.ThreadSafeCallbacks
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val DEVICE_NAME = "TestDeviceName"
private const val REMOTE_DEVICE_NAME = "TestRemoteDeviceName"

@RunWith(AndroidJUnit4::class)
class MultiProtocolDeviceControllerTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val testConnectionProtocol: TestConnectionProtocol = spy(TestConnectionProtocol())
  private val mockCallback: Callback = mock()
  private val mockAssociationCallback: AssociationCallback = mock()
  private val protocols = setOf(testConnectionProtocol)
  private lateinit var deviceController: MultiProtocolDeviceController
  private lateinit var testConnectedDevice: MultiProtocolDeviceController.ConnectedRemoteDevice
  private val testUuid = UUID.randomUUID()
  private val testRecipientUuid = UUID.randomUUID()
  private val testProtocolId = UUID.randomUUID()
  private val testDeviceMessage =
    DeviceMessage(
      testRecipientUuid,
      true,
      OperationType.CLIENT_MESSAGE,
      "test message".toByteArray()
    )
  private val testChallenge = "test Challenge".toByteArray()
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
    whenever(storage.hashWithChallengeSecret(any(), any())).thenReturn(testChallenge)
    spyStorage = storage
    deviceController = MultiProtocolDeviceController(protocols, storage)
    deviceController.registerCallback(mockCallback, directExecutor())
    testConnectedDevice = MultiProtocolDeviceController.ConnectedRemoteDevice()
    deviceController.callbackExecutor = directExecutor()
  }

  @Test
  fun startAssociation_startedSuccessfully() {
    deviceController.startAssociation(DEVICE_NAME, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(eq(DEVICE_NAME), capture())
      firstValue.onDiscoveryStartedSuccessfully()
      verify(mockAssociationCallback).onAssociationStartSuccess(DEVICE_NAME)
    }
  }

  @Test
  fun startAssociation_startedFailed() {
    deviceController.startAssociation(DEVICE_NAME, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(eq(DEVICE_NAME), capture())
      firstValue.onDiscoveryFailedToStart()
      verify(mockAssociationCallback).onAssociationStartFailure()
    }
  }

  @Test
  fun initiateConnectionToDevice_invokeStartConnectionDiscovery() {
    deviceController.initiateConnectionToDevice(testUuid)
    verify(testConnectionProtocol).startConnectionDiscovery(eq(testUuid), any(), any())
  }

  @Test
  fun reset_invokeConnectionProtocolReset() {
    deviceController.reset()
    verify(testConnectionProtocol).reset()
  }

  @Test
  fun onDeviceConnected_registerCallback() {
    deviceController.initiateConnectionToDevice(testUuid)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(testProtocolId.toString())
    }
    assertThat(testConnectionProtocol.getCallbackList()).hasSize(1)
  }

  @Test
  fun onDeviceDisconnected_informCallback() {
    deviceController.initiateConnectionToDevice(testUuid)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(testProtocolId.toString())
    }
    val callbacks = testConnectionProtocol.getCallbackList().get(testProtocolId.toString())
    callbacks!!.invoke { callback -> callback.onDeviceDisconnected(testProtocolId.toString()) }
    verify(mockCallback).onDeviceDisconnected(any())
  }

  @Test
  fun sendMessage_sendMessageFailed() {
    assertThat(deviceController.sendMessage(testUuid, testDeviceMessage)).isFalse()
  }

  @Test
  fun sendMessage_sendMessageSucceed() {
    deviceController.initiateConnectionToDevice(testUuid)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(any(), any(), capture())
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
      verify(testConnectionProtocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(testProtocolId.toString())
    }
    assertThat(deviceController.connectedDevices).hasSize(1)

    assertThat(deviceController.sendMessage(testUuid, testDeviceMessage)).isFalse()
  }

  @Test
  fun isReadyToSendMessage_returnsTrue() {
    deviceController.initiateConnectionToDevice(testUuid)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(any(), any(), capture())
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
      verify(testConnectionProtocol).startAssociationDiscovery(eq(DEVICE_NAME), capture())
      firstValue.onDeviceNameRetrieved(testProtocolId.toString(), REMOTE_DEVICE_NAME)
      verify(spyStorage)?.setAssociatedDeviceName(any(), eq(REMOTE_DEVICE_NAME))
    }
  }

  private class Base64CryptoHelper : CryptoHelper {
    override fun encrypt(value: ByteArray?): String? = Base64.encodeToString(value, Base64.DEFAULT)

    override fun decrypt(value: String?): ByteArray? = Base64.decode(value, Base64.DEFAULT)
  }

  open class TestConnectionProtocol : ConnectionProtocol() {
    override fun startAssociationDiscovery(name: String, callback: DiscoveryCallback) {
      // No implementation
    }

    override fun startConnectionDiscovery(
      id: UUID,
      challenge: ConnectChallenge,
      callback: DiscoveryCallback
    ) {
      // No implementation
    }

    override fun stopAssociationDiscovery() {
      // No implementation
    }

    override fun stopConnectionDiscovery(id: UUID) {
      // No implementation
    }

    override fun sendData(protocolId: String, data: ByteArray, callback: DataSendCallback?) {
      // No implementation
    }

    override fun disconnectDevice(protocolId: String) {
      // No implementation
    }

    override fun reset() {
      // No implementation
    }

    override fun getMaxWriteSize(protocolId: String): Int {
      // No implementation
      return 0
    }

    fun getCallbackList(): ConcurrentHashMap<String, ThreadSafeCallbacks<DeviceCallback>> {
      return deviceCallbacks
    }
  }
}
