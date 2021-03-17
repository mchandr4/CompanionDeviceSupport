package com.google.android.connecteddevice.transport

import com.google.android.connecteddevice.util.SafeLog
import com.google.android.connecteddevice.util.ThreadSafeCallbacks
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * Representation of a communication protocol that provides actions and event notifications for
 * interacting with devices.
 */
abstract class ConnectionProtocol {
  // protocolId -> callbacks
  private val deviceCallbacks: ConcurrentHashMap<String, ThreadSafeCallbacks<DeviceCallback>> =
    ConcurrentHashMap()

  /** Begin the discovery process for a new device to associate with. */
  abstract fun startAssociationDiscovery(callback: DiscoveryCallback)

  /**
   * Begin the discovery process for a device that will respond to the supplied [id].
   */
  abstract fun startConnectionDiscovery(
    id: UUID,
    callback: DiscoveryCallback
  )

  /** Stop an ongoing discovery for the provided device. */
  abstract fun stopDiscovery(id: UUID)

  /** Send data to a device. */
  abstract fun sendData(protocolId: String, data: ByteArray)

  /** Disconnect a specific device. */
  abstract fun disconnectDevice(protocolId: String)

  /**
   * Disconnect all active connections, cancel any discoveries in progress, and clean up to a
   * neutral state.
   */
  abstract fun reset()

  /** Register a callback to be notified of events for a device on this protocol. */
  fun registerCallback(
    protocolId: String,
    callback: DeviceCallback,
    executor: Executor
  ) {
    val callbacks = deviceCallbacks.computeIfAbsent(protocolId) { ThreadSafeCallbacks() }
    callbacks.add(callback, executor)
  }

  /** Unregister a previously registered callback. */
  fun unregisterCallback(
    protocolId: String,
    callback: DeviceCallback
  ) {
    if (!deviceCallbacks.containsKey(protocolId)) {
      SafeLog.logw(
        TAG,
        "Attempted to delete callback for device $protocolId that does not exist."
      )
      return
    }
    deviceCallbacks[protocolId]?.remove(callback)
  }

  /** Event notifications on the discovery process. */
  interface DiscoveryCallback {
    /** Invoked when discovery for a device has started successfully. */
    fun onDiscoveryStartedSuccessfully()

    /** Invoked when discovery for a device failed to start. */
    fun onDiscoveryFailedToStart()

    /** Invoked when a device connection is established in response to the discovery. */
    fun onDeviceConnected(protocolId: String)
  }

  /** Event notifications for a device on the protocol. */
  interface DeviceCallback {
    /** Invoked when a device has disconnected. */
    fun onDeviceDisconnected(protocolId: String)

    /** Invoked when the protocol has negotiated a new maximum data size. */
    fun onDeviceMaxDataSizeChanged(protocolId: String, maxBytes: Int)

    /** Invoked when data has been received from a device. */
    fun onDataReceived(protocolId: String, data: ByteArray?)
  }

  companion object {
    private const val TAG = "ConnectionProtocol"
  }
}
