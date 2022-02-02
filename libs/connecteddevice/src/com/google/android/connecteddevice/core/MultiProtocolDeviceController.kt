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

import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.os.ParcelUuid
import androidx.annotation.GuardedBy
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
import com.google.android.connecteddevice.model.StartAssociationResponse
import com.google.android.connecteddevice.oob.OobRunner
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.transport.ConnectChallenge
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.IDeviceDisconnectedListener
import com.google.android.connecteddevice.transport.IDiscoveryCallback
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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
 * @property enablePassenger Whether passenger devices automatically connect. When `true`, newly
 * associated devices will remain unclaimed by default.
 * @property callbackExecutor Executor on which callbacks are executed.
 */
class MultiProtocolDeviceController
@JvmOverloads
constructor(
  private val protocols: Set<ConnectionProtocol>,
  private val storage: ConnectedDeviceStorage,
  private val oobRunner: OobRunner,
  private val associationServiceUuid: UUID,
  private val enablePassenger: Boolean,
  private val callbackExecutor: Executor = Executors.newSingleThreadExecutor()
) : DeviceController {
  private val connectedRemoteDevices = ConcurrentHashMap<UUID, ConnectedRemoteDevice>()

  private val callbacks = ThreadSafeCallbacks<Callback>()

  @VisibleForTesting internal val associationPendingDeviceId = AtomicReference<UUID?>(null)

  private val lock = ReentrantLock()

  @GuardedBy("lock") private val associatedDevices = mutableListOf<AssociatedDevice>()
  @GuardedBy("lock") private val driverDevices = mutableListOf<AssociatedDevice>()

  private val storageCallback =
    object : ConnectedDeviceStorage.AssociatedDeviceCallback {
      override fun onAssociatedDeviceAdded(device: AssociatedDevice) {
        // Device population is handled locally when a new device is added.
      }

      override fun onAssociatedDeviceRemoved(device: AssociatedDevice) {
        logd(TAG, "An associated device has been removed. Repopulating devices from storage.")
        populateDevices()
      }

      override fun onAssociatedDeviceUpdated(device: AssociatedDevice) {
        logd(TAG, "An associated device has been updated. Repopulating devices from storage.")
        populateDevices()
      }
    }

  override val connectedDevices: List<ConnectedDevice>
    get() {
      lock.withLock {
        val devices = mutableListOf<ConnectedDevice>()
        for (device in connectedRemoteDevices.values) {
          val associatedDevice =
            associatedDevices.firstOrNull { it.deviceId == device.deviceId.toString() }
          if (associatedDevice == null) {
            logd(
              TAG,
              "Unable to find a device with id ${device.deviceId} in associated devices. Skipped " +
                "mapping."
            )
            continue
          }
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
    }

  init {
    storage.registerAssociatedDeviceCallback(storageCallback)
  }

  override fun start() {
    logd(TAG, "Starting controller and initiating connections with driver devices.")
    populateDevices()
    val driverDevices = storage.driverAssociatedDevices
    for (device in driverDevices) {
      if (device.isConnectionEnabled) {
        initiateConnectionToDevice(UUID.fromString(device.deviceId))
      }
    }
    if (!enablePassenger) {
      logd(TAG, "The passenger experience is disabled. Skipping discovery of passenger devices.")
      return
    }
    logd(TAG, "Initiating connections with passenger devices.")
    val passengerDevices = storage.passengerAssociatedDevices
    for (device in passengerDevices) {
      initiateConnectionToDevice(UUID.fromString(device.deviceId))
    }
  }

  override fun reset() {
    val callbackDevices = connectedDevices
    logd(TAG, "Resetting controller and disconnecting ${callbackDevices.size} devices.")
    // Current devices must be cleared prior to issuing callbacks to avoid race conditions.
    connectedRemoteDevices.clear()
    for (protocol in protocols) {
      protocol.reset()
    }
    associationPendingDeviceId.set(null)
    for (device in callbackDevices) {
      callbacks.invoke { it.onDeviceDisconnected(device) }
    }
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
      protocol.startConnectionDiscovery(ParcelUuid(deviceId), challenge, discoveryCallback)
    }
  }

  override fun startAssociation(
    nameForAssociation: String,
    callback: IAssociationCallback,
    identifier: UUID?
  ) {
    val associationUuid = identifier ?: associationServiceUuid
    logd(TAG, "Start association with name $nameForAssociation")
    val isAbleToStartAssociation = associationPendingDeviceId.compareAndSet(null, UUID.randomUUID())
    if (!isAbleToStartAssociation) {
      loge(TAG, "Attempted to start association when there is already an association in progress.")
      return
    }
    val startAssociationResponse =
      StartAssociationResponse(
        oobRunner.generateOobData(),
        ByteUtils.hexStringToByteArray(nameForAssociation),
        nameForAssociation
      )
    for (protocol in protocols) {
      val discoveryCallback =
        generateAssociationDiscoveryCallback(protocol, callback, startAssociationResponse)
      protocol.startAssociationDiscovery(
        nameForAssociation,
        ParcelUuid(associationUuid),
        discoveryCallback
      )
    }
  }

  override fun notifyVerificationCodeAccepted() {
    val deviceId = associationPendingDeviceId.get()
    if (deviceId == null) {
      loge(TAG, "Null connected device found when out-of-band confirmation received.")
      return
    }
    val secureChannel = connectedRemoteDevices[deviceId]?.secureChannel
    if (secureChannel == null) {
      loge(
        TAG,
        "Null SecureChannel found for the current connected device when out-of-band confirmation " +
          "received."
      )
      return
    }
    secureChannel.notifyVerificationCodeAccepted()
  }

  /**
   * Returns a [ConnectedRemoteDevice] with matching device id if it is currently connected or
   * `null` otherwise.
   */
  @VisibleForTesting
  internal fun getConnectedDevice(deviceId: UUID): ConnectedRemoteDevice? {
    return connectedRemoteDevices[deviceId]
  }

  override fun sendMessage(deviceId: UUID, message: DeviceMessage): Boolean {
    val device = connectedRemoteDevices[deviceId]
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
    connectedRemoteDevices[deviceId]?.secureChannel != null

  override fun disconnectDevice(deviceId: UUID) {
    logd(TAG, "Disconnecting device with id $deviceId.")
    for (protocol in protocols) {
      protocol.stopConnectionDiscovery(ParcelUuid(deviceId))
    }
    val device = connectedRemoteDevices.remove(deviceId)
    if (device == null) {
      loge(TAG, "Attempted to disconnect an unrecognized device. Ignored.")
      return
    }

    for ((protocol, protocolId) in device.protocolDevices) {
      protocol.disconnectDevice(protocolId)
    }

    invokeCallbacksWithDevice(device) { connectedDevice, callback ->
      callback.onDeviceDisconnected(connectedDevice)
    }
  }

  /** Stop the association process with any device. */
  override fun stopAssociation() {
    logd(TAG, "Stopping association.")
    oobRunner.reset()
    for (protocol in protocols) {
      protocol.stopAssociationDiscovery()
    }
    val pendingDeviceId = associationPendingDeviceId.getAndSet(null)
    if (pendingDeviceId == null) {
      logd(TAG, "Association was not in progress. No further action required.")
      return
    }
    val pendingDevice = connectedRemoteDevices.remove(pendingDeviceId)
    if (pendingDevice == null) {
      logw(
        TAG,
        "Unable to find a matching connected device matching the pending id. Nothing to disconnect."
      )
      return
    }
    for (protocolDevice in pendingDevice.protocolDevices) {
      protocolDevice.protocol.disconnectDevice(protocolDevice.protocolId)
    }
  }

  override fun registerCallback(callback: Callback, executor: Executor) {
    logd(TAG, "Registering a new callback.")
    callbacks.add(callback, executor)
  }

  override fun unregisterCallback(callback: Callback) {
    logd(TAG, "Unregistering a callback.")
    callbacks.remove(callback)
  }

  private fun populateDevices() {
    callbackExecutor.execute {
      while (true) {
        try {
          logd(TAG, "Populating associated devices from storage.")

          // Fetch devices prior to applying lock to reduce lock time.
          val driverOnlyDevices = storage.driverAssociatedDevices
          val allDevices = storage.allAssociatedDevices
          lock.withLock {
            associatedDevices.clear()
            associatedDevices.addAll(allDevices)
            driverDevices.clear()
            driverDevices.addAll(driverOnlyDevices)
          }
          logd(TAG, "Devices populated successfully.")
          break
        } catch (sqliteException: SQLiteCantOpenDatabaseException) {
          loge(
            TAG,
            "Caught transient exception while retrieving devices. Trying again.",
            sqliteException
          )
          try {
            Thread.sleep(ASSOCIATED_DEVICE_RETRY_MS)
          } catch (interrupted: InterruptedException) {
            loge(TAG, "Sleep interrupted.", interrupted)
            break
          }
        }
      }
    }
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
    object : IDiscoveryCallback.Stub() {
      override fun onDeviceConnected(protocolId: String) {
        logd(
          TAG,
          "New connection protocol connected for $deviceId. id: $protocolId, protocol: $protocol"
        )
        protocol.registerDeviceDisconnectedListener(
          protocolId,
          generateDeviceDisconnectedListener(deviceId, protocol)
        )
        val protocolDevice = ProtocolDevice(protocol, protocolId)
        val device =
          connectedRemoteDevices.compute(deviceId) { _, device ->
            if (device != null) {
              logd(
                TAG,
                "Certain connect protocol already exist, add id $protocolId to current " +
                  "connected remote device."
              )
              device.secureChannel?.addStream(ProtocolStream(protocolDevice))
              device.channelResolver?.addProtocolDevice(protocolDevice)
              device.protocolDevices.add(protocolDevice)
              return@compute device
            }

            ConnectedRemoteDevice(deviceId).apply {
              protocolDevices.add(protocolDevice)
              channelResolver = generateChannelResolver(protocolDevice, device = this)
              channelResolver?.resolveReconnect(deviceId, challenge.challenge)
            }
          }
            ?: return
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
   * Generate the [DiscoveryCallback] for associating with device with the [response] that will be
   * patched through the [associationCallback].
   */
  private fun generateAssociationDiscoveryCallback(
    protocol: ConnectionProtocol,
    associationCallback: IAssociationCallback,
    response: StartAssociationResponse
  ) =
    object : IDiscoveryCallback.Stub() {
      override fun onDeviceConnected(protocolId: String) {
        logd(TAG, "New connection protocol connected, id: $protocolId, protocol: $protocol")
        val protocolDevice = ProtocolDevice(protocol, protocolId)
        val pendingId = associationPendingDeviceId.get()
        if (pendingId == null) {
          loge(
            TAG,
            "Device connected for association when there was no association in progress. " +
              "Disconnecting."
          )
          protocol.disconnectDevice(protocolId)
          return
        }
        protocol.registerDeviceDisconnectedListener(
          protocolId,
          generateDeviceDisconnectedListener(pendingId, protocol)
        )
        // The channel only needs to be resolved once for all protocols connected to one remote
        // device.
        val newDevice =
          ConnectedRemoteDevice(pendingId).apply {
            protocolDevices.add(protocolDevice)
            callback = associationCallback
            channelResolver =
              generateChannelResolver(protocolDevice, device = this, associationCallback)
          }
        val existingDevice = connectedRemoteDevices.putIfAbsent(pendingId, newDevice)
        if (existingDevice != null) {
          logd(
            TAG,
            "Certain connect protocol already exist, add id to current connected remote device."
          )
          connectedRemoteDevices.compute(pendingId) { _, device ->
            device?.apply {
              secureChannel?.addStream(ProtocolStream(protocolDevice))
              channelResolver?.addProtocolDevice(protocolDevice)
              protocolDevices.add(protocolDevice)
            }
          }
          return
        }

        newDevice.channelResolver?.resolveAssociation(oobRunner)
      }

      override fun onDiscoveryStartedSuccessfully() {
        associationCallback.aliveOrNull()?.onAssociationStartSuccess(response)
          ?: run {
            loge(
              TAG,
              "Association callback binder has died. Unable to issue discovery started " +
                "successfully callback."
            )
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
    deviceId: UUID,
    protocol: ConnectionProtocol,
  ) =
    object : IDeviceDisconnectedListener.Stub() {
      override fun onDeviceDisconnected(protocolId: String) {
        logd(TAG, "Remote connect protocol disconnected, id: $protocolId, protocol: $protocol")
        connectedRemoteDevices.compute(deviceId) { deviceId, device ->
          if (device == null) {
            loge(TAG, "Unrecognized device disconnected. Ignoring.")
            return@compute null
          }
          for (protocolDevice in device.protocolDevices) {
            if (protocolDevice.protocol == protocol && protocolDevice.protocolId == protocolId) {
              device.protocolDevices.remove(protocolDevice)
              break
            }
          }
          if (device.protocolDevices.isEmpty()) {
            onLastProtocolDisconnected(device)
            if (associationPendingDeviceId.compareAndSet(deviceId, null)) {
              device
                .callback
                ?.aliveOrNull()
                ?.onAssociationError(Errors.DEVICE_ERROR_UNEXPECTED_DISCONNECTION)
            }
          } else {
            logd(
              TAG,
              "There are still ${device.protocolDevices.size} connected protocols for $deviceId. " +
                "A disconnect callback will not be issued."
            )
          }
          device
        }
      }
    }

  private fun onLastProtocolDisconnected(device: ConnectedRemoteDevice) {
    val disconnectedDeviceId = device.deviceId
    logd(
      TAG,
      "Device $disconnectedDeviceId has no more protocols connected. Issuing disconnect callback."
    )
    connectedRemoteDevices.remove(disconnectedDeviceId)

    invokeCallbacksWithDevice(device) { connectedDevice, callback ->
      callback.onDeviceDisconnected(connectedDevice)
    }
    callbackExecutor.execute {
      val associatedDevice = storage.getAssociatedDevice(disconnectedDeviceId.toString())
      if (associatedDevice == null) {
        loge(
          TAG,
          "Unable to find recently disconnected device $disconnectedDeviceId. " + "Cannot proceed."
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

  @VisibleForTesting
  internal fun generateSecureChannelCallback(device: ConnectedRemoteDevice) =
    object : MultiProtocolSecureChannel.Callback {
      override fun onSecureChannelEstablished() {
        if (associationPendingDeviceId.get() != null) {
          val uniqueId = storage.uniqueId
          logd(TAG, "Sending car's device id of $uniqueId to device.")
          val deviceMessage =
            DeviceMessage.createOutgoingMessage(
              /* recipient= */ null,
              true,
              OperationType.ENCRYPTION_HANDSHAKE,
              ByteUtils.uuidToBytes(uniqueId)
            )
          device.secureChannel?.sendClientMessage(deviceMessage)
        }
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
        device.callback?.aliveOrNull()?.onAssociationError(error.ordinal)
      }

      override fun onMessageReceived(deviceMessage: DeviceMessage) {
        handleSecureChannelMessage(deviceMessage, device)
      }

      override fun onMessageReceivedError(error: MultiProtocolSecureChannel.MessageError) {
        loge(TAG, "Error while receiving message.")
        device.callback?.aliveOrNull()?.onAssociationError(Errors.DEVICE_ERROR_INVALID_HANDSHAKE)
      }
    }

  @VisibleForTesting
  internal fun handleSecureChannelMessage(
    deviceMessage: DeviceMessage,
    device: ConnectedRemoteDevice
  ) {
    if (device.deviceId == associationPendingDeviceId.get()) {
      handleAssociationMessage(deviceMessage)
      return
    }

    logd(TAG, "Received new message from ${device.deviceId}.")
    invokeCallbacksWithDevice(device) { connectedDevice, callback ->
      callback.onMessageReceived(connectedDevice, deviceMessage)
    }
  }

  private fun handleAssociationMessage(deviceMessage: DeviceMessage) {
    val pendingDeviceId = associationPendingDeviceId.getAndSet(null)
    if (pendingDeviceId == null) {
      loge(TAG, "Received an association message with no pending association device. Ignoring.")
      return
    }
    val device = connectedRemoteDevices.remove(pendingDeviceId)
    if (device == null) {
      loge(TAG, "Received an association message and the device was missing!.")
      return
    }
    val deviceId = ByteUtils.bytesToUUID(deviceMessage.message.copyOf(DEVICE_ID_BYTES))
    if (deviceId == null) {
      loge(TAG, "Received invalid device id. Aborting.")
      device
        .callback
        ?.aliveOrNull()
        ?.onAssociationError(ChannelError.CHANNEL_ERROR_INVALID_DEVICE_ID.ordinal)
      return
    }

    connectedRemoteDevices.computeIfAbsent(deviceId) {
      logd(TAG, "Assigning newly-associated device to its real device id.")
      val newDevice = convertTempAssociationDeviceToRealDevice(device, deviceId)
      logd(TAG, "Received device id and secret from $deviceId.")
      try {
        storage.saveChallengeSecret(
          deviceId.toString(),
          deviceMessage.message.copyOfRange(DEVICE_ID_BYTES, deviceMessage.message.size)
        )
      } catch (e: InvalidParameterException) {
        loge(TAG, "Error saving challenge secret.", e)
        // Call on old device since it had the original association callback.
        device
          .callback
          ?.aliveOrNull()
          ?.onAssociationError(ChannelError.CHANNEL_ERROR_INVALID_ENCRYPTION_KEY.ordinal)
        return@computeIfAbsent newDevice
      }
      newDevice.secureChannel?.setDeviceIdDuringAssociation(deviceId)
      persistAssociatedDevice(deviceId.toString())
      newDevice.callback?.onAssociationCompleted()
      invokeCallbacksWithDevice(newDevice) { connectedDevice, callback ->
        callback.onDeviceConnected(connectedDevice)
      }
      invokeCallbacksWithDevice(newDevice) { connectedDevice, callback ->
        callback.onSecureChannelEstablished(connectedDevice)
      }
      newDevice
    }
  }

  private fun convertTempAssociationDeviceToRealDevice(
    device: ConnectedRemoteDevice,
    deviceId: UUID
  ): ConnectedRemoteDevice {
    val newDevice =
      device.copyWithNewDeviceId(deviceId).apply {
        secureChannel?.callback = generateSecureChannelCallback(this)
      }
    for (protocolDevice in newDevice.protocolDevices) {
      protocolDevice.protocol.registerDeviceDisconnectedListener(
        protocolDevice.protocolId,
        generateDeviceDisconnectedListener(deviceId, protocolDevice.protocol)
      )
    }
    return newDevice
  }

  private fun persistAssociatedDevice(deviceId: String) {
    val associatedDevice =
      AssociatedDevice(
        deviceId,
        /* deviceAddress= */ "",
        /* deviceName= */ null,
        /* isConnectionEnabled= */ true
      )
    lock.withLock {
      if (enablePassenger) {
        logd(TAG, "Saving newly associated device $deviceId as unclaimed.")
        storage.addAssociatedDeviceForUser(AssociatedDevice.UNCLAIMED_USER_ID, associatedDevice)
      } else {
        logd(TAG, "Saving newly associated device $deviceId as a driver's device.")
        storage.addAssociatedDeviceForDriver(associatedDevice)
        driverDevices.add(associatedDevice)
      }
      associatedDevices.add(associatedDevice)
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
    val connectedDevice = lock.withLock { device.toConnectedDevice(driverDevices) }
    callbacks.invoke { onCallback(connectedDevice, it) }
  }

  /** Container class to hold information about a connected device. */
  internal data class ConnectedRemoteDevice(
    val deviceId: UUID,
    val protocolDevices: CopyOnWriteArraySet<ProtocolDevice> = CopyOnWriteArraySet()
  ) {
    var secureChannel: MultiProtocolSecureChannel? = null
    var callback: IAssociationCallback? = null
    var name: String? = null
    var channelResolver: ChannelResolver? = null

    /** Returns the [ConnectedDevice] equivalent or `null` if the conversion failed. */
    fun toConnectedDevice(driverDevices: List<AssociatedDevice>): ConnectedDevice {
      val belongsToDriver = driverDevices.any { device -> device.deviceId == deviceId.toString() }
      val hasSecureChannel = secureChannel != null
      return ConnectedDevice(deviceId.toString(), name, belongsToDriver, hasSecureChannel)
    }

    override fun equals(other: Any?): Boolean =
      other is ConnectedRemoteDevice && deviceId == other.deviceId

    override fun hashCode(): Int {
      return Objects.hashCode(deviceId)
    }

    fun copyWithNewDeviceId(newDeviceId: UUID): ConnectedRemoteDevice {
      val newDevice = ConnectedRemoteDevice(newDeviceId, protocolDevices)
      newDevice.secureChannel = secureChannel
      newDevice.name = name
      newDevice.channelResolver = channelResolver
      return newDevice
    }
  }
  companion object {
    private const val TAG = "MultiProtocolDeviceController"
    private const val SALT_BYTES = 8
    private const val TOTAL_AD_DATA_BYTES = 16
    private const val DEVICE_ID_BYTES = 16
    private const val ASSOCIATED_DEVICE_RETRY_MS = 100L
  }
}
