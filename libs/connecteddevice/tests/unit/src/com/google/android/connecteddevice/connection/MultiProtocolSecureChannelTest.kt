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
package com.google.android.connecteddevice.connection

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.connection.MultiProtocolSecureChannel.ChannelError
import com.google.android.connecteddevice.connection.MultiProtocolSecureChannel.MessageError
import com.google.android.connecteddevice.connection.MultiProtocolSecureChannel.ShowVerificationCodeListener
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.model.DeviceMessage.OperationType
import com.google.android.connecteddevice.storage.ConnectedDeviceDatabase
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.storage.CryptoHelper
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.ProtocolDevice
import com.google.android.connecteddevice.util.ByteUtils
import com.google.android.encryptionrunner.EncryptionRunnerFactory
import com.google.android.encryptionrunner.FakeEncryptionRunner
import com.google.android.encryptionrunner.HandshakeException
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.nio.charset.StandardCharsets.UTF_8
import java.security.SignatureException
import java.util.UUID
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val PROTOCOL_ID_1 = "testProtocol1"
private const val PROTOCOL_ID_2 = "testProtocol1"
private val SERVER_DEVICE_ID = UUID.fromString("a29f0c74-2014-4b14-ac02-be6ed15b545a")

@RunWith(AndroidJUnit4::class)
class MultiProtocolSecureChannelTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val stream1 =
    spy(ProtocolStream(ProtocolDevice(TestProtocol(), PROTOCOL_ID_1), directExecutor()))
  private val stream2 =
    spy(ProtocolStream(ProtocolDevice(TestProtocol(), PROTOCOL_ID_2), directExecutor()))
  private val mockInflater: Inflater = mock()
  private val mockCallback: MultiProtocolSecureChannel.Callback = mock()

  private lateinit var secureChannel: MultiProtocolSecureChannel
  private lateinit var spyStorage: ConnectedDeviceStorage
  private val mockShowVerificationCodeListener: ShowVerificationCodeListener = mock()

  @Before
  @Throws(SignatureException::class)
  fun setUp() {
    val database =
      Room.inMemoryDatabaseBuilder(context, ConnectedDeviceDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(directExecutor())
        .build()
        .associatedDeviceDao()
    spyStorage = spy(ConnectedDeviceStorage(context, Base64CryptoHelper(), database))
    whenever(spyStorage.uniqueId).thenReturn(SERVER_DEVICE_ID)
  }

  @Test
  @Throws(SignatureException::class)
  fun decryptMessage_doesNothingForUnencryptedMessage() {
    val testPayload = ByteUtils.randomBytes(10)
    completeHandshakeAndSaveTheKey()

    val message =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ false,
        OperationType.CLIENT_MESSAGE,
        testPayload
      )
    secureChannel.decryptMessage(message)
    assertThat(message.message).isEqualTo(testPayload)
  }

  @Test
  @Throws(SignatureException::class)
  fun decryptMessage_decryptsEncryptedMessage() {
    completeHandshakeAndSaveTheKey()

    val message =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )
    assertThat(secureChannel.decryptMessage(message)).isTrue()
  }

  @Test
  @Throws(InterruptedException::class)
  fun decryptMessage_onMessageReceivedErrorForEncryptedMessageWithNoKey() {
    setupSecureChannel(true)
    val message =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )
    assertThat(secureChannel.decryptMessage(message)).isFalse()
    verify(mockCallback).onMessageReceivedError(MessageError.MESSAGE_ERROR_DECRYPTION_FAILURE)
    assertThat(message.message).isEmpty()
  }

  @Test
  fun onDeviceMessageReceived_onEstablishSecureChannelFailureBadHandshakeMessage() {
    setupSecureChannel(true)
    val message =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.ENCRYPTION_HANDSHAKE,
        ByteUtils.randomBytes(10)
      )
    secureChannel.onDeviceMessageReceived(message)
    verify(mockCallback).onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_MSG)
  }

  @Test
  fun onDeviceMessageReceived_onMessageReceivedNotIssuedForEmptyMessage() {
    completeHandshakeAndSaveTheKey()

    val message =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ false,
        OperationType.CLIENT_MESSAGE,
        /* message= */ byteArrayOf()
      )
    secureChannel.onDeviceMessageReceived(message)
    verify(mockCallback, never()).onMessageReceived(any())
  }

  @Test
  fun onDeviceMessageReceived_processHandshakeExceptionIssuesSecureChannelFailureCallback() {
    setupSecureChannel(true)
    val message =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ false,
        OperationType.ENCRYPTION_HANDSHAKE,
        FakeEncryptionRunner.INIT_MESSAGE
      )

    whenever(secureChannel.processHandshake(message.message)).then {
      throw HandshakeException("test")
    }
    secureChannel.onDeviceMessageReceived(message)
    verify(mockCallback)
      .onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_HANDSHAKE)
  }

  @Test
  fun onDeviceMessageReceived_processClientMessageIssuesMessageReceivedErrorCallback() {
    setupSecureChannel(true)
    val message =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )
    secureChannel.onDeviceMessageReceived(message)
    verify(mockCallback).onMessageReceivedError(MessageError.MESSAGE_ERROR_DECRYPTION_FAILURE)
  }

  @Test
  fun onDeviceMessageReceived_noExceptionWhenReceivedUnknowMessageType() {
    completeHandshakeAndSaveTheKey()
    val message =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.UNKNOWN,
        ByteUtils.randomBytes(10)
      )
    try {
      secureChannel.onDeviceMessageReceived(message)
    } catch (e: Exception) {
      fail("Should not throw exception.")
    }
  }

  @Test
  fun onDeviceMessageReceived_issueOnMessageReceivedWhenHandshakeFinished() {
    completeHandshakeAndSaveTheKey()
    val message =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.ENCRYPTION_HANDSHAKE,
        ByteUtils.randomBytes(10)
      )
    secureChannel.onDeviceMessageReceived(message)
    verify(mockCallback).onMessageReceived(message)
  }

  @Test
  fun decompressMessage_returnsOriginalMessageIfOriginalSizeIsZero() {
    val testPayload = ByteUtils.randomBytes(10)
    completeHandshakeAndSaveTheKey()

    val deviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ false,
        OperationType.CLIENT_MESSAGE,
        testPayload
      )
    deviceMessage.originalMessageSize = 0
    assertThat(secureChannel.decompressMessage(deviceMessage)).isTrue()
    assertThat(deviceMessage.message).isEqualTo(testPayload)
  }

  @Test
  fun decompressMessage_returnsFlaseWhenThereAreDataFormatException() {
    completeHandshakeAndSaveTheKey()
    whenever(mockInflater.inflate(any())).then { throw DataFormatException() }
    val deviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ false,
        OperationType.CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )
    secureChannel.callback = mockCallback
    deviceMessage.originalMessageSize = 1
    assertThat(secureChannel.decompressMessage(deviceMessage)).isFalse()
    verify(mockCallback).onMessageReceivedError(MessageError.MESSAGE_ERROR_DECOMPRESSION_FAILURE)
  }

  @Test
  fun compressMessage_returnsCompressedMessageWithOriginalSize() {
    completeHandshakeAndSaveTheKey()

    val message = ByteArray(100)
    val deviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ false,
        OperationType.CLIENT_MESSAGE,
        message
      )
    secureChannel.compressMessage(deviceMessage)
    assertThat(deviceMessage.message).isNotEqualTo(message)
    assertThat(deviceMessage.originalMessageSize).isEqualTo(message.size)
  }

  @Test
  fun compressedMessageCanBeDecompressed() {
    completeHandshakeAndSaveTheKey()

    val message = ByteArray(100)
    val deviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ false,
        OperationType.CLIENT_MESSAGE,
        message
      )
    secureChannel.compressMessage(deviceMessage)
    assertThat(secureChannel.decompressMessage(deviceMessage)).isTrue()
    assertThat(deviceMessage.message).isEqualTo(message)
  }

  @Test
  fun addStream_notifyCallbackWhenMessageReceived() {
    completeHandshakeAndSaveTheKey()

    val deviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )
    secureChannel.addStream(stream2)
    assertThat(stream2.messageReceivedListener).isNotNull()
    stream2.messageReceivedListener!!.onMessageReceived(deviceMessage)
    verify(mockCallback).onMessageReceived(any())
  }

  @Test
  fun addStream_notifyCallbackWhenProtocolDisconnected() {
    setupSecureChannel(true)

    assertThat(stream1.protocolDisconnectListener).isNotNull()
    stream1.protocolDisconnectListener!!.onProtocolDisconnected()
    verify(mockCallback)
      .onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_DEVICE_DISCONNECTED)
  }

  @Test
  fun addStream_doNotNotifyCallbackWhenProtocolDisconnected() {
    setupSecureChannel(true)
    secureChannel.addStream(stream2)
    assertThat(stream2.protocolDisconnectListener).isNotNull()
    stream2.protocolDisconnectListener!!.onProtocolDisconnected()
    verify(mockCallback, never())
      .onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_DEVICE_DISCONNECTED)
  }

  @Test
  fun sendClientMessage_FailedToSendMessageWithNullKey() {
    setupSecureChannel(true)
    val deviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )

    assertThat(secureChannel.sendClientMessage(deviceMessage)).isFalse()
  }

  @Test
  fun sendClientMessage_FailedToSendMessageWithEmptyStream() {
    val deviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )
    completeHandshakeAndSaveTheKey()

    stream1.protocolDisconnectListener!!.onProtocolDisconnected()
    assertThat(secureChannel.sendClientMessage(deviceMessage)).isFalse()
  }

  @Test
  fun sendClientMessage_successfullySendMessage() {
    val deviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )

    completeHandshakeAndSaveTheKey()

    assertThat(secureChannel.sendClientMessage(deviceMessage)).isTrue()
  }

  @Test
  fun sendClientMessage_successfullyEncryptAndSetMessage() {
    val testPayload = ByteUtils.randomBytes(10)
    val deviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.CLIENT_MESSAGE,
        testPayload
      )

    completeHandshakeAndSaveTheKey()
    secureChannel.sendClientMessage(deviceMessage)

    assertThat(deviceMessage.message).isNotEqualTo(testPayload)
  }

  @Test
  fun association_secureChannelEstablishedSuccessfully() {
    val clientId = UUID.randomUUID()
    setupSecureChannel(false)
    secureChannel.showVerificationCodeListener = mockShowVerificationCodeListener
    argumentCaptor<DeviceMessage>().apply {
      initHandshakeMessage()
      verify(stream1).sendMessage(capture())
      val response = firstValue.message
      assertThat(response).isEqualTo(FakeEncryptionRunner.INIT_RESPONSE)
    }
    respondToContinueMessage()
    verify(mockShowVerificationCodeListener).showVerificationCode(any())

    secureChannel.notifyVerificationCodeAccepted()
    secureChannel.setDeviceIdDuringAssociation(clientId)
    verify(spyStorage).saveEncryptionKey(eq(clientId.toString()), any())
    verify(mockCallback).onSecureChannelEstablished()
  }

  @Test
  fun association_wrongInitHandshakeMessage_issueInvalidHandshakeError() {
    setupSecureChannel(false)

    // Wrong init handshake message
    respondToContinueMessage()
    verify(mockCallback)
      .onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_HANDSHAKE)
  }

  @Test
  @Throws(InterruptedException::class)
  fun association_wrongRespondToContinueMessage_issueInvalidHandshakeError() {
    setupSecureChannel(false)
    initHandshakeMessage()

    // Wrong respond to continue message
    initHandshakeMessage()
    verify(mockCallback)
      .onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_HANDSHAKE)
  }

  @Test
  fun reconnect_secureChannelEstablishedSuccessfully() {
    val clientId = UUID.randomUUID()
    whenever(spyStorage.getEncryptionKey(clientId.toString())).thenReturn(byteArrayOf())
    setupSecureChannel(true, clientId.toString())
    initHandshakeMessage()
    respondToContinueMessage()
    respondToResumeMessage()

    verify(spyStorage).saveEncryptionKey(eq(clientId.toString()), any())
    verify(mockCallback).onSecureChannelEstablished()
  }

  @Test
  fun reconnect_deviceIdNotSet_issueInvalidStateError() {
    setupSecureChannel(true)

    initHandshakeMessage()
    respondToContinueMessage()
    respondToResumeMessage()

    verify(mockCallback).onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_STATE)
  }

  @Test
  fun reconnect_keyNotSet_issueInvalidKeyError() {
    setupSecureChannel(true, UUID.randomUUID().toString())
    initHandshakeMessage()
    respondToContinueMessage()
    respondToResumeMessage()

    verify(mockCallback)
      .onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_ENCRYPTION_KEY)
  }

  @Test
  fun processHandshakeResumingSession_incorrectHandshakeState_issueInvalidStateError() {
    val clientId = UUID.randomUUID().toString()
    setupSecureChannel(true, clientId)
    whenever(spyStorage.getEncryptionKey(clientId)).thenReturn(byteArrayOf())
    initHandshakeMessage()
    respondToContinueMessage()
    respondToResumeMessage(FakeEncryptionRunner.RECONNECTION_MESSAGE_STATE_ERROR)

    verify(mockCallback).onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_STATE)
  }

  @Test
  fun processHandshakeResumingSession_emptyNewKey_issueInvalidKeyError() {
    val clientId = UUID.randomUUID().toString()
    setupSecureChannel(true, clientId)
    whenever(spyStorage.getEncryptionKey(clientId)).thenReturn(byteArrayOf())
    initHandshakeMessage()
    respondToContinueMessage()
    respondToResumeMessage(FakeEncryptionRunner.RECONNECTION_MESSAGE_KEY_ERROR)

    verify(mockCallback)
      .onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_ENCRYPTION_KEY)
  }

  @Test
  fun processHandshakeResumingSession_emptyNextMessage_issueInvalidMessageError() {
    val clientId = UUID.randomUUID().toString()
    setupSecureChannel(true, clientId)
    whenever(spyStorage.getEncryptionKey(clientId)).thenReturn(byteArrayOf())
    initHandshakeMessage()
    respondToContinueMessage()
    respondToResumeMessage(FakeEncryptionRunner.RECONNECTION_MESSAGE_EMPTY_RESPONSE)

    verify(mockCallback).onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_MSG)
  }

  @Test
  fun processHandshake_invalidState_issueInvalidStateError() {
    setupSecureChannel(false)
    secureChannel.showVerificationCodeListener = mockShowVerificationCodeListener
    initHandshakeMessage()
    respondToContinueMessage()
    secureChannel.processHandshake(byteArrayOf())

    verify(mockCallback).onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_STATE)
  }

  @Test
  fun processHandshake_receivedMessageAfterFinish_ignoreMessage() {
    setupSecureChannel(false)
    secureChannel.showVerificationCodeListener = mockShowVerificationCodeListener
    initHandshakeMessage()
    respondToContinueMessage()
    secureChannel.notifyVerificationCodeAccepted()
    secureChannel.processHandshake(byteArrayOf())

    verify(mockCallback, never()).onEstablishSecureChannelFailure(any())
  }

  @Test
  fun processHandshakeInitialization_nextMessageIsNull_issueInvalidHandshakeError() {
    setupSecureChannel(false)
    initHandshakeMessage(FakeEncryptionRunner.INIT_MESSAGE_EMPTY_RESPONSE)

    verify(mockCallback)
      .onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_HANDSHAKE)
  }

  @Test
  fun processVerificationCode_listenerNotSet_issueInvalidStateError() {
    setupSecureChannel(false)
    initHandshakeMessage()
    respondToContinueMessage()
    verify(mockCallback).onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_STATE)
  }

  @Test
  fun setDeviceIdDuringAssociation_encryptionKeyNotSet_issueInvalidKeyError() {
    setupSecureChannel(false)
    secureChannel.setDeviceIdDuringAssociation(UUID.randomUUID())

    verify(mockCallback)
      .onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_ENCRYPTION_KEY)
  }

  @Test
  fun notifyVerificationCodeAccepted_invalidState_issueInvalidVerificationError() {
    setupSecureChannel(false)
    initHandshakeMessage()
    secureChannel.notifyVerificationCodeAccepted()

    verify(mockCallback)
      .onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_VERIFICATION)
  }

  @Test
  fun processHandshakeInProgress_oobVerificationNeeded_notifyVerificationCodeAvailableCallback() {
    setupOobSecureChannel()
    initHandshakeMessage()
    respondToContinueMessage()

    verify(mockCallback)
      .onOobVerificationCodeAvailable(FakeEncryptionRunner.VERIFICATION_CODE.toByteArray(UTF_8))
  }

  @Test
  fun processHandshake_receivedOobVerificationCode_notifyVerificationCodeReceivedCallback() {
    setupOobSecureChannel()
    initHandshakeMessage()
    respondToContinueMessage()
    respondToOobCode()

    verify(mockCallback)
      .onOobVerificationCodeReceived(FakeEncryptionRunner.VERIFICATION_CODE.toByteArray(UTF_8))
  }

  @Test
  fun sendOobEncryptedCode_sendHandshakeMessage() {
    val testEncryptedCode = "VerificationCode".toByteArray()
    setupOobSecureChannel()

    argumentCaptor<DeviceMessage>().apply {
      secureChannel.sendOobEncryptedCode(testEncryptedCode)
      verify(stream1).sendMessage(capture())
      val codeMessage = firstValue.message
      assertThat(codeMessage).isEqualTo(testEncryptedCode)
    }
  }

  private fun setupSecureChannel(isReconnect: Boolean, deviceId: String? = null) {
    val encryptionRunner = EncryptionRunnerFactory.newFakeRunner()
    encryptionRunner.setIsReconnect(isReconnect)
    secureChannel =
      spy(
        MultiProtocolSecureChannel(
            stream1,
            spyStorage,
            encryptionRunner,
            deviceId,
            inflater = mockInflater
          )
          .apply { callback = mockCallback }
      )
  }

  private fun setupOobSecureChannel() {
    val oobEncryptionRunner = EncryptionRunnerFactory.newOobFakeRunner()
    secureChannel =
      spy(
        MultiProtocolSecureChannel(
            stream1,
            spyStorage,
            oobEncryptionRunner,
            deviceId = null,
            inflater = mockInflater
          )
          .apply { callback = mockCallback }
      )
  }
  private fun initHandshakeMessage(message: ByteArray = FakeEncryptionRunner.INIT_MESSAGE) {
    val deviceMessage =
      DeviceMessage(
        /* recipient= */ null,
        /* isMessageEncrypted= */ false,
        OperationType.ENCRYPTION_HANDSHAKE,
        message
      )
    secureChannel.onDeviceMessageReceived(deviceMessage)
  }

  private fun respondToContinueMessage(message: ByteArray = FakeEncryptionRunner.CLIENT_RESPONSE) {
    val deviceMessage =
      DeviceMessage(
        /* recipient= */ null,
        /* isMessageEncrypted= */ false,
        OperationType.ENCRYPTION_HANDSHAKE,
        message
      )
    secureChannel.onDeviceMessageReceived(deviceMessage)
  }

  private fun respondToOobCode() {
    val message =
      DeviceMessage(
        /* recipient= */ null,
        /* isMessageEncrypted= */ false,
        OperationType.ENCRYPTION_HANDSHAKE,
        FakeEncryptionRunner.VERIFICATION_CODE.toByteArray(UTF_8)
      )
    secureChannel.onDeviceMessageReceived(message)
  }

  private fun respondToResumeMessage(message: ByteArray = "Placeholder Message".toByteArray()) {
    val deviceMessage =
      DeviceMessage(
        /* recipient= */ null,
        /* isMessageEncrypted= */ false,
        OperationType.ENCRYPTION_HANDSHAKE,
        message
      )
    secureChannel.onDeviceMessageReceived(deviceMessage)
  }

  private fun completeHandshakeAndSaveTheKey() {
    setupSecureChannel(false)
    secureChannel.showVerificationCodeListener = mockShowVerificationCodeListener
    initHandshakeMessage()
    respondToContinueMessage()
    secureChannel.notifyVerificationCodeAccepted()
  }
}

open class TestProtocol : ConnectionProtocol() {
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

private class Base64CryptoHelper : CryptoHelper {
  override fun encrypt(value: ByteArray?): String? = Base64.encodeToString(value, Base64.DEFAULT)

  override fun decrypt(value: String?): ByteArray? = Base64.decode(value, Base64.DEFAULT)
}
