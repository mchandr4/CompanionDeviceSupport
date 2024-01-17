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

import android.os.ParcelUuid
import androidx.annotation.CallSuper
import com.google.android.connecteddevice.util.AidlThreadSafeCallbacks
import com.google.android.connecteddevice.util.DirectExecutor
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.logw
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/**
 * Representation of a communication protocol that provides actions and event notifications for
 * interacting with devices.
 */
abstract class ConnectionProtocol() : IConnectionProtocol.Stub() {
  private val callbackExecutor: Executor = DirectExecutor()

  protected val dataReceivedListeners:
    MutableMap<String, AidlThreadSafeCallbacks<IDataReceivedListener>> =
    ConcurrentHashMap()

  protected val deviceDisconnectedListeners:
    MutableMap<String, AidlThreadSafeCallbacks<IDeviceDisconnectedListener>> =
    ConcurrentHashMap()

  protected val maxDataSizeChangedListeners:
    MutableMap<String, AidlThreadSafeCallbacks<IDeviceMaxDataSizeChangedListener>> =
    ConcurrentHashMap()

  private val missedData: MutableMap<String, MutableList<ByteArray>> = ConcurrentHashMap()

  /**
   * `true` if challenge exchange is required to verify the remote device for establishing a secure
   * channel over this [ConnectionProtocol].
   */
  abstract override fun isDeviceVerificationRequired(): Boolean

  /**
   * Begin the discovery process with [name] and [identifier] for a new device to associate with.
   */
  abstract override fun startAssociationDiscovery(
    name: String,
    identifier: ParcelUuid,
    callback: IDiscoveryCallback
  )

  /**
   * Begin the discovery process for a device that will respond to the supplied [id] with
   * [challenge].
   */
  abstract override fun startConnectionDiscovery(
    id: ParcelUuid,
    challenge: ConnectChallenge,
    callback: IDiscoveryCallback
  )

  /** Stop an ongoing association discovery. */
  abstract override fun stopAssociationDiscovery()

  /** Stop an ongoing connection discovery for the provided device. */
  abstract override fun stopConnectionDiscovery(id: ParcelUuid)

  /** Send data to a device. */
  abstract override fun sendData(protocolId: String, data: ByteArray, callback: IDataSendCallback?)

  /** Disconnect a specific device. */
  abstract override fun disconnectDevice(protocolId: String)

  /**
   * Disconnect all active connections, cancel any discoveries in progress, and clean up to a
   * neutral state.
   */
  @CallSuper
  override fun reset() {
    deviceDisconnectedListeners.clear()
    dataReceivedListeners.clear()
    maxDataSizeChangedListeners.clear()
    missedData.clear()
  }

  /**
   * Returns the maximum number of bytes that can be written in a single message for the device
   * matching the [protocolId].
   */
  abstract override fun getMaxWriteSize(protocolId: String): Int

  /** Register a listener to be notified when data has been received on protocol [protocolId]. */
  override fun registerDataReceivedListener(protocolId: String, listener: IDataReceivedListener) {
    logd(TAG, "Registering a new DataReceivedListener.")
    val listeners = dataReceivedListeners.computeIfAbsent(protocolId) { AidlThreadSafeCallbacks() }
    listeners.add(listener, callbackExecutor)
    missedData.remove(protocolId)?.forEach { data ->
      listeners.invoke { it.onDataReceived(protocolId, data) }
    }
  }

  /** Register a listener to be notified when device has disconnected on protocol [protocolId]. */
  override fun registerDeviceDisconnectedListener(
    protocolId: String,
    listener: IDeviceDisconnectedListener
  ) {
    deviceDisconnectedListeners
      .computeIfAbsent(protocolId) { AidlThreadSafeCallbacks() }
      .add(listener, callbackExecutor)
  }

  /**
   * Register a listener to be notified when the protocol [protocolId] has negotiated a new maximum
   * data size.
   */
  override fun registerDeviceMaxDataSizeChangedListener(
    protocolId: String,
    listener: IDeviceMaxDataSizeChangedListener
  ) {
    maxDataSizeChangedListeners
      .computeIfAbsent(protocolId) { AidlThreadSafeCallbacks() }
      .add(listener, callbackExecutor)
  }

  /** Unregister a previously registered [listener]. */
  override fun unregisterDataReceivedListener(protocolId: String, listener: IDataReceivedListener) {
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

  /** Unregister a previously registered [listener]. */
  override fun unregisterDeviceDisconnectListener(
    protocolId: String,
    listener: IDeviceDisconnectedListener
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

  /** Unregister a previously registered [listener]. */
  override fun unregisterDeviceMaxDataSizeChangedListener(
    protocolId: String,
    listener: IDeviceMaxDataSizeChangedListener
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
  override fun removeListeners(protocolId: String) {
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
    dataReceivedListeners[protocolId]
      ?.takeUnless { it.isEmpty }
      ?.invoke { it.onDataReceived(protocolId, data) }
      ?: run {
        logd(
          TAG,
          "No callback has been registered for connection $protocolId, cached received message."
        )
        missedData.computeIfAbsent(protocolId) { CopyOnWriteArrayList() }.add(data)
      }
  }

  companion object {
    private const val TAG = "ConnectionProtocol"
  }
}
