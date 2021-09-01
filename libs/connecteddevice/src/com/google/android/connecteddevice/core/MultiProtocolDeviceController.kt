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
package com.google.android.connecteddevice.core

import androidx.annotation.VisibleForTesting
import com.google.android.connecteddevice.api.IAssociationCallback
import com.google.android.connecteddevice.connection.ChannelResolver
import com.google.android.connecteddevice.connection.MultiProtocolSecureChannel
import com.google.android.connecteddevice.connection.MultiProtocolSecureChannel.ChannelError
import com.google.android.connecteddevice.connection.MultiProtocolSecureChannel.ShowVerificationCodeListener
import com.google.android.connecteddevice.connection.ProtocolStream
import com.google.android.connecteddevice.core.DeviceController.Callback
import com.google.android.connecteddevice.model.AssociatedDevice
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.model.DeviceMessage.OperationType
import com.google.android.connecteddevice.model.Errors
import com.google.android.connecteddevice.oob.OobRunner
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.ConnectionProtocol.ConnectChallenge
import com.google.android.connecteddevice.transport.ConnectionProtocol.DiscoveryCallback
import com.google.android.connecteddevice.transport.ProtocolDevice
import com.google.android.connecteddevice.util.ByteUtils
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logw
import com.google.android.connecteddevice.util.ThreadSafeCallbacks
import com.google.android.connecteddevice.util.aliveOrNull
import java.security.InvalidParameterException
import java.util.Objects
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The controller to manage all the connected devices and connected protocols of each connected
 * device.
 *
 * It is responsible for:
 * 1. Establish a connection: Handle Association/Reconnection request and communicate with
 * [ConnectionProtocol].
 * 2. Maintain the connection: Manage all connected devices with [ConnectedRemoteDevice]; Dispatch
 * [Callback.onMessageReceived] callback; Enable to disconnect specific devices.
 *
 * @property protocols List of supported protocols.
 * @property storage Storage necessary to generate reconnect challenge.
 * @property callbackExecutor Executor on which callbacks are executed.
 */
