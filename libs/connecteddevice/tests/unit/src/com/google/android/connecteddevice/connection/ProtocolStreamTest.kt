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
import com.google.android.companionprotos.DeviceMessageProto.Message
import com.google.android.companionprotos.OperationProto.OperationType
import com.google.android.companionprotos.PacketProto.Packet
import com.google.android.connecteddevice.connection.ProtocolStream.MessageReceivedListener
import com.google.android.connecteddevice.connection.ProtocolStream.ProtocolDisconnectListener
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.ProtocolDevice
import com.google.android.connecteddevice.util.ByteUtils
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.google.protobuf.ByteString
import com.google.protobuf.ExtensionRegistryLite
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

private const val PROTOCOL_ID = "testDevice"
private const val MAX_WRITE_SIZE = 185

@RunWith(AndroidJUnit4::class)
class ProtocolStreamTest {

  private val protocol = spy(TestProtocol())

  private val stream = ProtocolStream(ProtocolDevice(protocol, PROTOCOL_ID), directExecutor())

  @Test
  fun sendMessage_smallMessageSendsSinglePacket() {
    val recipient = UUID.randomUUID()
    val message = ByteUtils.randomBytes(MAX_WRITE_SIZE / 2)
    stream.sendMessage(
      DeviceMessage(
        recipient,
        /* isMessageEncrypted= */ false,
        DeviceMessage.OperationType.CLIENT_MESSAGE,
        message
      )
    )
    verify(protocol).sendData(eq(PROTOCOL_ID), any(), any())
  }

  @Test
  fun sendMessage_largeMessageSendsMultiplePackets() {
    val recipient = UUID.randomUUID()
    val message = ByteUtils.randomBytes(MAX_WRITE_SIZE + 1)
    stream.sendMessage(
      DeviceMessage(
        recipient,
        /* isMessageEncrypted= */ false,
        DeviceMessage.OperationType.CLIENT_MESSAGE,
        message
      )
    )
    verify(protocol, times(2)).sendData(eq(PROTOCOL_ID), any(), any())
  }

  @Test
  fun sendMessage_serializesPayloadIntoPacket() {
    val recipient = UUID.randomUUID()
    val message = ByteUtils.randomBytes(MAX_WRITE_SIZE / 2)
    stream.sendMessage(
      DeviceMessage(
        recipient,
        /* isMessageEncrypted= */ false,
        DeviceMessage.OperationType.CLIENT_MESSAGE,
        message
      )
    )
    argumentCaptor<ByteArray>().apply {
      verify(protocol).sendData(eq(PROTOCOL_ID), capture(), any())
      val packet = Packet.parseFrom(firstValue, ExtensionRegistryLite.getEmptyRegistry())
      val sentMessage =
        Message.parseFrom(packet.payload.toByteArray(), ExtensionRegistryLite.getEmptyRegistry())
          .payload
          .toByteArray()
      assertThat(message).isEqualTo(sentMessage)
    }
  }

  @Test
  fun protocolDisconnect_preventsFutureMessagesFromBeingSent() {
    protocol.disconnectDevice(PROTOCOL_ID)
    val recipient = UUID.randomUUID()
    val message = ByteUtils.randomBytes(MAX_WRITE_SIZE / 2)
    stream.sendMessage(
      DeviceMessage(
        recipient,
        /* isMessageEncrypted= */ false,
        DeviceMessage.OperationType.CLIENT_MESSAGE,
        message
      )
    )
    verify(protocol, never()).sendData(eq(PROTOCOL_ID), any(), any())
  }

  @Test
  fun protocolDisconnect_invokesProtocolDisconnectListener() {
    val listener: ProtocolDisconnectListener = mock()
    stream.protocolDisconnectListener = listener
    protocol.disconnectDevice(PROTOCOL_ID)
    verify(listener).onProtocolDisconnected()
  }

  @Test
  fun protocolOnDataFailedToSend_disconnectsProtocol() {
    val failingProtocol = spy(FailingSendProtocol())
    val failingStream =
      ProtocolStream(ProtocolDevice(failingProtocol, PROTOCOL_ID), directExecutor())
    val recipient = UUID.randomUUID()
    val message = ByteUtils.randomBytes(MAX_WRITE_SIZE / 2)
    failingStream.sendMessage(
      DeviceMessage(
        recipient,
        /* isMessageEncrypted= */ false,
        DeviceMessage.OperationType.CLIENT_MESSAGE,
        message
      )
    )
    verify(failingProtocol).disconnectDevice(PROTOCOL_ID)
  }

  @Test
  fun onMessageReceived_invokedWithSinglePacketMessage() {
    val listener: MessageReceivedListener = mock()
    stream.messageReceivedListener = listener
    val message = ByteUtils.randomBytes(MAX_WRITE_SIZE / 2)
    val packets = createPackets(message)
    packets.forEach { protocol.receiveData(it.toByteArray()) }
    val captor = argumentCaptor<DeviceMessage>()
    verify(listener).onMessageReceived(captor.capture())
    assertThat(message).isEqualTo(captor.firstValue.message)
  }

