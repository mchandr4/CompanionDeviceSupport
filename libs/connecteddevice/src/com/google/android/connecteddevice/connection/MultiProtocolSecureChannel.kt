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
import com.google.android.companionprotos.VerificationCode
import com.google.android.companionprotos.VerificationCodeState
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.model.DeviceMessage.OperationType
import com.google.android.connecteddevice.oob.OobRunner
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.util.SafeConsumer
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.encryptionrunner.EncryptionRunner
import com.google.android.encryptionrunner.HandshakeException
import com.google.android.encryptionrunner.HandshakeMessage.HandshakeState
import com.google.android.encryptionrunner.Key
import com.google.protobuf.ByteString
import java.security.SignatureException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.roundToLong

/**
 * Establishes a secure channel with [EncryptionRunner] over [ProtocolStream]s as server side, sends
 * and receives messages securely after the secure channel has been established.
 */
open class MultiProtocolSecureChannel(
  stream: ProtocolStream,
  private val storage: ConnectedDeviceStorage,
  private val encryptionRunner: EncryptionRunner,
  private val oobRunner: OobRunner? = null,
  /** Should be set whenever the remote device id is available. */
  private var deviceId: String? = null,
  protected val inflater: Inflater = Inflater(),
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

    /** Disconnected before secure channel is established. */
    CHANNEL_ERROR_DEVICE_DISCONNECTED
  }

  enum class MessageError {
    /** Indicates an error when decrypting the message. */
    MESSAGE_ERROR_DECRYPTION_FAILURE,
    /** Indicates an error when decompressing the message. */
    MESSAGE_ERROR_DECOMPRESSION_FAILURE
  }

  private val streams = ConcurrentHashMap.newKeySet<ProtocolStream>()

  private val encryptionKey = AtomicReference<Key>()

  private var visualVerificationCode: String? = null

  var showVerificationCodeListener: ShowVerificationCodeListener? = null

  /**
   * The out of band verification code get from the [EncryptionRunner], will be set during out of
   * band association.
   */
  private var oobCode: ByteArray? = null

  @HandshakeState private var state: Int = HandshakeState.UNKNOWN

  /** Callback that notifies secure channel events. */
  var callback: Callback? = null

  init {
    addStream(stream)
  }

  /** Logic for processing a handshake message from device. */
  @Throws(HandshakeException::class)
  fun processHandshake(message: ByteArray) {
    when (state) {
      HandshakeState.UNKNOWN -> processHandshakeInitialization(message)
      HandshakeState.IN_PROGRESS -> processHandshakeInProgress(message)
      HandshakeState.VERIFICATION_NEEDED -> processVerificationCodeMessage(message)
      HandshakeState.FINISHED ->
        // Should not reach this line.
        loge(TAG, "Received handshake message after handshake is completed. Ignored.")
      HandshakeState.RESUMING_SESSION -> processHandshakeResumingSession(message)
      else -> {
        loge(TAG, "Encountered unexpected handshake state: $state.")
        notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_STATE)
      }
    }
  }

  @Throws(HandshakeException::class)
  private fun processHandshakeInitialization(message: ByteArray) {
    logd(TAG, "Responding to handshake init request.")
    val handshakeMessage = encryptionRunner.respondToInitRequest(message)
    state = handshakeMessage.handshakeState
    val nextMessage = handshakeMessage.nextMessage
    if (nextMessage == null) {
      loge(TAG, "Failed to get the next message after handshake initialization.")
      notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_HANDSHAKE)
      return
    }
    sendHandshakeMessage(nextMessage)
  }

  @Throws(HandshakeException::class)
  private fun processHandshakeInProgress(message: ByteArray) {
    logd(TAG, "Continuing handshake.")
    val handshakeMessage = encryptionRunner.continueHandshake(message)
    state = handshakeMessage.handshakeState
    if (deviceId != null) {
      // No further actions needed here during reconnect.
      return
    }
    when (state) {
      HandshakeState.VERIFICATION_NEEDED -> {
        visualVerificationCode = handshakeMessage.verificationCode
        val fullVerificationCode = handshakeMessage.fullVerificationCode
        if (fullVerificationCode != null) {
          this.oobCode = fullVerificationCode
        } else {
          loge(TAG, "Full verification is null. Notify channel error")
          notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_STATE)
        }
      }
      else -> {
        loge(
          TAG,
          "processHandshakeInProgress: Encountered unexpected handshake state: $state. " +
            "Notify callback of failure."
        )
        notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_STATE)
      }
    }
  }

  /** Process the message received from remote device during verification stage. */
  private fun processVerificationCodeMessage(message: ByteArray) {
    val verificationMessage = VerificationCode.parseFrom(message)
    when (verificationMessage.state) {
      VerificationCodeState.OOB_VERIFICATION ->
        confirmOobVerificationCode(verificationMessage.payload.toByteArray())
      VerificationCodeState.VISUAL_VERIFICATION -> invokeVerificationCodeListener()
      else -> {
        loge(TAG, "Unexpected verification message received, issue error callback.")
        notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_STATE)
      }
    }
  }

  /** Should be called when OOB verification code is received from remote device. */
  protected fun confirmOobVerificationCode(encryptedCode: ByteArray) {
    val runner = oobRunner
    if (runner == null) {
      loge(TAG, "Missing OOB elements during OOB verification, issue error callback.")
      notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_ENCRYPTION_KEY)
      return
    }
    val decryptedCode: ByteArray =
      try {
        runner.decryptData(encryptedCode)
      } catch (e: Exception) {
        loge(TAG, "Decryption failed for verification code exchange", e)
        notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_ENCRYPTION_KEY)
        return
      }
    val code = oobCode
    if (code == null || !decryptedCode.contentEquals(code)) {
      loge(TAG, "Exchanged verification codes do not match. Notify callback of failure.")
      notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_ENCRYPTION_KEY)
      return
    }
    encryptAndSendOobVerificationCode(code, runner)
    verificationCodeAcceptedInternal()
  }

  private fun encryptAndSendOobVerificationCode(code: ByteArray, oobRunner: OobRunner) {
    val encryptedCode: ByteArray =
      try {
        oobRunner.encryptData(code)
      } catch (e: Exception) {
        loge(TAG, "Encryption failed for verification code exchange.", e)
        notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_ENCRYPTION_KEY)
        return
      }
    sendHandshakeMessage(createOobResponse(encryptedCode))
  }

  /** Generate understandable OOB data response which will be sent to remote device. */
  private fun createOobResponse(code: ByteArray) =
    VerificationCode.newBuilder()
      .run {
        setState(VerificationCodeState.OOB_VERIFICATION)
        setPayload(ByteString.copyFrom(code))
        build()
      }
      .toByteArray()

  /** Communicate with other components that the visual verification code is available. */
  protected fun invokeVerificationCodeListener() {
    if (showVerificationCodeListener == null) {
      loge(
        TAG,
        "No verification code callback has been set. Unable to display verification code " +
          "to user."
      )
      notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_STATE)
      return
    }
    val code = visualVerificationCode
    if (code == null) {
      loge(TAG, "No verification code set. Unable to display verification code to user.")
      notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_STATE)
      return
    }

    logd(TAG, "Notify pairing code available: $code")
    showVerificationCodeListener!!.showVerificationCode(code)
  }

  @Throws(HandshakeException::class)
  private fun processHandshakeResumingSession(message: ByteArray) {
    logd(TAG, "Process resuming session.")
    if (deviceId == null) {
      loge(TAG, "Reconnect with invalid device id.")
      notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_DEVICE_ID)
      return
    }
    logd(TAG, "Start reconnection authentication.")
    val previousKey = storage.getEncryptionKey(deviceId.toString())
    if (previousKey == null) {
      loge(TAG, "Unable to resume session, previous key is null.")
      notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_ENCRYPTION_KEY)
      return
    }
    val handshakeMessage = encryptionRunner.authenticateReconnection(message, previousKey)
    state = handshakeMessage.handshakeState
    if (state != HandshakeState.FINISHED) {
      loge(TAG, "Unable to resume session, unexpected next handshake state: $state.")
      notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_STATE)
      return
    }
    val newKey = handshakeMessage.key
    if (newKey == null) {
      loge(TAG, "Unable to resume session, new key is null.")
      notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_ENCRYPTION_KEY)
      return
    }
    storage.saveEncryptionKey(deviceId.toString(), newKey.asBytes())
    logd(TAG, "Saved new key for reconnection.")
    encryptionKey.set(newKey)
    sendServerAuthToClient(handshakeMessage.nextMessage)
    notifyCallback { it.onSecureChannelEstablished() }
  }

  private fun sendServerAuthToClient(message: ByteArray?) {
    if (message == null) {
      loge(TAG, "Unable to send server authentication message to client, message is null.")
      notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_MSG)
      return
    }
    sendHandshakeMessage(message)
  }

  /** Notify that the device id is received from remote device during association. */
  open fun setDeviceIdDuringAssociation(deviceId: UUID) {
    this.deviceId = deviceId.toString()
    if (encryptionKey.get() == null) {
      loge(TAG, "Key is null when received client device id $deviceId.")
      notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_ENCRYPTION_KEY)
      return
    }
    storage.saveEncryptionKey(deviceId.toString(), encryptionKey.get().asBytes())
  }

  /**
   * Called by the client to notify that the user has accepted a pairing code or any out-of-band
   * confirmation, and send confirmation signals to remote bluetooth device.
   */
  open fun notifyVerificationCodeAccepted() {
    val confirmationMessage =
      VerificationCode.newBuilder().run {
        setState(VerificationCodeState.VISUAL_CONFIRMATION)
        build()
      }
    sendHandshakeMessage(confirmationMessage.toByteArray())
    verificationCodeAcceptedInternal()
  }

  private fun verificationCodeAcceptedInternal() {
    oobCode = null
    val message =
      try {
        encryptionRunner.notifyPinVerified()
      } catch (e: HandshakeException) {
        loge(TAG, "Error during PIN verification", e)
        notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_VERIFICATION)
        return
      }
    state = message.handshakeState
    if (state != HandshakeState.FINISHED) {
      loge(
        TAG,
        "Handshake not finished after calling verify PIN. Instead got state: " +
          "${message.handshakeState}."
      )
      notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_STATE)
      return
    }
    val localKey = message.key
    if (localKey == null) {
      loge(TAG, "Unable to finish association, generated key is null.")
      notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_ENCRYPTION_KEY)
      return
    }
    encryptionKey.set(localKey)
    logd(TAG, "Pairing code successfully verified.")
    notifyCallback { it.onSecureChannelEstablished() }
  }

  /** Add a protocol stream to this channel. */
  fun addStream(stream: ProtocolStream) {
    streams.add(stream)
    stream.messageReceivedListener =
      object : ProtocolStream.MessageReceivedListener {
        override fun onMessageReceived(deviceMessage: DeviceMessage) {
          logd(TAG, "A new message was received from the stream.")
          onDeviceMessageReceived(deviceMessage)
        }
      }
    stream.protocolDisconnectListener =
      object : ProtocolStream.ProtocolDisconnectListener {
        override fun onProtocolDisconnected() {
          logd(TAG, "The stream's protocol has disconnected. Removing from secure channel.")
          streams.remove(stream)
          // Notify secure channel error during association if device get disconnected before secure
          // channel is established.
          if (streams.isEmpty() && deviceId == null) {
            loge(TAG, "There are no more streams to complete association.")
            notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_DEVICE_DISCONNECTED)
          }
        }
      }
  }

  /** Send un-encrypted message to remote device during handshake. */
  protected fun sendHandshakeMessage(message: ByteArray) {
    logd(TAG, "Sending handshake message.")
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        /* recipient= */ null,
        false,
        OperationType.ENCRYPTION_HANDSHAKE,
        message
      )
    sendMessage(deviceMessage)
  }

  /**
   * Send a client [DeviceMessage] to remote device. Returns 'true' if the send is successful,
   * `false` if this method is called with an encrypted message only after the secure channel has
   * been established
   */
  open fun sendClientMessage(deviceMessage: DeviceMessage): Boolean {
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
    val encryptedMessage = key.encryptData(deviceMessage.message)
    deviceMessage.setMessage(encryptedMessage)
  }

  /** Inform the secure channel related events through [callback]. */
  private fun notifyCallback(notification: SafeConsumer<Callback>) {
    callback?.let { notification.accept(it) }
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

        if (state == HandshakeState.FINISHED) {
          logd(TAG, "Received device id from remote device.")
          notifyCallback { it.onMessageReceived(deviceMessage) }
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
    deviceMessage.message = compressedMessage.copyOf(compressedSize)
    val compressionSavings =
      ((originalMessage.size - compressedSize) / originalMessage.size.toDouble()).roundToLong() *
        100L
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
  }

  /** Listener that will be invoked to display verification code. */
  interface ShowVerificationCodeListener {
    /** Invoked when a verification [code] needs to be displayed during device association. */
    fun showVerificationCode(code: String)
  }

  companion object {
    private const val TAG = "MultiProtocolSecureChannel"
  }
}
