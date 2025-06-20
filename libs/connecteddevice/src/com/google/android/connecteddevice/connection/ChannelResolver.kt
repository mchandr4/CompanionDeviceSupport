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

import com.google.android.companionprotos.VersionExchangeProto.VersionExchange
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.model.DeviceMessage.OperationType.ENCRYPTION_HANDSHAKE
import com.google.android.connecteddevice.oob.OobChannel
import com.google.android.connecteddevice.oob.OobRunner
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.IDataReceivedListener
import com.google.android.connecteddevice.transport.ProtocolDevice
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.encryptionrunner.EncryptionRunner
import com.google.android.encryptionrunner.EncryptionRunnerFactory.EncryptionRunnerType
import com.google.android.encryptionrunner.EncryptionRunnerFactory.newRunner
import com.google.protobuf.ExtensionRegistryLite
import com.google.protobuf.InvalidProtocolBufferException
import java.lang.Integer.min
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.properties.Delegates
import kotlinx.coroutines.runBlocking

/**
 * Manages the version, capability exchange and device verification that must be completed in order
 * to establish a secure channel with a remote device.
 *
 * [resolveReconnect] or [resolveAssociation] must be called right after the [ChannelResolver]
 * initiation. [ChannelResolver] would use the [ConnectionProtocol] that the first message was from
 * as current protocol to receive and send messages after.
 *
 * Channel resolving for association:
 * 1. Process the first received data as version exchange message, send back car versions.
 * 2. Create a [ProtocolStream] with the current [ConnectionProtocol].
 * 3. Create a [MultiProtocolSecureChannel], add [ProtocolStream]s for all connected
 *    [ConnectionProtocol]s and notify that the channel has been resolved.
 *
 * Channel resolving for reconnect:
 * 1. Process the first received data as version exchange message, send back car versions.
 * 2. Create a [ProtocolStream] with the current [ConnectionProtocol].
 * 3. If the current [ConnectionProtocol] requires device verification, process the next
 *    [DeviceMessage] received over the created [ProtocolStream] as challenge message and send back
 *    new challenge.
 * 4. Create a [MultiProtocolSecureChannel], add [ProtocolStream]s for all connected
 *    [ConnectionProtocol]s and notify that the channel has been resolved.
 */
