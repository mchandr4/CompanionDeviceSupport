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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.connection.MultiProtocolSecureChannel.ChannelError
import com.google.android.connecteddevice.connection.MultiProtocolSecureChannel.MessageError
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.model.DeviceMessage.OperationType
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.util.ByteUtils
import com.google.android.encryptionrunner.EncryptionRunnerFactory
import com.google.android.encryptionrunner.HandshakeException
import com.google.android.encryptionrunner.Key
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.security.SignatureException
import java.util.UUID
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private val TEST_PAYLOAD = ByteUtils.randomBytes(10)
private const val PROTOCOL_ID = "testDevice"

@RunWith(AndroidJUnit4::class)
class MultiProtocolSecureChannelTest {
  private val secondaryStream = spy(ProtocolStream(TestProtocol(), PROTOCOL_ID, directExecutor()))
  private val mockStream: ProtocolStream = mock()
  private val mockInflater: Inflater = mock()
  private val mockCallback: MultiProtocolSecureChannel.Callback = mock()

  private lateinit var fakeKey: Key

  private lateinit var secureChannel: MultiProtocolSecureChannel

  @Before
  @Throws(SignatureException::class)
  fun setUp() {
    secureChannel =
      object : MultiProtocolSecureChannel(mockStream, EncryptionRunnerFactory.newFakeRunner()) {
        override fun processHandshake(message: ByteArray) {}
      }

    fakeKey = spy(FakeKey())
    secureChannel.setEncryptionKey(fakeKey)
    secureChannel.callback = mockCallback
  }