class MultiProtocolDeviceController
@JvmOverloads
constructor(
  private val protocols: Set<ConnectionProtocol>,
  private val storage: ConnectedDeviceStorage,
  private val oobRunner: OobRunner,
  private val callbackExecutor: Executor = Executors.newSingleThreadExecutor()
) : DeviceController {
  private val connectedRemoteDevices = CopyOnWriteArraySet<ConnectedRemoteDevice>()

  private val callbacks = ThreadSafeCallbacks<Callback>()
  private var associationPendingDevice: ConnectedRemoteDevice? = null

  /**
   * The out of band verification code get from the [EncryptionRunner], will be set during out of
   * band association.
   */
  @VisibleForTesting internal var oobCode: ByteArray? = null

  private val associatedDevices = CopyOnWriteArrayList<AssociatedDevice>()
  private val driverDevices = CopyOnWriteArrayList<AssociatedDevice>()

  override val connectedDevices: List<ConnectedDevice>
    get() {
      val devices = mutableListOf<ConnectedDevice>()
      for (device in connectedRemoteDevices) {
        val associatedDevice =
          associatedDevices.firstOrNull { it.deviceId == device.deviceId?.toString() } ?: continue
        val belongsToDriver = driverDevices.any { it.deviceId == associatedDevice.deviceId }
        val hasSecureChannel = device.secureChannel != null
        devices.add(
          ConnectedDevice(
            associatedDevice.deviceId,
            associatedDevice.deviceName,
            belongsToDriver,
            hasSecureChannel
          )
        )
      }

      return devices
    }

  init {
    callbackExecutor.execute {
      associatedDevices.addAll(storage.allAssociatedDevices)
      driverDevices.addAll(storage.activeUserAssociatedDevices)
    }

    // TODO(b/192656006) Add registration for updates to associated devices to keep in sync
  }

  override fun start() {
    val associatedDevices = storage.activeUserAssociatedDevices
    for (device in associatedDevices) {
      if (device.isConnectionEnabled) {
        initiateConnectionToDevice(UUID.fromString(device.deviceId))
      }
    }
  }

  override fun reset() {
    for (protocol in protocols) {
      protocol.reset()
    }
    oobCode = null
  }

  override fun initiateConnectionToDevice(deviceId: UUID) {
    logd(TAG, "Start listening for device with id: $deviceId")
    // Generate {challenge, concatenated challenge to advertise}.
    val challenge = generateChallenge(deviceId)
    if (challenge == null) {
      loge(TAG, "Unable to create connect challenge. Aborting connection.")
      return
    }
    for (protocol in protocols) {
      val discoveryCallback = generateConnectionDiscoveryCallback(deviceId, protocol, challenge)
      protocol.startConnectionDiscovery(deviceId, challenge, discoveryCallback)
    }
  }

  override fun startAssociation(nameForAssociation: String, callback: IAssociationCallback) {
    logd(TAG, "Start association with name $nameForAssociation")
    oobCode = null
    for (protocol in protocols) {
      val discoveryCallback =
        generateAssociationDiscoveryCallback(protocol, callback, nameForAssociation)
      protocol.startAssociationDiscovery(nameForAssociation, discoveryCallback)
    }
  }

  override fun notifyVerificationCodeAccepted() {
    if (associationPendingDevice == null) {
      loge(TAG, "Null connected device found when out-of-band confirmation received.")
      return
    }
    val secureChannel = associationPendingDevice?.secureChannel
    if (secureChannel == null) {
      loge(
        TAG,
        "Null SecureChannel found for the current connected device " +
          "when out-of-band confirmation received."
      )
      return
    }
    secureChannel.notifyVerificationCodeAccepted()
    val uniqueId = storage.uniqueId
    logd(TAG, "Sending car's device id of $uniqueId to device.")
    val deviceMessage =
      DeviceMessage(
        /* recipient= */ null,
        true,
        OperationType.ENCRYPTION_HANDSHAKE,
        ByteUtils.uuidToBytes(uniqueId)
      )
    secureChannel.sendClientMessage(deviceMessage)
  }

  /**
   * Returns a [ConnectedRemoteDevice] with matching device id if it is currently connected or
   * `null` otherwise.
   */
  @VisibleForTesting
  internal fun getConnectedDevice(deviceId: UUID): ConnectedRemoteDevice? {
    return connectedRemoteDevices.firstOrNull { it.deviceId == deviceId }
  }

  /**
   * Returns a [ConnectedRemoteDevice] with matching association callback if it is currently
   * connected or `null` otherwise.
   */
  @VisibleForTesting
  internal fun getConnectedDevice(callback: IAssociationCallback): ConnectedRemoteDevice? {
    return connectedRemoteDevices.firstOrNull { it.callback == callback }
  }

  override fun sendMessage(deviceId: UUID, message: DeviceMessage): Boolean {
    val device = getConnectedDevice(deviceId)
    if (device == null) {
      logw(TAG, "Attempted to send message to disconnected device $deviceId. Ignored.")
      return false
    }
    logd(TAG, "Writing ${message.message.size} bytes to $deviceId.")
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

  override fun isReadyToSendMessage(deviceId: UUID): Boolean =
    getConnectedDevice(deviceId)?.secureChannel != null

  override fun disconnectDevice(deviceId: UUID) {
    logd(TAG, "Disconnecting device with id $deviceId.")
    for (protocol in protocols) {
      protocol.stopConnectionDiscovery(deviceId)
    }
    val device = getConnectedDevice(deviceId)
    if (device == null) {
      loge(TAG, "Attempted to disconnect an unrecognized device. Ignored.")
      return
    }
    connectedRemoteDevices.remove(device)
    for ((protocol, protocolId) in device.protocolDevices) {
      protocol.disconnectDevice(protocolId)
    }

    invokeCallbacksWithDevice(device) { connectedDevice, callback ->
      callback.onDeviceDisconnected(connectedDevice)
    }
  }

  /** Stop the association process with any device. */
  override fun stopAssociation() {
    for (protocol in protocols) {
      protocol.stopAssociationDiscovery()
    }

    associationPendingDevice?.protocolDevices?.forEach {
      it.protocol.disconnectDevice(it.protocolId)
    }
    associationPendingDevice = null
    oobRunner.reset()
  }

  override fun registerCallback(callback: Callback, executor: Executor) {
    logd(TAG, "Registering a new callback.")
    callbacks.add(callback, executor)
  }

  override fun unregisterCallback(callback: Callback) {
    logd(TAG, "Unregistering a callback.")
    callbacks.remove(callback)
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
    val zeroPadded =
      ByteUtils.concatByteArrays(salt, ByteArray(TOTAL_AD_DATA_BYTES - SALT_BYTES)) ?: return null
    val challenge = storage.hashWithChallengeSecret(id.toString(), zeroPadded) ?: return null
    return ConnectChallenge(challenge, salt)
  }

  /**
   * Generate the [DiscoveryCallback] for reconnecting to device [deviceId] with reconnect
   * [challenge].
   */
  private fun generateConnectionDiscoveryCallback(
    deviceId: UUID,
    protocol: ConnectionProtocol,
    challenge: ConnectChallenge
  ) =
    object : DiscoveryCallback {
      override fun onDeviceConnected(protocolId: String) {
        logd(TAG, "New connection protocol connected, id: $protocolId, protocol: $protocol")
        protocol.registerDeviceDisconnectedListener(
          protocolId,
          generateDeviceDisconnectedListener(callback = null, deviceId, protocol),
          callbackExecutor
        )
        val protocolDevice = ProtocolDevice(protocol, protocolId)
        var device = getConnectedDevice(deviceId)
        if (device != null) {
          logd(
            TAG,
            "Certain connect protocol already exist, add id $protocolId to current " +
              "connected remote device."
          )
          device.secureChannel?.addStream(ProtocolStream(protocolDevice))
          device.channelResolver?.addProtocolDevice(protocolDevice)
          device.protocolDevices.add(protocolDevice)
          return
        }
        device =
          ConnectedRemoteDevice().apply {
            this.deviceId = deviceId
            protocolDevices.add(protocolDevice)
            channelResolver = generateChannelResolver(protocolDevice, device = this)
          }
        device.channelResolver?.resolveReconnect(deviceId, challenge.challenge)
        connectedRemoteDevices.add(device)

        invokeCallbacksWithDevice(device) { connectedDevice, callback ->
          callback.onDeviceConnected(connectedDevice)
        }
      }

      override fun onDiscoveryStartedSuccessfully() {
        logd(TAG, "Connection discovery started successfully.")
      }

      override fun onDiscoveryFailedToStart() {
        loge(TAG, "Connection discovery failed to start.")
      }
    }

  /**
   * Generate the [DiscoveryCallback] for associating with device with advertisement name
   * [nameForAssociation] response will be patched through the [associationCallback].
   */
  private fun generateAssociationDiscoveryCallback(
    protocol: ConnectionProtocol,
    associationCallback: IAssociationCallback,
    nameForAssociation: String
  ) =
    object : DiscoveryCallback {
      override fun onDeviceConnected(protocolId: String) {
        logd(TAG, "New connection protocol connected, id: $protocolId, protocol: $protocol")
        val protocolDevice = ProtocolDevice(protocol, protocolId)
        protocol.registerDeviceDisconnectedListener(
          protocolId,
          generateDeviceDisconnectedListener(associationCallback, deviceId = null, protocol),
          callbackExecutor
        )
        // The channel only needs to be resolved once for all protocols connected to one remote
        // device.
        var device = getConnectedDevice(associationCallback)
        if (device != null) {
          logd(
            TAG,
            "Certain connect protocol already exist, add id to current connected remote device."
          )
          device.secureChannel?.addStream(ProtocolStream(protocolDevice))
          device.channelResolver?.addProtocolDevice(protocolDevice)
          device.protocolDevices.add(protocolDevice)
          return
        }
        device =
          ConnectedRemoteDevice().apply {
            protocolDevices.add(protocolDevice)
            callback = associationCallback
            channelResolver =
              generateChannelResolver(protocolDevice, device = this, associationCallback)
          }
        device.channelResolver?.resolveAssociation(oobRunner)
        connectedRemoteDevices.add(device)
        associationPendingDevice = device
      }

      override fun onDiscoveryStartedSuccessfully() {
        // TODO(b/197692697): Pass the OOB data to UI
        oobRunner.generateOobData()
        associationCallback.aliveOrNull()?.let {
          logd(TAG, "Association started successfully with name $nameForAssociation")
          it.onAssociationStartSuccess(nameForAssociation)
        }
      }

      override fun onDiscoveryFailedToStart() {
        associationCallback.aliveOrNull()?.onAssociationStartFailure()
          ?: run {
            loge(
              TAG,
              "Association callback binder has died. Unable to issue discovery failed callback."
            )
          }
      }
    }

  private fun createShowVerificationCodeListener(callback: IAssociationCallback) =
    object : ShowVerificationCodeListener {
      override fun showVerificationCode(code: String) {
        callback.aliveOrNull()?.onVerificationCodeAvailable(code)
          ?: run {
            loge(TAG, "Association callback binder has died. Unable to display verification code.")
          }
      }
    }

  private fun generateChannelResolver(
    protocolDevice: ProtocolDevice,
    device: ConnectedRemoteDevice,
    associationCallback: IAssociationCallback? = null
  ) =
    ChannelResolver(
      protocolDevice,
      storage,
      object : ChannelResolver.Callback {
        override fun onChannelResolved(channel: MultiProtocolSecureChannel) {
          logd(TAG, "Resolved channel successfully for device $device.")
          channel.callback = generateSecureChannelCallback(device)
          associationCallback?.let {
            channel.showVerificationCodeListener = createShowVerificationCodeListener(it)
          }
          device.secureChannel = channel
          device.channelResolver = null
        }

        override fun onChannelResolutionError() {
          loge(TAG, "Failed to resolve channel, disconnecting device $device.")
          for ((protocol, protocolId) in device.protocolDevices) {
            protocol.disconnectDevice(protocolId)
          }
          device
            .callback
            ?.aliveOrNull()
            ?.onAssociationError(Errors.DEVICE_ERROR_INVALID_CHANNEL_STATE)
            ?: run {
              loge(
                TAG,
                "Association callback binder has died. Unable to issue association error callback."
              )
            }
        }
      }
    )

  private fun generateDeviceDisconnectedListener(
    callback: IAssociationCallback?,
    deviceId: UUID?,
    protocol: ConnectionProtocol,
  ) =
    object : ConnectionProtocol.DeviceDisconnectedListener {
      override fun onDeviceDisconnected(protocolId: String) {
        logd(TAG, "Remote connect protocol disconnected, id: $protocolId, protocol: $protocol")
        val device =
          when {
            callback != null -> getConnectedDevice(callback)
            deviceId != null -> getConnectedDevice(deviceId)
            else -> null
          }
        if (device == null) {
          loge(TAG, "Unrecognized device disconnected. Ignoring.")
          return
        }
        for (protocolDevice in device.protocolDevices) {
          if (protocolDevice.protocol == protocol && protocolDevice.protocolId == protocolId) {
            device.protocolDevices.remove(protocolDevice)
            break
          }
        }
        if (device.protocolDevices.isEmpty()) {
          onLastProtocolDisconnected(device)
          if (associationPendingDevice == device) {
            callback
              ?.aliveOrNull()
              ?.onAssociationError(Errors.DEVICE_ERROR_UNEXPECTED_DISCONNECTION)
            associationPendingDevice = null
          }
        } else {
          logd(
            TAG,
            "There are still ${device.protocolDevices.size} connected protocols for $deviceId. " +
              "A disconnect callback will not be issued."
          )
        }
      }
    }

  private fun onLastProtocolDisconnected(device: ConnectedRemoteDevice) {
    logd(
      TAG,
      "Device ${device.deviceId} has no more protocols connected. Issuing disconnect callback."
    )
    connectedRemoteDevices.remove(device)

    invokeCallbacksWithDevice(device) { connectedDevice, callback ->
      callback.onDeviceDisconnected(connectedDevice)
    }
    device.deviceId?.let { disconnectedDeviceId ->
      callbackExecutor.execute {
        val associatedDevice = storage.getAssociatedDevice(disconnectedDeviceId.toString())
        if (associatedDevice == null) {
          loge(
            TAG,
            "Unable to find recently disconnected device $disconnectedDeviceId. " +
              "Cannot proceed."
          )
          return@execute
        }
        if (!associatedDevice.isConnectionEnabled) {
          logd(TAG, "$disconnectedDeviceId is disabled and will not attempt to reconnect.")
          return@execute
        }
        logd(TAG, "Attempting to reconnect to recently disconnected device $disconnectedDeviceId.")
        initiateConnectionToDevice(disconnectedDeviceId)
      }
    }
  }

  private fun generateSecureChannelCallback(device: ConnectedRemoteDevice) =
    object : MultiProtocolSecureChannel.Callback {
      override fun onOobVerificationCodeAvailable(code: ByteArray) {
        oobCode = code
      }

      override fun onOobVerificationCodeReceived(code: ByteArray) {
        confirmOobVerificationCode(code, device)
      }

      override fun onSecureChannelEstablished() {
        logd(
          TAG,
          "Notifying callbacks that a secure channel has been established with " +
            "${device.deviceId}."
        )
        invokeCallbacksWithDevice(device) { connectedDevice, callback ->
          callback.onSecureChannelEstablished(connectedDevice)
        }
      }

      override fun onEstablishSecureChannelFailure(error: ChannelError) {
        device.callback?.onAssociationError(error.ordinal)
      }

      override fun onMessageReceived(deviceMessage: DeviceMessage) {
        handleSecureChannelMessage(deviceMessage, device)
      }

      override fun onMessageReceivedError(error: MultiProtocolSecureChannel.MessageError) {
        loge(TAG, "Error while receiving message.")
        device.callback?.onAssociationError(Errors.DEVICE_ERROR_INVALID_HANDSHAKE)
      }
    }

  @VisibleForTesting
  internal fun encryptAndSendOobVerificationCode(code: ByteArray, device: ConnectedRemoteDevice) {
    val encryptedCode: ByteArray =
      try {
        oobRunner.encryptData(code)
      } catch (e: Exception) {
        loge(TAG, "Encryption failed for verification code exchange.", e)
        device.callback?.aliveOrNull()?.onAssociationError(Errors.DEVICE_ERROR_INVALID_VERIFICATION)
        return
      }
    device.secureChannel?.sendOobEncryptedCode(encryptedCode)
  }

  @VisibleForTesting
  internal fun confirmOobVerificationCode(encryptedCode: ByteArray, device: ConnectedRemoteDevice) {
    val decryptedCode: ByteArray =
      try {
        oobRunner.decryptData(encryptedCode)
      } catch (e: Exception) {
        loge(TAG, "Decryption failed for verification code exchange", e)
        device.callback?.aliveOrNull()?.onAssociationError(Errors.DEVICE_ERROR_INVALID_VERIFICATION)
        return
      }
    val code = oobCode
    if (code == null || !decryptedCode.contentEquals(code)) {
      loge(TAG, "Exchanged verification codes do not match. Notify callback of failure.")
      device.callback?.aliveOrNull()?.onAssociationError(Errors.DEVICE_ERROR_INVALID_VERIFICATION)
      return
    }
    encryptAndSendOobVerificationCode(code, device)
    notifyVerificationCodeAccepted()
  }

  @VisibleForTesting
  internal fun handleSecureChannelMessage(
    deviceMessage: DeviceMessage,
    device: ConnectedRemoteDevice
  ) {
    if (device.deviceId == null) {
      handleAssociationMessage(deviceMessage, device)
      return
    }

    logd(TAG, "Received new message from ${device.deviceId}.")
    invokeCallbacksWithDevice(device) { connectedDevice, callback ->
      callback.onMessageReceived(connectedDevice, deviceMessage)
    }
  }

  private fun handleAssociationMessage(
    deviceMessage: DeviceMessage,
    device: ConnectedRemoteDevice
  ) {
    val deviceId = ByteUtils.bytesToUUID(deviceMessage.message.copyOf(DEVICE_ID_BYTES))
    if (deviceId == null) {
      loge(TAG, "Received invalid device id. Aborting.")
      device.callback?.onAssociationError(ChannelError.CHANNEL_ERROR_INVALID_DEVICE_ID.ordinal)
      return
    }
    device.deviceId = deviceId
    logd(TAG, "Received device id and secret from $deviceId.")
    try {
      storage.saveChallengeSecret(
        deviceId.toString(),
        deviceMessage.message.copyOfRange(DEVICE_ID_BYTES, deviceMessage.message.size)
      )
    } catch (e: InvalidParameterException) {
      loge(TAG, "Error saving challenge secret.", e)
      device.callback?.onAssociationError(ChannelError.CHANNEL_ERROR_INVALID_ENCRYPTION_KEY.ordinal)
      return
    }
    device.secureChannel?.setDeviceIdDuringAssociation(deviceId)
    val associatedDevice =
      AssociatedDevice(
        deviceId.toString(),
        /* deviceAddress= */ "",
        /* deviceName= */ null,
        /* isConnectionEnabled= */ true
      )
    storage.addAssociatedDeviceForActiveUser(associatedDevice)
    driverDevices.add(associatedDevice)
    associationPendingDevice = null
    device.callback?.onAssociationCompleted()
    invokeCallbacksWithDevice(device) { connectedDevice, callback ->
      callback.onDeviceConnected(connectedDevice)
    }
    invokeCallbacksWithDevice(device) { connectedDevice, callback ->
      callback.onSecureChannelEstablished(connectedDevice)
    }
  }

  /**
   * Convert provided [device] to a [ConnectedDevice] and invoke [onCallback] for each registered
   * [Callback]. Note: No callbacks will be invoked if device conversion fails.
   */
  private fun invokeCallbacksWithDevice(
    device: ConnectedRemoteDevice,
    onCallback: (ConnectedDevice, Callback) -> Unit
  ) {
    val connectedDevice = device.toConnectedDevice(driverDevices)
    if (connectedDevice == null) {
      loge(TAG, "Unable to convert to connected device. Callbacks were not invoked!")
      return
    }
    callbacks.invoke { onCallback(connectedDevice, it) }
  }

  /** Container class to hold information about a connected device. */
  internal data class ConnectedRemoteDevice(
    val protocolDevices: CopyOnWriteArraySet<ProtocolDevice> = CopyOnWriteArraySet()
  ) {
    var deviceId: UUID? = null
    var secureChannel: MultiProtocolSecureChannel? = null
    var callback: IAssociationCallback? = null
    var name: String? = null
    var channelResolver: ChannelResolver? = null

    /** Returns the [ConnectedDevice] equivalent or `null` if the conversion failed. */
    fun toConnectedDevice(driverDevices: List<AssociatedDevice>): ConnectedDevice? {
      return deviceId?.let {
        val belongsToDriver = driverDevices.any { device -> device.deviceId == it.toString() }
        val hasSecureChannel = secureChannel != null
        ConnectedDevice(it.toString(), name, belongsToDriver, hasSecureChannel)
      }
    }

    override fun equals(other: Any?): Boolean =
      other is ConnectedRemoteDevice && deviceId == other.deviceId

    override fun hashCode(): Int {
      return Objects.hashCode(deviceId)
    }
  }
  companion object {
    private const val TAG = "MultiProtocolDeviceController"
    private const val SALT_BYTES = 8
    private const val TOTAL_AD_DATA_BYTES = 16
    private const val DEVICE_ID_BYTES = 16
  }
}