class ChannelResolver(
  private val protocolDevice: ProtocolDevice,
  private val storage: ConnectedDeviceStorage,
  private val callback: Callback,
  private val streamFactory: ProtocolStreamFactory = ProtocolStreamFactoryImpl(),
  private var encryptionRunner: EncryptionRunner = newRunner(EncryptionRunnerType.UKEY2),
) {
  private val currentDevice: AtomicReference<ProtocolDevice?> =
    AtomicReference<ProtocolDevice?>(null)
  // protocol device -> listeners. This map tracks listeners for channel resolving on connected
  // protocols.
  private val protocolDevices = ConcurrentHashMap<ProtocolDevice, IDataReceivedListener>()
  private var isReconnect by Delegates.notNull<Boolean>()
  private var deviceId: UUID? = null
  private var challenge: ByteArray? = null
  private var oobRunner: OobRunner? = null

  /** Resolves the [MultiProtocolSecureChannel] for a reconnect with [deviceId] and [challenge]. */
  fun resolveReconnect(deviceId: UUID, challenge: ByteArray) {
    logd(TAG, "Resolving channel as a reconnect.")
    isReconnect = true
    this.deviceId = deviceId
    this.challenge = challenge
    addProtocolDevice(protocolDevice)
  }

  /** Resolves the [MultiProtocolSecureChannel] for association with supported [OobChannel]s. */
  fun resolveAssociation(oobRunner: OobRunner) {
    logd(TAG, "Resolving channel with a new association.")
    isReconnect = false
    this.oobRunner = oobRunner
    addProtocolDevice(protocolDevice)
  }

  /**
   * Adds a protocol device to resolve.
   *
   * This needs to be called when the remote device is connected on a new [ConnectionProtocol]
   * before a [MultiProtocolSecureChannel] has been established for the remote device.
   */
  fun addProtocolDevice(device: ProtocolDevice) {
    logd(TAG, "Registering listener for received data received.")
    val dataReceivedListener =
      object : IDataReceivedListener.Stub() {
        override fun onDataReceived(protocolId: String, data: ByteArray) {
          if (currentDevice.compareAndSet(null, device)) {
            processVersionMessage(data, device)
            return
          }
          logd(
            TAG,
            "Channel already resolving with connection ${currentDevice.get()?.protocolId}. " +
              "Ignoring data received from connection $protocolId.",
          )
        }
      }
    device.protocol.registerDataReceivedListener(device.protocolId, dataReceivedListener)
    protocolDevices[device] = dataReceivedListener
  }

  private fun processVersionMessage(message: ByteArray, device: ProtocolDevice) {
    val version =
      try {
        VersionExchange.parseFrom(message, ExtensionRegistryLite.getEmptyRegistry())
      } catch (e: InvalidProtocolBufferException) {
        onError("Received a malformed version exchange message.", e)
        return
      }
    if (
      version.minSupportedMessagingVersion > MAX_MESSAGING_VERSION ||
        version.maxSupportedMessagingVersion < MIN_MESSAGING_VERSION
    ) {
      onError("Device does not have a compatible messaging version.")
      return
    }
    if (
      version.minSupportedSecurityVersion > MAX_SECURITY_VERSION ||
        version.maxSupportedSecurityVersion < MIN_SECURITY_VERSION
    ) {
      onError("Device does not have a compatible messaging version.")
      return
    }

    val resolvedMessageVersion = min(MAX_MESSAGING_VERSION, version.maxSupportedMessagingVersion)
    val resolvedSecurityVersion = min(MAX_SECURITY_VERSION, version.maxSupportedSecurityVersion)
    logd(
      TAG,
      "Resolved to messaging version $resolvedMessageVersion and security version " +
        "$resolvedSecurityVersion.",
    )
    val carVersion =
      VersionExchange.newBuilder()
        .setMaxSupportedMessagingVersion(MAX_MESSAGING_VERSION)
        .setMinSupportedMessagingVersion(MIN_MESSAGING_VERSION)
        .setMaxSupportedSecurityVersion(MAX_SECURITY_VERSION)
        .setMinSupportedSecurityVersion(MIN_SECURITY_VERSION)
        .build()
    device.protocol.sendData(device.protocolId, carVersion.toByteArray(), /* callback= */ null)

    resolveStream(device)
  }

  private fun resolveStream(device: ProtocolDevice) {
    protocolDevices.remove(device)?.let { listener ->
      device.protocol.unregisterDataReceivedListener(device.protocolId, listener)
    }
    val stream = streamFactory.createProtocolStream(device)
    if (isReconnect && device.protocol.isDeviceVerificationRequired) {
      verifyDevice(stream)
      return
    }
    resolveChannel(stream)
  }

  private fun verifyDevice(stream: ProtocolStream) {
    val id = deviceId
    if (id == null) {
      onError("Device verification failed, device id is null.")
      return
    }
    val expectedChallenge = challenge
    if (expectedChallenge == null) {
      onError("Device verification failed, expected challenge is null.")
      return
    }
    stream.protocolDisconnectListener =
      object : ProtocolStream.ProtocolDisconnectListener {
        override fun onProtocolDisconnected() {
          onError("Failed to resolve channel, protocol disconnected during device verification.")
        }
      }
    stream.messageReceivedListener =
      object : ProtocolStream.MessageReceivedListener {
        override fun onMessageReceived(deviceMessage: DeviceMessage) {
          if (deviceMessage.operationType != ENCRYPTION_HANDSHAKE) {
            val error =
              "Expecting message with operation type ${ENCRYPTION_HANDSHAKE.name} " +
                "but received message with operation type ${deviceMessage.operationType.name}."
            onError(error)
            return
          }
          val message = deviceMessage.message
          val challengeResponse = message.copyOf(expectedChallenge.size)
          val deviceChallenge = message.copyOfRange(expectedChallenge.size, message.size)
          if (!expectedChallenge.contentEquals(challengeResponse)) {
            onError("Failed to resolve channel, received invalid challenge.")
            return
          }
          logd(TAG, "Responding to challenge.")
          val deviceChallengeResponse = runBlocking {
            storage.hashWithChallengeSecret(id.toString(), deviceChallenge)
          }
          if (deviceChallengeResponse == null) {
            onError("Failed to generate challenge response.")
            return
          }
          val challengeResponseMessage =
            DeviceMessage.createOutgoingMessage(
              /* recipient= */ null,
              /* isMessageEncrypted= */ false,
              ENCRYPTION_HANDSHAKE,
              deviceChallengeResponse,
            )
          stream.sendMessage(challengeResponseMessage)
          resolveChannel(stream)
        }
      }
  }

  private fun resolveChannel(stream: ProtocolStream) {
    encryptionRunner.setIsReconnect(isReconnect)
    val channel =
      MultiProtocolSecureChannel(stream, storage, encryptionRunner, oobRunner, deviceId?.toString())
    protocolDevices.keys.forEach { channel.addStream(ProtocolStream(it)) }
    clearDataReceivedListeners()
    callback.onChannelResolved(channel)
  }

  private fun onError(message: String, exception: Exception? = null) {
    loge(TAG, message, exception)
    clearDataReceivedListeners()
    callback.onChannelResolutionError()
  }

  private fun clearDataReceivedListeners() {
    protocolDevices.forEach { (device, listener) ->
      device.protocol.unregisterDataReceivedListener(device.protocolId, listener)
    }
    protocolDevices.clear()
  }

  /** Callback for the result of channel resolving. */
  interface Callback {
    /** Invoked when the channel has been successfully resolved. */
    fun onChannelResolved(channel: MultiProtocolSecureChannel)

    /** Invoked when an error occurs during channel resolving. */
    fun onChannelResolutionError()
  }

  companion object {
    // copybara:strip_begin
    // LINT.IfChange
    // copybara:strip_end
    internal const val MIN_MESSAGING_VERSION = 3
    internal const val MAX_MESSAGING_VERSION = 3
    internal const val MIN_SECURITY_VERSION = 4
    internal const val MAX_SECURITY_VERSION = 4
    // copybara:strip_begin
    // LINT.ThenChange(//depot/google3/third_party/java_src/android_libs/connecteddevice/java/com/google/android/connecteddevice/res/values/config.xml)
    // copybara:strip_end
    private const val TAG = "ChannelResolver"
  }
}
