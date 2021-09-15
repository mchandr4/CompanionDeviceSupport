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
package com.google.android.connecteddevice.transport

import androidx.annotation.CallSuper
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.logw
import com.google.android.connecteddevice.util.ThreadSafeCallbacks
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/**
 * Representation of a communication protocol that provides actions and event notifications for
 * interacting with devices.
 */
abstract class ConnectionProtocol {
  protected val dataReceivedListeners:
    MutableMap<String, ThreadSafeCallbacks<DataReceivedListener>> =
    ConcurrentHashMap()

  protected val deviceDisconnectedListeners:
    MutableMap<String, ThreadSafeCallbacks<DeviceDisconnectedListener>> =
    ConcurrentHashMap()

  protected val maxDataSizeChangedListeners:
    MutableMap<String, ThreadSafeCallbacks<DeviceMaxDataSizeChangedListener>> =
    ConcurrentHashMap()

  protected val missedData: MutableMap<String, MutableList<ByteArray>> = ConcurrentHashMap()

  /**
   * `true` if challenge exchange is required to verify the remote device for establishing a secure
   * channel over this [ConnectionProtocol].
   */
  abstract val isDeviceVerificationRequired: Boolean

  /** Begin the discovery process with [name] for a new device to associate with. */
  abstract fun startAssociationDiscovery(name: String, callback: DiscoveryCallback)

  /**
   * Begin the discovery process for a device that will respond to the supplied [id] with
   * [challenge].
   */
  abstract fun startConnectionDiscovery(
    id: UUID,
    challenge: ConnectChallenge,
    callback: DiscoveryCallback
  )

  /** Stop an ongoing association discovery. */
  abstract fun stopAssociationDiscovery()

  /** Stop an ongoing connection discovery for the provided device. */
  abstract fun stopConnectionDiscovery(id: UUID)

  /** Send data to a device. */
  abstract fun sendData(protocolId: String, data: ByteArray, callback: DataSendCallback? = null)

  /** Disconnect a specific device. */
  abstract fun disconnectDevice(protocolId: String)

  /**
   * Disconnect all active connections, cancel any discoveries in progress, and clean up to a
   * neutral state.
   */
  @CallSuper
  open fun reset() {
    deviceDisconnectedListeners.clear()
    dataReceivedListeners.clear()
    maxDataSizeChangedListeners.clear()
    missedData.clear()
  }

  /**
   * Returns the maximum number of bytes that can be written in a single message for the device
   * matching the [protocolId].
   */
  abstract fun getMaxWriteSize(protocolId: String): Int

  /** Register a listener to be notified when data has been received on protocol [protocolId]. */
  open fun registerDataReceivedListener(
    protocolId: String,
    listener: DataReceivedListener,
    executor: Executor
  ) {
    logd(TAG, "Registering a new DataReceivedListener.")
    val listeners = dataReceivedListeners.computeIfAbsent(protocolId) { ThreadSafeCallbacks() }
    listeners.add(listener, executor)
    missedData.remove(protocolId)?.forEach { data ->
      listeners.invoke { it.onDataReceived(protocolId, data) }
    }
  }

  /** Register a listener to be notified when device has disconnected on protocol [protocolId]. */
  open fun registerDeviceDisconnectedListener(
    protocolId: String,
    listener: DeviceDisconnectedListener,
    executor: Executor
  ) {
    deviceDisconnectedListeners
      .computeIfAbsent(protocolId) { ThreadSafeCallbacks() }
      .add(listener, executor)
  }

  /**
   * Register a listener to be notified when the protocol [protocolId] has negotiated a new maximum
   * data size.
   */
  open fun registerDeviceMaxDataSizeChangedListener(
    protocolId: String,
    listener: DeviceMaxDataSizeChangedListener,
    executor: Executor
  ) {
    maxDataSizeChangedListeners
      .computeIfAbsent(protocolId) { ThreadSafeCallbacks() }
      .add(listener, executor)
  }

