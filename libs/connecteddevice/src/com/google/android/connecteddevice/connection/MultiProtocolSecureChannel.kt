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

import androidx.annotation.VisibleForTesting
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.model.DeviceMessage.OperationType
import com.google.android.connecteddevice.util.SafeConsumer
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.encryptionrunner.EncryptionRunner
import com.google.android.encryptionrunner.HandshakeException
import com.google.android.encryptionrunner.Key
import java.security.SignatureException
import java.util.Arrays
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Establishes a secure channel with [EncryptionRunner] over [ProtocolStream]s as server side, sends
 * and receives messages securely after the secure channel has been established.
 */
abstract class MultiProtocolSecureChannel(
  stream: ProtocolStream,
  protected val encryptionRunner: EncryptionRunner,
  @VisibleForTesting internal val inflater: Inflater = Inflater(),
  private val deflater: Deflater = Deflater(Deflater.BEST_COMPRESSION),
  private val isCompressionEnabled: Boolean = true
) {

  enum class ChannelError {
    /** Indicates an error during a Handshake of EncryptionRunner. */
    CHANNEL_ERROR_INVALID_HANDSHAKE,

    /** Received an invalid handshake message or has an invalid handshake message to send. */
    CHANNEL_ERROR_INVALID_MSG,

    /** Unable to retrieve a valid device id. */
    CHANNEL_ERROR_INVALID_DEVICE_ID,

    /** Unable to get verification code or there's a error during pin verification. */
    CHANNEL_ERROR_INVALID_VERIFICATION,

    /** Encountered an unexpected handshake state. */
    CHANNEL_ERROR_INVALID_STATE,

    /** Failed to get a valid previous/new encryption key. */
    CHANNEL_ERROR_INVALID_ENCRYPTION_KEY,

    /** Failed to save or retrieve security keys to or from storage. */
    CHANNEL_ERROR_STORAGE_ERROR,

    /** Disconnected before secure channel is established. */
    CHANNEL_ERROR_DEVICE_DISCONNECTED
  }

  enum class MessageError {
    /** Indicates an error when decrypting the message. */
    MESSAGE_ERROR_DECRYPTION_FAILURE,
    /** Indicates an error when decompressing the message. */
    MESSAGE_ERROR_DECOMPRESSION_FAILURE
  }

  private val streams = CopyOnWriteArrayList<ProtocolStream>()

  private val encryptionKey = AtomicReference<Key>()

  /** Should be set whenever the remote device id is available. */
  protected var deviceId: String? = null

  /** Callback that notifies secure channel events. */
  var callback: Callback? = null

  init {
    addStream(stream)
  }

  /** Logic for processing a handshake message from device. */
  @Throws(HandshakeException::class) abstract fun processHandshake(message: ByteArray)

  /** Set the encryption key that secures this channel. */
  internal fun setEncryptionKey(encryptionKey: Key?) {
    this.encryptionKey.set(encryptionKey)
  }

  /** Add a protocol stream to this channel. */
  fun addStream(stream: ProtocolStream) {
    streams.add(stream)
    stream.messageReceivedListener =
      object : ProtocolStream.MessageReceivedListener {
        override fun onMessageReceived(deviceMessage: DeviceMessage) {
          logd(TAG, "Message received from stream $stream")
          onDeviceMessageReceived(deviceMessage)
        }
      }
    stream.protocolDisconnectListener =
      object : ProtocolStream.ProtocolDisconnectListener {
        override fun onProtocolDisconnected() {
          logd(TAG, "Protocol of stream $stream disconnected. Removing from secure channel.")
          streams.remove(stream)
          // Notify secure channel error during association if device get disconnected before secure
          // channel is established.
          if (streams.isEmpty() && deviceId == null) {
            notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_DEVICE_DISCONNECTED)
          }
        }
      }
  }

  internal fun sendHandshakeMessage(message: ByteArray, isEncrypted: Boolean) {
    logd(TAG, "Sending handshake message.")
    val deviceMessage =
      DeviceMessage(/* recipient= */ null, isEncrypted, OperationType.ENCRYPTION_HANDSHAKE, message)
    sendMessage(deviceMessage)
  }

  /**
   * Send a client [DeviceMessage] to remote device. Returns 'true' if the send is successful,
   * `false` if this method is called with an encrypted message only after the secure channel has
   * been established
   */
  fun sendClientMessage(deviceMessage: DeviceMessage): Boolean {
    if (encryptionKey.get() == null) {
      loge(TAG, "Attempted to send client message when secure channel is not established. Ignored.")
      return false
    }

    if (!deviceMessage.isMessageEncrypted) {
      loge(TAG, "Attempted to send un-encrypted client message. Ignored.")
      return false
    }

    return sendMessage(deviceMessage)
  }

  private fun sendMessage(deviceMessage: DeviceMessage): Boolean {
    val stream = streams.firstOrNull()
    if (stream == null) {
      loge(TAG, "Attempted to send a message to a disconnected device, ignored.")
      return false
    }

    if (isCompressionEnabled) {
      compressMessage(deviceMessage)
    }
    if (deviceMessage.isMessageEncrypted) {
      try {
        encryptMessage(deviceMessage)
      } catch (e: IllegalStateException) {
        loge(TAG, "Secure channel has not been established.")
        return false
      }
    }
    // TODO(b/189247832): Send message through one of the connected streams for now.
    stream.sendMessage(deviceMessage)
    return true
  }

  private fun encryptMessage(deviceMessage: DeviceMessage) {
    val key =
      encryptionKey.get() ?: throw IllegalStateException("Secure channel has not been established.")
    key.encryptData(deviceMessage.message)
  }

  /** Inform the secure channel related events through [callback]. */
  protected fun notifyCallback(notification: SafeConsumer<Callback>) {
    if (callback != null) {
      notification.accept(callback)
    }
  }

  /** Notify callbacks that an error has occurred. */
  protected fun notifySecureChannelFailure(error: ChannelError) {
    loge(TAG, "Secure channel error: $error")
    notifyCallback { it.onEstablishSecureChannelFailure(error) }
  }

  /**
   * Process the inner message and replace with decrypted value if necessary. If an error occurs the
   * inner message will be replaced with `null` and call [Callback.onMessageReceivedError] on the
   * registered callback.
   *
   * @param deviceMessage The message to process.
   * @return `true` if message was successfully processed. `false` if an error occurred.
   */
  internal fun decryptMessage(deviceMessage: DeviceMessage): Boolean {
    if (!deviceMessage.isMessageEncrypted) {
      logd(TAG, "Message was not encrypted. No further action necessary.")
      return true
    }
    val key = encryptionKey.get()
    if (key == null) {
      loge(TAG, "Received encrypted message before secure channel has been established.")
      // Clear the unusable message.
      deviceMessage.message = byteArrayOf()
      notifyCallback { it.onMessageReceivedError(MessageError.MESSAGE_ERROR_DECRYPTION_FAILURE) }
      return false
    }
    return try {
      val decryptedMessage = key.decryptData(deviceMessage.message)
      deviceMessage.message = decryptedMessage
      logd(TAG, "Decrypted secure message.")
      true
    } catch (e: SignatureException) {
      loge(TAG, "Could not decrypt secure message.", e)
      notifyCallback { it.onMessageReceivedError(MessageError.MESSAGE_ERROR_DECRYPTION_FAILURE) }
      false
    }
  }

  @VisibleForTesting
  internal fun onDeviceMessageReceived(deviceMessage: DeviceMessage) {
    if (deviceMessage.message.isEmpty()) {
      loge(TAG, "Received empty message, ignored.")
      return
    }
    var success = decryptMessage(deviceMessage)
    if (success) {
      success = decompressMessage(deviceMessage)
    }
    when (val operationType = deviceMessage.operationType) {
      OperationType.ENCRYPTION_HANDSHAKE -> {
        if (!success) {
          notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_MSG)
          return
        }
        logd(TAG, "Received handshake message.")
        try {
          processHandshake(deviceMessage.message)
        } catch (e: HandshakeException) {
          loge(TAG, "Handshake failed.", e)
          notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_HANDSHAKE)
        }
      }
      OperationType.CLIENT_MESSAGE, OperationType.QUERY, OperationType.QUERY_RESPONSE -> {
        if (!success) {
          loge(TAG, "Invalid message received. Ignored.")
          return
        }
        logd(TAG, "Received client message.")
        notifyCallback { it.onMessageReceived(deviceMessage) }
      }
      else -> loge(TAG, "Received unexpected operation type: ${operationType.name}.")
    }
  }

  @VisibleForTesting
  internal fun compressMessage(deviceMessage: DeviceMessage) {
    val originalMessage = deviceMessage.message
    val compressedMessage = ByteArray(originalMessage.size)
    deflater.reset()
    deflater.setInput(originalMessage)
    deflater.finish()
    val compressedSize = deflater.deflate(compressedMessage)
    if (compressedSize >= originalMessage.size) {
      logd(TAG, "Message compression resulted in no savings. Sending original message.")
      deviceMessage.originalMessageSize = 0
      return
    }
    deviceMessage.originalMessageSize = originalMessage.size
    deviceMessage.message = Arrays.copyOf(compressedMessage, compressedSize)
    val compressionSavings =
      (Math.round((originalMessage.size - compressedSize) / originalMessage.size.toDouble()) * 100L)
    logd(
      TAG,
      "Message compressed from ${originalMessage.size} to $compressedSize bytes saving " +
        "$compressionSavings%"
    )
  }

  @VisibleForTesting
  internal fun decompressMessage(deviceMessage: DeviceMessage): Boolean {
    val message = deviceMessage.message
    val originalMessageSize = deviceMessage.originalMessageSize
    if (originalMessageSize == 0) {
      logd(TAG, "Incoming message was not compressed. No further action necessary.")
      return true
    }
    inflater.reset()
    inflater.setInput(message)
    val decompressedMessage = ByteArray(originalMessageSize)
    try {
      inflater.inflate(decompressedMessage)
      deviceMessage.message = decompressedMessage
    } catch (e: DataFormatException) {
      notifyCallback { it.onMessageReceivedError(MessageError.MESSAGE_ERROR_DECOMPRESSION_FAILURE) }
      loge(TAG, "An error occurred while decompressing the message.", e)
      return false
    }
    logd(
      TAG,
      "Message successfully decompressed from ${message.size} to $originalMessageSize bytes."
    )
    return true
  }

  /**
   * Callbacks that will be invoked during establishing secure channel, sending and receiving
   * messages securely.
   */
  interface Callback {
    /** Invoked when secure channel has been established successfully. */
    fun onSecureChannelEstablished() {}

    /**
     * Invoked when a [ChannelError] has been encountered in attempting to establish a secure
     * channel.
     *
     * @param error The failure indication.
     */
    fun onEstablishSecureChannelFailure(error: ChannelError) {}

    /**
     * Invoked when a complete message is received securely from the client and decrypted.
     *
     * @param deviceMessage The [DeviceMessage] with decrypted message.
     */
    fun onMessageReceived(deviceMessage: DeviceMessage) {}

    /**
     * Invoked when there was an error during a processing or decrypting of a client message.
     *
     * @param error The message processing error.
     */
    fun onMessageReceivedError(error: MessageError) {}

    /**
     * Invoked when the device id was received from the client.
     *
     * @param deviceId The unique device id of client.
     */
    fun onDeviceIdReceived(deviceId: String) {}
  }

  companion object {
    private const val TAG = "MultiProtocolsSecureChannel"
  }
}
