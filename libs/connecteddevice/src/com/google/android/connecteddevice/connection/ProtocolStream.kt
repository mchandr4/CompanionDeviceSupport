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

import com.google.android.companionprotos.DeviceMessageProto
import com.google.android.companionprotos.OperationProto.OperationType
import com.google.android.companionprotos.PacketProto.Packet
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.util.ByteUtils
import com.google.android.connecteddevice.util.EventLog.onMessageFullyReceived
import com.google.android.connecteddevice.util.EventLog.onMessageStarted
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logw
import com.google.protobuf.ByteString
import com.google.protobuf.ExtensionRegistryLite
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Data stream for a specified [protocol] and its corresponding device identified with
 * [protocolId].
 */
class ProtocolStream(
  private val protocol: ConnectionProtocol,
  private val protocolId: String,
  callbackExecutor: Executor = Executors.newSingleThreadExecutor()
) {
  /** Listener which will be notified when there is new [DeviceMessage] received. */
  var messageReceivedListener: MessageReceivedListener? = null

  /**
   * Listener which will be notified when the underlying protocol is disconnected and this
   * stream can be discarded.
   */
  var deviceDisconnectListener: DeviceDisconnectListener? = null

  private val packetQueue = ArrayDeque<Packet>()
  // messageId  -> accumulated bytes
  private val pendingData: MutableMap<Int, ByteArrayOutputStream> = HashMap()

  // messageId -> nextExpectedPacketNumber
  private val pendingPacketNumber: MutableMap<Int, Int> = HashMap()
  private val messageIdGenerator = MessageIdGenerator()
  private val isSendingInProgress = AtomicBoolean(false)
  private val isConnected = AtomicBoolean(true)

  private var maxWriteSize: Int

  init {
    maxWriteSize = protocol.getMaxWriteSize(protocolId)
    protocol.registerCallback(
      protocolId,
      object : ConnectionProtocol.DeviceCallback {
        override fun onDeviceDisconnected(protocolId: String) {
          isConnected.set(false)
          deviceDisconnectListener?.onDeviceDisconnected()
        }

        override fun onDeviceMaxDataSizeChanged(protocolId: String, maxBytes: Int) {
          maxWriteSize = maxBytes
        }

        override fun onDataReceived(protocolId: String, data: ByteArray?) {
          data?.let { onDataReceived(it) }
        }
      },
      callbackExecutor
    )
  }

  private fun send(data: ByteArray) {
    if (!isConnected.get()) {
      logw(TAG, "Unable to send data to disconnected device.")
      return
    }
    protocol.sendData(
      protocolId, data,
      object : ConnectionProtocol.DataSendCallback {
        override fun onDataSentSuccessfully() {
          isSendingInProgress.set(false)
          writeNextMessageInQueue()
        }

        override fun onDataFailedToSend() {
          loge(TAG, "Data failed to send. Disconnecting.")
          protocol.disconnectDevice(protocolId)
        }
      }
    )
  }

  /**
   * Sends the given [deviceMessage] to the stream with type [operationType].
   *
   * Note: This method will handle the chunking of messages based on the max write size.
   */
  @JvmOverloads
  fun sendMessage(
    deviceMessage: DeviceMessage,
    operationType: OperationType = OperationType.CLIENT_MESSAGE
  ) {
    if (!isConnected.get()) {
      logw(TAG, "Unable to send message to disconnected device.")
      return
    }
    val builder = DeviceMessageProto.Message.newBuilder()
      .setOperation(operationType)
      .setIsPayloadEncrypted(deviceMessage.isMessageEncrypted)
      .setPayload(ByteString.copyFrom(deviceMessage.message))
      .setOriginalSize(deviceMessage.originalMessageSize)
    deviceMessage.recipient?.let {
      builder.recipient = ByteString.copyFrom(ByteUtils.uuidToBytes(it))
    }
    val message = builder.build()
    val rawBytes = message.toByteArray()
    val packets = try {
      PacketFactory.makePackets(rawBytes, messageIdGenerator.next(), maxWriteSize)
    } catch (e: PacketFactoryException) {
      loge(TAG, "Error while creating message packets.", e)
      return
    }
    packetQueue.addAll(packets)
    writeNextMessageInQueue()
  }

  private fun writeNextMessageInQueue() {
    if (packetQueue.isEmpty()) {
      logd(TAG, "There are no more packets to send.")
      return
    }
    val isLockAcquired = isSendingInProgress.compareAndSet(false, true)
    if (!isLockAcquired) {
      logd(TAG, "Unable to send a packet at this time.")
      return
    }
    val packet = packetQueue.remove()
    logd(
      TAG,
      "Writing packet ${packet.packetNumber} of ${packet.totalPackets} for ${packet.messageId}."
    )
    send(packet.toByteArray())
  }

  /** Process incoming data from stream.  */
  @Synchronized // Guarantee order for byte streams
  private fun onDataReceived(data: ByteArray) {
    logd(TAG, "Received ${data.size} bytes.")
    val packet = try {
      Packet.parseFrom(data, ExtensionRegistryLite.getEmptyRegistry())
    } catch (e: IOException) {
      loge(TAG, "Can not parse packet from client. Disconnecting.", e)
      protocol.disconnectDevice(protocolId)
      return
    }
    processPacket(packet)
  }

  private fun processPacket(packet: Packet) {
    if (!isExpectedPacketNumber(packet)) {
      return
    }
    val messageId = packet.messageId
    val currentPayloadStream = pendingData.getOrPut(messageId) { ByteArrayOutputStream() }
    val payload = packet.payload.toByteArray()
    try {
      currentPayloadStream.write(payload)
    } catch (e: IOException) {
      loge(TAG, "Error writing packet to stream. Disconnecting.", e)
      protocol.disconnectDevice(protocolId)
      return
    }
    logd(
      TAG,
      "Parsed packet ${packet.packetNumber} of ${packet.totalPackets} for message $messageId. " +
        "Writing ${payload.size}."
    )
    if (packet.packetNumber == 1) {
      onMessageStarted(messageId)
    }
    if (packet.packetNumber != packet.totalPackets) {
      return
    }

    pendingData.remove(messageId)
    receiveMessage(messageId, currentPayloadStream.toByteArray())
  }

  private fun isExpectedPacketNumber(packet: Packet): Boolean {
    val messageId = packet.messageId
    val packetNumber = packet.packetNumber
    val expectedPacket = pendingPacketNumber.getOrDefault(messageId, 1)

    if (packetNumber == expectedPacket - 1) {
      logw(
        TAG,
        "Received duplicate packet ${packet.packetNumber} for message $messageId. Ignoring."
      )
      return false
    }

    if (packetNumber != expectedPacket) {
      loge(
        TAG,
        "Received unexpected packet $packetNumber for message $messageId. Disconnecting."
      )
      protocol.disconnectDevice(protocolId)
      return false
    }

    pendingPacketNumber[messageId] = packetNumber + 1
    return true
  }

  private fun receiveMessage(messageId: Int, messageBytes: ByteArray) {
    onMessageFullyReceived(messageId, messageBytes.size)
    logd(
      TAG,
      "Received complete device message $messageId of ${messageBytes.size} bytes."
    )
    val message = try {
      DeviceMessageProto.Message.parseFrom(messageBytes, ExtensionRegistryLite.getEmptyRegistry())
    } catch (e: IOException) {
      loge(TAG, "Cannot parse device message from client. Disconnecting.", e)
      protocol.disconnectDevice(protocolId)
      return
    }
    val deviceMessage = DeviceMessage(message)
    messageReceivedListener?.onMessageReceived(deviceMessage, message.operation)
  }

  /** A generator of unique IDs for messages.  */
  private class MessageIdGenerator {
    private val messageId = AtomicInteger(0)
    fun next(): Int {
      val current = messageId.getAndIncrement()
      messageId.compareAndSet(Int.MAX_VALUE, 0)
      return current
    }
  }

  /** Listener to be invoked when a complete message is received from the client. */
  interface MessageReceivedListener {
    /**
     * Called when a complete message is received from the client.
     *
     * @param deviceMessage The message received from the client.
     * @param operationType The [OperationType] of the received message.
     */
    fun onMessageReceived(deviceMessage: DeviceMessage, operationType: OperationType)
  }

  /** Listener to be invoked when the device disconnects and the stream should be discarded. */
  interface DeviceDisconnectListener {
    /** Called when the underlying device has disconnected from the supplied protocol. */
    fun onDeviceDisconnected()
  }

  companion object {
    private const val TAG = "ProtocolStream"
  }
}
