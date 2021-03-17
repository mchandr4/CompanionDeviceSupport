package com.google.android.connecteddevice.connection

import com.google.android.connecteddevice.connection.DeviceController.Callback
import com.google.android.connecteddevice.connection.DeviceController.ConnectedRemoteDevice
import com.google.android.connecteddevice.model.Errors
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.ConnectionProtocol.DiscoveryCallback
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logw
import com.google.android.connecteddevice.util.ThreadSafeCallbacks
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executor

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
    val discoveryCallback = generateDiscoveryCallback(
      associationCallback = null,
      deviceId,
      nameForAssociation = null
    )
    for (protocol in protocols) {
      protocol.startConnectionDiscovery(deviceId, discoveryCallback)
    }
  }

  /** Start the association with a new device  */
  fun startAssociation(nameForAssociation: String, callback: AssociationCallback) {
    // Assign an random UUID to each association request to ensure only one association process is
    // ongoing for one device simultaneously.
    val associationId = UUID.randomUUID()
    val discoveryCallback = generateDiscoveryCallback(callback, associationId, nameForAssociation)
    for (protocol in protocols) {
      protocol.startAssociationDiscovery(discoveryCallback)
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
    // TODO(b/182396724) Disconnect all the connected channels.
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
  internal data class ConnectedRemoteDevice(var deviceId: UUID) {
    /** List of connected protocol ids.  */
    // TODO(b/182396724) Adding a protocol and protocolId pair here.
    val protocolIds = CopyOnWriteArraySet<String>()
    var secureChannel: SecureChannel? = null
    var callback: AssociationCallback? = null
  }

  /**
   *  Generate the [DiscoveryCallback] for device [deviceId] with advertisement name
   *  [nameForAssociation], response will be patched through the [associationCallback].
   */
  private fun generateDiscoveryCallback(
    associationCallback: AssociationCallback?,
    deviceId: UUID,
    nameForAssociation: String?,
  ): DiscoveryCallback {
    return object : DiscoveryCallback {
      override fun onDeviceConnected(protocolId: String) {
        // TODO(b/180743856): DeviceMessageStream uses protocol interface for communication
        // Each device only do version resolver for once
        var device = getConnectedDevice(deviceId)
        if (device != null) {
          // TODO(b/180743223): Add the stream to current secure channel
          device.protocolIds.add(protocolId)
          return
        }
        device = ConnectedRemoteDevice(deviceId).apply {
          protocolIds.add(protocolId)
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
          it.onAssociationStartSuccess(nameForAssociation)
        }
      }

      override fun onDiscoveryFailedToStart() {
        associationCallback?.onAssociationStartFailure()
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
        // TODO(b/182396724): Disconnect certain protocol.
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
  }
}