  @Test
  @Throws(SignatureException::class)
  fun decryptMessage_doesNothingForUnencryptedMessage() {
    val message =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ false,
        OperationType.CLIENT_MESSAGE,
        TEST_PAYLOAD
      )
    secureChannel.decryptMessage(message)
    assertThat(message.message).isEqualTo(TEST_PAYLOAD)
    verify(fakeKey, never()).decryptData(any())
  }

  @Test
  @Throws(SignatureException::class)
  fun decryptMessage_decryptsEncryptedMessage() {
    val message =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.CLIENT_MESSAGE,
        TEST_PAYLOAD
      )
    secureChannel.decryptMessage(message)
    verify(fakeKey).decryptData(any())
  }

  @Test
  @Throws(InterruptedException::class)
  fun decryptMessage_onMessageReceivedErrorForEncryptedMessageWithNoKey() {
    val message =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.CLIENT_MESSAGE,
        TEST_PAYLOAD
      )
    secureChannel.setEncryptionKey(null)
    assertThat(secureChannel.decryptMessage(message)).isFalse()
    verify(mockCallback).onMessageReceivedError(MessageError.MESSAGE_ERROR_DECRYPTION_FAILURE)
    assertThat(message.message).isEmpty()
  }

  @Test
  fun onDeviceMessageReceived_onEstablishSecureChannelFailureBadHandshakeMessage() {
    val message =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.ENCRYPTION_HANDSHAKE,
        TEST_PAYLOAD
      )
    secureChannel.setEncryptionKey(null)
    secureChannel.onDeviceMessageReceived(message)
    verify(mockCallback).onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_MSG)
  }

  @Test
  fun onDeviceMessageReceived_onMessageReceivedNotIssuedForEmptyMessage() {
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
    val secureChannel: MultiProtocolSecureChannel =
      object : MultiProtocolSecureChannel(mockStream, EncryptionRunnerFactory.newFakeRunner()) {
        override fun processHandshake(message: ByteArray) {
          throw HandshakeException("test")
        }
      }
    secureChannel.callback = mockCallback
    val message =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ false,
        OperationType.ENCRYPTION_HANDSHAKE,
        TEST_PAYLOAD
      )
    secureChannel.onDeviceMessageReceived(message)
    verify(mockCallback)
      .onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_HANDSHAKE)
  }

  @Test
  fun onDeviceMessageReceived_processClientMessageIssuesMessageReceivedErrorCallback() {
    secureChannel.setEncryptionKey(null)
    val message =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.CLIENT_MESSAGE,
        TEST_PAYLOAD
      )
    secureChannel.onDeviceMessageReceived(message)
    verify(mockCallback).onMessageReceivedError(MessageError.MESSAGE_ERROR_DECRYPTION_FAILURE)
  }

  @Test
  fun onDeviceMessageReceived_noExceptionWhenReceivedUnknowMessageType() {
    val message =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.UNKNOWN,
        TEST_PAYLOAD
      )
    try {
      secureChannel.onDeviceMessageReceived(message)
    } catch (e: Exception) {
      fail("Should not throw exception.")
    }
  }

  @Test
  fun decompressMessage_returnsOriginalMessageIfOriginalSizeIsZero() {
    val deviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ false,
        OperationType.CLIENT_MESSAGE,
        TEST_PAYLOAD
      )
    deviceMessage.originalMessageSize = 0
    assertThat(secureChannel.decompressMessage(deviceMessage)).isTrue()
    assertThat(deviceMessage.message).isEqualTo(TEST_PAYLOAD)
  }

  @Test
  fun decompressMessage_returnsFlaseWhenThereAreDataFormatException() {
    whenever(mockInflater.inflate(any())).then { throw DataFormatException() }
    val secureChannel =
      object :
        MultiProtocolSecureChannel(
          mockStream,
          EncryptionRunnerFactory.newFakeRunner(),
          inflater = mockInflater
        ) {
        override fun processHandshake(message: ByteArray) {}
      }
    val deviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ false,
        OperationType.CLIENT_MESSAGE,
        TEST_PAYLOAD
      )
    secureChannel.callback = mockCallback
    deviceMessage.originalMessageSize = 1
    assertThat(secureChannel.decompressMessage(deviceMessage)).isFalse()
    verify(mockCallback).onMessageReceivedError(MessageError.MESSAGE_ERROR_DECOMPRESSION_FAILURE)
  }

  @Test
  fun compressMessage_returnsCompressedMessageWithOriginalSize() {
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
    assertThat(deviceMessage.getMessage()).isEqualTo(message)
  }

  @Test
  fun addStream_notifyCallbackWhenMessageReceived() {
    val deviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.CLIENT_MESSAGE,
        TEST_PAYLOAD
      )
    secureChannel.addStream(secondaryStream)
    assertThat(secondaryStream.messageReceivedListener).isNotNull()
    secondaryStream.messageReceivedListener!!.onMessageReceived(deviceMessage)
    verify(mockCallback).onMessageReceived(any())
  }

  @Test
  fun addStream_notifyCallbackWhenProtocolDisconnected() {
    val secureChannel: MultiProtocolSecureChannel =
      object :
        MultiProtocolSecureChannel(secondaryStream, EncryptionRunnerFactory.newFakeRunner()) {
        override fun processHandshake(message: ByteArray) {}
      }
    secureChannel.setEncryptionKey(null)
    secureChannel.callback = mockCallback
    assertThat(secondaryStream.protocolDisconnectListener).isNotNull()
    secondaryStream.protocolDisconnectListener!!.onProtocolDisconnected()
    verify(mockCallback)
      .onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_DEVICE_DISCONNECTED)
  }

  @Test
  fun addStream_doNotNotifyCallbackWhenProtocolDisconnected() {
    secureChannel.addStream(secondaryStream)
    secureChannel.setEncryptionKey(null)
    assertThat(secondaryStream.protocolDisconnectListener).isNotNull()
    secondaryStream.protocolDisconnectListener!!.onProtocolDisconnected()
    verify(mockCallback, never())
      .onEstablishSecureChannelFailure(ChannelError.CHANNEL_ERROR_DEVICE_DISCONNECTED)
  }

  @Test
  fun sendClientMessage_FailedToSendMessageWithNullKey() {
    val deviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.CLIENT_MESSAGE,
        TEST_PAYLOAD
      )
    secureChannel.setEncryptionKey(null)

    assertThat(secureChannel.sendClientMessage(deviceMessage)).isFalse()
  }

  @Test
  fun sendClientMessage_FailedToSendMessageWithEmptyStream() {
    val deviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.CLIENT_MESSAGE,
        TEST_PAYLOAD
      )
    val secureChannel: MultiProtocolSecureChannel =
      object :
        MultiProtocolSecureChannel(secondaryStream, EncryptionRunnerFactory.newFakeRunner()) {
        override fun processHandshake(message: ByteArray) {}
      }
    secureChannel.setEncryptionKey(fakeKey)
    secondaryStream.protocolDisconnectListener!!.onProtocolDisconnected()
    assertThat(secureChannel.sendClientMessage(deviceMessage)).isFalse()
  }

  @Test
  fun sendClientMessage_SuccessfullySendMessage() {
    val deviceMessage =
      DeviceMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        OperationType.CLIENT_MESSAGE,
        TEST_PAYLOAD
      )
    assertThat(secureChannel.sendClientMessage(deviceMessage)).isTrue()
    verify(mockStream).sendMessage(any())
  }

  @Test
  fun sendHandshakeMessage_SetCorrectMessageType() {
    secureChannel.sendHandshakeMessage(TEST_PAYLOAD, false)
    argumentCaptor<DeviceMessage>().apply {
      verify(mockStream).sendMessage(capture())
      assertThat(firstValue.operationType).isEqualTo(OperationType.ENCRYPTION_HANDSHAKE)
      assertThat(firstValue.isMessageEncrypted).isFalse()
    }
  }
}

open class TestProtocol : ConnectionProtocol() {

  fun receiveData(data: ByteArray) {}

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

open class FakeKey() : Key {
  private val testPadding = "padding".toByteArray()

  override fun asBytes(): ByteArray {
    return byteArrayOf()
  }

  override fun encryptData(data: ByteArray): ByteArray {
    return testPadding + data
  }

  override fun decryptData(encryptedData: ByteArray): ByteArray {
    return encryptedData.copyOfRange(testPadding.size, encryptedData.size)
  }

  override fun getUniqueSession(): ByteArray {
    return "UniqueSession".toByteArray()
  }
}
