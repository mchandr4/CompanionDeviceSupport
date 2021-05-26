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
import com.google.android.connecteddevice.connection.DeviceController.Callback
import com.google.android.connecteddevice.connection.DeviceController.ConnectedRemoteDevice
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.model.Errors
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.ConnectionProtocol.ConnectChallenge
import com.google.android.connecteddevice.transport.ConnectionProtocol.DeviceCallback
import com.google.android.connecteddevice.transport.ConnectionProtocol.DiscoveryCallback
import com.google.android.connecteddevice.util.ByteUtils
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logw
import com.google.android.connecteddevice.util.ThreadSafeCallbacks
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The controller to manage all the connected devices and connected protocols of each connected
 * device.
 *
 * It is responsible for:
 * 1. Establish a connection: Handle Association/Reconnection request from [ConnectedDeviceManager]
 * and communicate with [ConnectionProtocol].
 * 2. Maintain the connection: Manage all connected devices with [ConnectedRemoteDevice]; Dispatch
 * [Callback.onMessageReceived] callback; Enable to disconnect specific devices.
 *
 * @property protocols List of supported protocols.
 * @property storage Storage necessary to generate reconnect challenge.
 */
class DeviceController(
  private val protocols: Set<ConnectionProtocol>,
  private val storage: ConnectedDeviceStorage,
) {
  internal val connectedDevices = CopyOnWriteArraySet<ConnectedRemoteDevice>()

  @VisibleForTesting
  internal var callbackExecutor: Executor = Executors.newSingleThreadExecutor()
  private val callbacks = ThreadSafeCallbacks<Callback>()

  /**
   * Disconnect and clear all the protocols
   */
  fun stop() {
    for (protocol in protocols) {
      protocol.reset()
    }
  }

  /** Start to connect to associated devices  */
  fun initiateConnectionToDevice(deviceId: UUID) {
    logd(TAG, "Start listening for device with id: $deviceId")
    // Generate {challenge, concatencated challenge to advertise}.
    val challenge = generateChallenge(deviceId)
    if (challenge == null) {
      loge(TAG, "Unable to create connect challenge. Aborting connection.")
      return
    }
    for (protocol in protocols) {
      val discoveryCallback = generateDiscoveryCallback(
        associationCallback = null,
        deviceId,
        nameForAssociation = null,
        protocol,
        challenge.challenge
      )
      protocol.startConnectionDiscovery(deviceId, challenge, discoveryCallback)
    }
  }

  /** Start the association with a new device  */
  fun startAssociation(nameForAssociation: String, callback: AssociationCallback) {
    logd(TAG, "Start association with name $nameForAssociation")
    for (protocol in protocols) {
      val discoveryCallback = generateDiscoveryCallback(
        callback,
        deviceId = null,
        nameForAssociation,
        protocol,
        challenge = null
      )
      protocol.startAssociationDiscovery(nameForAssociation, discoveryCallback)
    }
  }

  /** Notify that the user has accepted a pairing code or other out-of-band confirmation. */
  fun notifyOutOfBandAccepted(deviceId: UUID) {
    if (getConnectedDevice(deviceId) == null) {
      loge(TAG, "Null connected device found when out-of-band confirmation received.")
      return
    }
    val secureChannel = getConnectedDevice(deviceId)?.secureChannel
    if (secureChannel == null) {
      loge(
        TAG,
        "Null SecureChannel found for the current connected device " +
          "when out-of-band confirmation received."
      )
      return
    }
    if (secureChannel is AssociationSecureChannel) {
      secureChannel.notifyOutOfBandAccepted()
      return
    }
    loge(TAG, "Notified out of band accepted during a non-association process.")
  }

  /**
   * Returns a [ConnectedRemoteDevice] with matching device id if it is currently connected or
   * `null` otherwise.
   */
  private fun getConnectedDevice(deviceId: UUID): ConnectedRemoteDevice? {
    return connectedDevices.firstOrNull { it.deviceId == deviceId }
  }

  /**
   * Returns a [ConnectedRemoteDevice] with matching association callback if it is currently
   * connected or `null` otherwise.
   */
  private fun getConnectedDevice(callback: AssociationCallback): ConnectedRemoteDevice? {
    return connectedDevices.firstOrNull { it.callback == callback }
  }

  /**
   * Send the [message] to a connected device with id [deviceId] and returns `true` if the send was
   * successful.
   *
   * If `false` is returned, then it could mean that the given device was not currently connected
   * or is waiting for its secure channel to be set up. [isReadyToSendMessage] should return
   * `true` for the given `deviceId` before calling this method with the same `deviceId`.
   */
  fun sendMessage(deviceId: UUID, message: DeviceMessage): Boolean {
    val device = getConnectedDevice(deviceId)
    if (device == null) {
      logw(TAG, "Attempted to send message to disconnected device $deviceId. Ignored.")
      return false
    }
    logd(TAG, "Writing ${message.message?.size} bytes to $deviceId.")
    if (device.secureChannel == null) {
      logw(
        TAG,
        "Attempted to send message to device $deviceId when secure channel is not " +
          "established. Ignored."
      )
      return false
    }
    device.secureChannel?.sendClientMessage(message)
    return true
  }

  /**
   * Returns `true` if a secure channel has been established for device with id [deviceId]. A value
   * of `true` means that [sendMessage] can be called for the device.
   */
  fun isReadyToSendMessage(deviceId: UUID): Boolean =
    getConnectedDevice(deviceId)?.secureChannel != null

  /** Disconnect the provided device from this controller.  */
  fun disconnectDevice(deviceId: UUID) {
    logd(TAG, "Disconnect device with id $deviceId")
    val device = getConnectedDevice(deviceId)
    if (device == null) {
      loge(TAG, "Try to disconnect an unrecognized device, ignored.")
      return
    }

    for ((protocol, protocolId) in device.protocolIdPairs) {
      protocol.disconnectDevice(protocolId)
    }
  }

  /** Register a [Callback] to be notified on the [Executor].  */
  fun registerCallback(callback: Callback, executor: Executor) {
    callbacks.add(callback, executor)
  }

  /**
   * Unregister a callback.
   *
   * @param callback The [Callback] to unregister.
   */
  fun unregisterCallback(callback: Callback) {
    callbacks.remove(callback)
  }

  /** Container class to hold information about a connected device.  */
  internal data class ConnectedRemoteDevice(
    val protocolIdPairs:
      CopyOnWriteArraySet<Pair<ConnectionProtocol, String>> = CopyOnWriteArraySet()
  ) {
    var deviceId: UUID? = null
    var secureChannel: SecureChannel? = null
    var callback: AssociationCallback? = null
    var name: String? = null
  }

  /**
   * Create challenge for connection advertisement.
   *
   * Process:
   *
   * 1. Generate random [SALT_BYTES] byte salt and zero-pad to [TOTAL_AD_DATA_BYTES] bytes.
   * 2. Hash with stored challenge secret to generate challenge.
   * 3. Return the challenge and salt.
   */
  private fun generateChallenge(id: UUID): ConnectChallenge? {
    val salt = ByteUtils.randomBytes(SALT_BYTES)
    val zeroPadded = ByteUtils.concatByteArrays(salt, ByteArray(TOTAL_AD_DATA_BYTES - SALT_BYTES))
      ?: return null
    val challenge = storage.hashWithChallengeSecret(id.toString(), zeroPadded) ?: return null
    return ConnectChallenge(challenge, salt)
  }

  /**
   *  Generate the [DiscoveryCallback] for device [deviceId] with advertisement name
   *  [nameForAssociation] and reconnect [challenge], response will be patched through the
   *  [associationCallback].
   */
  private fun generateDiscoveryCallback(
    associationCallback: AssociationCallback?,
    deviceId: UUID?,
    nameForAssociation: String?,
    protocol: ConnectionProtocol,
    challenge: ByteArray?,
  ): DiscoveryCallback {
    return object : DiscoveryCallback {
      override fun onDeviceConnected(protocolId: String) {
        logd(TAG, "New connection protocol connected, id: $protocolId, protocol: $protocol")
        protocol.registerCallback(
          protocolId,
          generateDeviceCallback(associationCallback, deviceId, protocol),
          callbackExecutor
        )
        // TODO(b/180743856): DeviceMessageStream uses protocol interface for communication
        // Each device only do version resolver for once
        var device = when {
          associationCallback != null -> getConnectedDevice(associationCallback)
          deviceId != null -> getConnectedDevice(deviceId)
          else -> null
        }

        if (device != null) {
          logd(
            TAG,
            "Certain connect protocol already exist, add id to current connected " +
              "remote device."
          )
          // TODO(b/180743223): Add the stream to current secure channel
          device.protocolIdPairs.add(Pair(protocol, protocolId))
          return
        }
        device = ConnectedRemoteDevice().apply {
          this.deviceId = deviceId
          protocolIdPairs.add(Pair(protocol, protocolId))
          callback = associationCallback
        }
        connectedDevices.add(device)
        // Do version & capability exchange.
        // TODO(b/180743873): Expand ConnectionResolver to resolve a SecureChannel, register the
        //  secure channel callback and assign the secure channel to the device.
      }

      override fun onDiscoveryStartedSuccessfully() {
        associationCallback?.let {
          if (nameForAssociation == null) {
            loge(TAG, "Error get name for association, ignored")
            it.onAssociationStartFailure()
            return
          }
          logd(TAG, "Association started successfully with name $nameForAssociation")
          it.onAssociationStartSuccess(nameForAssociation)
        }
      }

      override fun onDiscoveryFailedToStart() {
        associationCallback?.onAssociationStartFailure()
      }

      override fun onDeviceNameRetrieved(protocolId: String, name: String) {
        val device = when {
          associationCallback != null -> getConnectedDevice(associationCallback)
          deviceId != null -> getConnectedDevice(deviceId)
          else -> null
        }
        device?.name = name
        storage.setAssociatedDeviceName(deviceId.toString(), name)
      }
    }
  }

  private fun generateDeviceCallback(
    callback: AssociationCallback?,
    deviceId: UUID?,
    protocol: ConnectionProtocol
  ): DeviceCallback {
    return object : DeviceCallback {
      override fun onDeviceDisconnected(protocolId: String) {
        logd(TAG, "Remote connect protocol disconnected, id: $protocolId, protocol: $protocol")
        val device = when {
          callback != null -> getConnectedDevice(callback)
          deviceId != null -> getConnectedDevice(deviceId)
          else -> null
        }
        if (device == null) {
          loge(TAG, "Unrecognized device disconnected, ignore")
          return
        }
        for (pair in device.protocolIdPairs) {
          if (pair.first == protocol && pair.second == protocolId) {
            device.protocolIdPairs.remove(pair)
            break
          }
        }
        if (device.protocolIdPairs.isEmpty()) {
          connectedDevices.remove(device)
          callbacks.invoke { callback -> callback.onDeviceDisconnected(device.deviceId.toString()) }
        }
      }

      override fun onDeviceMaxDataSizeChanged(protocolId: String, maxBytes: Int) {
        // No implementation
      }

      override fun onDataReceived(protocolId: String, data: ByteArray?) {
        // No implementation
      }
    }
  }

  private fun generateSecureChannelCallback(device: ConnectedRemoteDevice): SecureChannel.Callback {
    return object : SecureChannel.Callback {
      override fun onSecureChannelEstablished() {
        device.callback?.onAssociationCompleted(device.deviceId.toString())
        callbacks.invoke { it.onSecureChannelEstablished(device.deviceId.toString()) }
      }

      override fun onEstablishSecureChannelFailure(error: Int) {
        callbacks.invoke { it.onSecureChannelError(device.deviceId.toString()) }
        device.callback?.onAssociationError(error)
      }

      override fun onMessageReceived(deviceMessage: DeviceMessage) {
        callbacks.invoke { it.onMessageReceived(device.deviceId.toString(), deviceMessage) }
      }

      override fun onMessageReceivedError(exception: Exception?) {
        loge(TAG, "Error while receiving message.", exception)
        device.callback?.onAssociationError(Errors.DEVICE_ERROR_INVALID_HANDSHAKE)
      }

      override fun onDeviceIdReceived(deviceId: String) {
        device.deviceId = UUID.fromString(deviceId)
        callbacks.invoke { it.onDeviceConnected(deviceId) }
      }
    }
  }

  /** Callback for triggered events from [DeviceController].  */
  interface Callback {
    /**
     * Invoked when the device with the given [deviceId] has connected.
     *
     * @param deviceId The unique identifier of the connected device.
     */
    fun onDeviceConnected(deviceId: String)

    /**
     * Invoked when the device with the given [deviceId] has disconnected.
     *
     * @param deviceId The unique identifier of the device which has been disconnected.
     */
    fun onDeviceDisconnected(deviceId: String)

    /**
     * Triggered when the device with the given [deviceId] has established encryption for secure.
     * communication.
     *
     * @param deviceId Id of device that has established encryption.
     */
    fun onSecureChannelEstablished(deviceId: String)

    /**
     * Triggered when a new message is received from the device with the given [deviceId].
     *
     * @param deviceId Id of the device that sent the message.
     * @param message The message that was received.
     */
    fun onMessageReceived(deviceId: String, message: DeviceMessage)

    /**
     * Triggered if an error occurred when establishing the secure channel.
     *
     * @param deviceId Id of the device that experienced the error.
     */
    fun onSecureChannelError(deviceId: String)
  }

  companion object {
    private const val TAG = "DeviceController"
    private const val SALT_BYTES = 8
    private const val TOTAL_AD_DATA_BYTES = 16
  }
}
