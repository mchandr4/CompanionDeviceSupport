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
import com.google.android.connecteddevice.util.ByteUtils
import com.google.android.connecteddevice.util.ThreadSafeCallbacks
import com.google.android.encryptionrunner.EncryptionRunnerFactory
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.security.InvalidParameterException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
  private val mockAssociationCallback: AssociationCallback = mock()
  private val protocols = setOf(testConnectionProtocol)
  private lateinit var deviceController: MultiProtocolDeviceController
  private lateinit var testConnectedDevice: MultiProtocolDeviceController.ConnectedRemoteDevice
  private lateinit var secureChannel: MultiProtocolSecureChannel
  private lateinit var spyStorage: ConnectedDeviceStorage

  @Before
  fun setUp() {
    val database =
      Room.inMemoryDatabaseBuilder(context, ConnectedDeviceDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(directExecutor())
        .build()
        .associatedDeviceDao()
    spyStorage = spy(ConnectedDeviceStorage(context, Base64CryptoHelper(), database))
    whenever(spyStorage.hashWithChallengeSecret(any(), any())).thenReturn(TEST_CHALLENGE)
    deviceController = MultiProtocolDeviceController(protocols, spyStorage)
    deviceController.registerCallback(mockCallback, directExecutor())
    testConnectedDevice = MultiProtocolDeviceController.ConnectedRemoteDevice()
    deviceController.callbackExecutor = directExecutor()
    secureChannel =
      MultiProtocolSecureChannel(mockStream, spyStorage, EncryptionRunnerFactory.newFakeRunner())
  }

  @Test
  fun startAssociation_startedSuccessfully() {
    val deviceName = "TestDeviceName"
    deviceController.startAssociation(deviceName, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(eq(deviceName), capture())
      firstValue.onDiscoveryStartedSuccessfully()
      verify(mockAssociationCallback).onAssociationStartSuccess(deviceName)
    }
  }

  @Test
  fun startAssociation_startedFailed() {
    val deviceName = "TestDeviceName"
    deviceController.startAssociation(deviceName, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(eq(deviceName), capture())
      firstValue.onDiscoveryFailedToStart()
      verify(mockAssociationCallback).onAssociationStartFailure()
    }
  }

  @Test
  fun initiateConnectionToDevice_invokeStartConnectionDiscovery() {
    val testUuid = UUID.randomUUID()
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
    val testUuid = UUID.randomUUID()
    deviceController.initiateConnectionToDevice(testUuid)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startConnectionDiscovery(any(), any(), capture())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
    }
    assertThat(testConnectionProtocol.getCallbackList()).hasSize(1)
  }

  @Test
  fun onSecondProtocolConnected_addToCurrentDevice() {
    val deviceName = "TestDeviceName"
    deviceController.startAssociation(deviceName, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(eq(deviceName), capture())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
    }

    assertThat(deviceController.connectedDevices).hasSize(1)
  }

  @Test
  fun onDeviceDisconnected_informCallback() {
    val testUuid = UUID.randomUUID()
    val testProtocolId = UUID.randomUUID()
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
  fun sendMessage_noConnectedDevice_sendMessageFailed() {
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
  fun sendMessage_sendMessageSucceed() {
    val testUuid = UUID.randomUUID()
    val testProtocolId = UUID.randomUUID()
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
      firstValue.onDeviceConnected(testProtocolId.toString())
    }

    assertThat(deviceController.connectedDevices).hasSize(1)

    // TODO(b/180743873) Test the secure channel created inside code ratheer than mock.
    deviceController.connectedDevices.elementAt(0).secureChannel = secureChannel

    assertThat(deviceController.sendMessage(testUuid, testDeviceMessage)).isTrue()
  }

  @Test
  fun isReadyToSendMessage_noSecureChannelEstablished_returnsFalse() {
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
    assertThat(deviceController.connectedDevices).hasSize(1)

    assertThat(deviceController.sendMessage(testUuid, testDeviceMessage)).isFalse()
  }

  @Test
  fun isReadyToSendMessage_returnsTrue() {
    val testUuid = UUID.randomUUID()
    val testProtocolId = UUID.randomUUID()
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
      firstValue.onDeviceConnected(testProtocolId.toString())
    }
    assertThat(deviceController.connectedDevices).hasSize(1)
    deviceController.connectedDevices.elementAt(0).secureChannel = secureChannel

    assertThat(deviceController.sendMessage(testUuid, testDeviceMessage)).isTrue()
  }

  @Test
  fun onDeviceNameRetrieved_storageMethodTriggered() {
    val deviceName = "TestDeviceName"
    val remoteDeviceName = "TestRemoteDeviceName"
    deviceController.startAssociation(deviceName, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(eq(deviceName), capture())
      firstValue.onDeviceNameRetrieved(UUID.randomUUID().toString(), remoteDeviceName)
      verify(spyStorage).setAssociatedDeviceName(any(), eq(remoteDeviceName))
    }
  }

  @Test
  fun notifyVerificationCodeAccepted_sendDeviceId() {
    val deviceName = "TestDeviceName"
    deviceController.startAssociation(deviceName, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(eq(deviceName), capture())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
    }
    assertThat(deviceController.connectedDevices).hasSize(1)
    deviceController.connectedDevices.first().secureChannel = secureChannel

    deviceController.notifyVerificationCodeAccepted()
    verify(spyStorage).getUniqueId()
  }

  @Test
  fun handleSecureChannelMessage_storageException_issueSecureChannelError() {
    val deviceName = "TestDeviceName"
    val testDeviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        true,
        OperationType.CLIENT_MESSAGE,
        ByteUtils.randomBytes(50)
      )
    whenever(spyStorage.hashWithChallengeSecret(any(), any())).then {
      throw InvalidParameterException()
    }
    deviceController.startAssociation(deviceName, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(eq(deviceName), capture())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
    }
    deviceController.handleSecureChannelMessage(
      testDeviceMessage,
      deviceController.connectedDevices.first()
    )

    verify(mockCallback).onSecureChannelError(any())
  }

  @Test
  fun handleSecureChannelMessage_associationNotCompleted_issueOnDeviceConnected() {
    val deviceName = "TestDeviceName"
    val validMessageLength =
      MultiProtocolDeviceController.DEVICE_ID_BYTES + ConnectedDeviceStorage.CHALLENGE_SECRET_BYTES
    val testDeviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        true,
        OperationType.CLIENT_MESSAGE,
        ByteUtils.randomBytes(validMessageLength)
      )
    deviceController.startAssociation(deviceName, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(eq(deviceName), capture())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
    }
    deviceController.handleSecureChannelMessage(
      testDeviceMessage,
      deviceController.connectedDevices.first()
    )
    argumentCaptor<String>().apply {
      verify(spyStorage).saveChallengeSecret(capture(), any())
      verify(mockCallback).onDeviceConnected(firstValue)
    }
  }

  @Test
  fun handleSecureChannelMessage_associationCompleted_issueOnMessageReceived() {
    val deviceName = "TestDeviceName"
    val validMessageLength =
      MultiProtocolDeviceController.DEVICE_ID_BYTES + ConnectedDeviceStorage.CHALLENGE_SECRET_BYTES
    val testDeviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        true,
        OperationType.CLIENT_MESSAGE,
        ByteUtils.randomBytes(validMessageLength)
      )
    deviceController.startAssociation(deviceName, mockAssociationCallback)
    argumentCaptor<ConnectionProtocol.DiscoveryCallback>().apply {
      verify(testConnectionProtocol).startAssociationDiscovery(eq(deviceName), capture())
      firstValue.onDeviceConnected(UUID.randomUUID().toString())
    }

    val connectedDevice = deviceController.connectedDevices.first()
    deviceController.handleSecureChannelMessage(testDeviceMessage, connectedDevice)
    deviceController.handleSecureChannelMessage(testDeviceMessage, connectedDevice)
    verify(mockCallback).onMessageReceived(any(), any())
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