  @Test
  fun onMessageReceived_invokedWithMultiplePacketMessage() {
    val listener: MessageReceivedListener = mock()
    stream.messageReceivedListener = listener
    val message = ByteUtils.randomBytes(MAX_WRITE_SIZE + 1)
    val packets = createPackets(message)
    packets.forEach { protocol.receiveData(it.toByteArray()) }
    val captor = argumentCaptor<DeviceMessage>()
    verify(listener).onMessageReceived(captor.capture())
    assertThat(message).isEqualTo(captor.firstValue.message)
  }

  @Test
  fun onMessageReceived_parallelMessagesSuccessful() {
    val listener: MessageReceivedListener = mock()
    stream.messageReceivedListener = listener
    val longMessage = ByteUtils.randomBytes((MAX_WRITE_SIZE * 2.5).toInt())
    val shortMessage = ByteUtils.randomBytes(MAX_WRITE_SIZE / 2)
    val longPackets = createPackets(longMessage)
    val shortPackets = createPackets(shortMessage)
    protocol.receiveData(longPackets.first().toByteArray())
    protocol.receiveData(shortPackets.first().toByteArray())
    val captor = argumentCaptor<DeviceMessage>()
    verify(listener).onMessageReceived(captor.capture())
    assertThat(shortMessage).isEqualTo(captor.firstValue.message)
  }

  @Test
  fun onDataReceived_duplicatePacketIsIgnored() {
    val listener: MessageReceivedListener = mock()
    stream.messageReceivedListener = listener
    val message = ByteUtils.randomBytes(MAX_WRITE_SIZE + 1)
    val packets = createPackets(message)
    protocol.receiveData(packets.first().toByteArray())
    packets.forEach { protocol.receiveData(it.toByteArray()) }
    val captor = argumentCaptor<DeviceMessage>()
    verify(listener).onMessageReceived(captor.capture())
    assertThat(message).isEqualTo(captor.firstValue.message)
  }

  @Test
  fun onDataReceived_packetBeforeExpectedRangeDisconnectsDevice() {
    val message = ByteUtils.randomBytes((MAX_WRITE_SIZE * 2.5).toInt())
    val packets = createPackets(message)
    protocol.receiveData(packets[0].toByteArray())
    protocol.receiveData(packets[1].toByteArray())
    protocol.receiveData(packets[0].toByteArray())
    verify(protocol).disconnectDevice(PROTOCOL_ID)
  }

  @Test
  fun onDataReceived_packetAfterExpectedRangeDisconnectsDevice() {
    val message = ByteUtils.randomBytes((MAX_WRITE_SIZE * 2.5).toInt())
    val packets = createPackets(message)
    protocol.receiveData(packets.first().toByteArray())
    protocol.receiveData(packets.last().toByteArray())
    verify(protocol).disconnectDevice(PROTOCOL_ID)
  }

  private fun createPackets(data: ByteArray): List<Packet> {
    return try {
      val message =
        Message.newBuilder()
          .setPayload(ByteString.copyFrom(data))
          .setOperation(OperationType.CLIENT_MESSAGE)
          .build()
      PacketFactory.makePackets(
        message.toByteArray(),
        ThreadLocalRandom.current().nextInt(),
        MAX_WRITE_SIZE
      )
    } catch (e: Exception) {
      Truth.assertWithMessage("Uncaught exception while making packets.").fail()
      ArrayList()
    }
  }

  open class TestProtocol : ConnectionProtocol() {
    override val isDeviceVerificationRequired = false

    fun receiveData(data: ByteArray) {
      dataReceivedListeners[PROTOCOL_ID]?.invoke { it.onDataReceived(PROTOCOL_ID, data) }
    }

    override fun startAssociationDiscovery(name: String, callback: DiscoveryCallback) {}

    override fun startConnectionDiscovery(
      id: UUID,
      challenge: ConnectChallenge,
      callback: DiscoveryCallback
    ) {}

    override fun stopAssociationDiscovery() {}

    override fun stopConnectionDiscovery(id: UUID) {}

    override fun sendData(protocolId: String, data: ByteArray, callback: DataSendCallback?) {
      callback?.onDataSentSuccessfully()
    }

    override fun disconnectDevice(protocolId: String) {
      deviceDisconnectedListeners[protocolId]?.invoke { it.onDeviceDisconnected(protocolId) }
    }

    override fun reset() {}

    override fun getMaxWriteSize(protocolId: String): Int {
      return MAX_WRITE_SIZE
    }
  }

  open class FailingSendProtocol : ConnectionProtocol() {
    override val isDeviceVerificationRequired = false

    override fun startAssociationDiscovery(name: String, callback: DiscoveryCallback) {}

    override fun startConnectionDiscovery(
      id: UUID,
      challenge: ConnectChallenge,
      callback: DiscoveryCallback
    ) {}

    override fun stopAssociationDiscovery() {}

    override fun stopConnectionDiscovery(id: UUID) {}

    override fun sendData(protocolId: String, data: ByteArray, callback: DataSendCallback?) {
      callback?.onDataFailedToSend()
    }

    override fun disconnectDevice(protocolId: String) {}

    override fun reset() {}

    override fun getMaxWriteSize(protocolId: String): Int {
      return MAX_WRITE_SIZE
    }
  }
}