  /** Unregister a previously registered [DataReceivedListener]. */
  open fun unregisterDataReceivedListener(protocolId: String, listener: DataReceivedListener) {
    logd(TAG, "Removing data DataReceivedListener.")
    val listeners =
      dataReceivedListeners.getOrElse(protocolId) {
        logw(
          TAG,
          "Attempted to delete data received listener for device $protocolId that does not exist."
        )
        return
      }
    listeners.remove(listener)
    if (listeners.isEmpty) {
      dataReceivedListeners.remove(protocolId)
    }
  }

  /** Unregister a previously registered [DeviceDisconnectedListener]. */
  open fun unregisterDeviceDisconnectListener(
    protocolId: String,
    listener: DeviceDisconnectedListener
  ) {
    val listeners =
      deviceDisconnectedListeners.getOrElse(protocolId) {
        logw(
          TAG,
          "Attempted to delete disconnect listener for device $protocolId that does not exist."
        )
        return
      }
    listeners.remove(listener)
    if (listeners.isEmpty) {
      deviceDisconnectedListeners.remove(protocolId)
    }
  }

  /** Unregister a previously registered [DeviceMaxDataSizeChangedListener]. */
  open fun unregisterDeviceMaxDataSizeChangedListener(
    protocolId: String,
    listener: DeviceMaxDataSizeChangedListener
  ) {
    val listeners =
      maxDataSizeChangedListeners.getOrElse(protocolId) {
        logw(
          TAG,
          "Attempted to delete maxDataSizeChangedListener for device $protocolId that does not " +
            "exist."
        )
        return
      }
    listeners.remove(listener)
    if (listeners.isEmpty) {
      maxDataSizeChangedListeners.remove(protocolId)
    }
  }

  /** Removes registered listeners for connection [protocolId]. */
  fun removeListeners(protocolId: String) {
    deviceDisconnectedListeners.remove(protocolId)
    dataReceivedListeners.remove(protocolId)
    maxDataSizeChangedListeners.remove(protocolId)
  }

  /**
   * Notifies that [data] has been received from connected device.
   *
   * If there's no registered dataReceivedListener to notify, the data will be cached for the
   * listeners registered later.
   */
  fun notifyDataReceived(protocolId: String, data: ByteArray) {
    dataReceivedListeners[protocolId]?.takeUnless { it.isEmpty }?.invoke {
      it.onDataReceived(protocolId, data)
    }
      ?: run {
        logd(
          TAG,
          "No callback has been registered for connection $protocolId, cached received message."
        )
        missedData.computeIfAbsent(protocolId) { CopyOnWriteArrayList() }.add(data)
      }
  }

  /** Container class to hold the connect challenge the salt that generated the challenge. */
  data class ConnectChallenge(val challenge: ByteArray, val salt: ByteArray)

  /** Event notifications on the discovery process. */
  interface DiscoveryCallback {
    /** Invoked when discovery for a device has started successfully. */
    fun onDiscoveryStartedSuccessfully()

    /** Invoked when discovery for a device failed to start. */
    fun onDiscoveryFailedToStart()

    /** Invoked when a device connection is established in response to the discovery. */
    fun onDeviceConnected(protocolId: String)
  }

  /** Callback for the result of sending data. */
  interface DataSendCallback {
    /** Invoked when the data was successfully sent. */
    fun onDataSentSuccessfully()

    /** Invoked when the data failed to send. */
    fun onDataFailedToSend()
  }

  /** Listener to be invoked when a device has disconnected. */
  interface DeviceDisconnectedListener {
    /** Called when the device has disconnected on protocol [protocolId]. */
    fun onDeviceDisconnected(protocolId: String)
  }

  /** Listener to be invoked when data has been received from a device. */
  interface DataReceivedListener {
    /** Called when [data] is received from the remote device on protocol [protocolId]. */
    fun onDataReceived(protocolId: String, data: ByteArray)
  }

  /** Listener to be invoked when the protocol has negotiated a new maximum data size. */
  interface DeviceMaxDataSizeChangedListener {
    /** Called when the protocol [protocolId] has negotiated a new maximum data size [maxBytes]. */
    fun onDeviceMaxDataSizeChanged(protocolId: String, maxBytes: Int)
  }

  companion object {
    private const val TAG = "ConnectionProtocol"
  }
}
