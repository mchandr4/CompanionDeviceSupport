/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.google.android.connecteddevice.api

import android.content.Context
import android.content.Intent
import android.os.ParcelUuid
import com.google.android.connecteddevice.api.external.ISafeOnAssociatedDevicesRetrievedListener
import com.google.android.connecteddevice.model.AssociatedDevice

/**
 * Interface for establishing and maintaining a connection to the companion device platform. This is
 * a subset of Connector, only implementing feature-related functions. It is meant for external
 * feature usage. Fully backward and forward compatible to ensure robust version mismatch handling.
 */
interface SafeConnector {

  /** Feature id for sending and receiving messages. */
  val featureId: ParcelUuid

  /** [Callback] for connection events. */
  var callback: Callback

  /** List of ids for the currently connected devices. */
  val connectedDevices: List<String>

  /** Whether this [SafeConnector] is currently connected and ready for interaction. */
  val isConnected: Boolean

  /** Establishes a connection to the companion platform. */
  fun connect(callback: Callback)

  /**
   * Cleans up services and feature coordinators attached to the companion platform. Will disconnect
   * the feature from the platform and prevent it from receiving Companion-related events.
   * Expectation is that disconnect will only be called before the SafeConnector object is disposed.
   */
  fun disconnect()

  /** Sends message to a device. */
  fun sendMessage(deviceId: String, message: ByteArray)

  /** Sends a query to a device and registers a [QueryCallback] for a response. */
  fun sendQuery(
    deviceId: String,
    request: ByteArray,
    parameters: ByteArray?,
    queryCallback: QueryCallback,
  )

  /** Sends a response to a query with an indication of whether it was successful. */
  fun respondToQuery(deviceId: String, queryId: Int, success: Boolean, response: ByteArray?)

  /** Queries the [ConnectedDevice] for its companion application name. */
  fun retrieveCompanionApplicationName(deviceId: String, appNameCallback: AppNameCallback)

  /**
   * Retrieves all associated devices with a [listener] that will be notified when the associated
   * devices are retrieved.
   */
  fun retrieveAssociatedDevices(listener: IOnAssociatedDevicesRetrievedListener)

  /**
   * Retrieves all associated devices with a [listener] that will be notified when the associated
   * devices are retrieved.
   */
  fun retrieveAssociatedDevices(listener: ISafeOnAssociatedDevicesRetrievedListener)

  /** Callbacks invoked on connection events. */
  interface Callback {
    /** Invoked when a connection has been successfully established. */
    fun onConnected() {}

    /** Invoked when the connection to the platform has been lost. */
    fun onDisconnected() {}

    /** Invoked when no connection to the platform could be established. */
    fun onFailedToConnect() {}

    /** Invoked when the platform version is older than the minimum supported version. */
    fun onApiNotSupported() {}

    /** Invoked when a new connected device with id deviceId is connected. */
    fun onDeviceConnected(deviceId: String) {}

    /** Invoked when a connected device with id deviceId disconnects. */
    fun onDeviceDisconnected(deviceId: String) {}

    /**
     * Invoked when a secure channel has been established with a connected device with id deviceId.
     */
    fun onSecureChannelEstablished(deviceId: String) {}

    /**
     * Invoked when a message fails to send to a device.
     *
     * @param deviceId Id of the device the message failed to send to.
     * @param message Message to send.
     * @param isTransient `true` if cause of failure is transient and can be retried. `false` if
     *   failure is permanent.
     */
    fun onMessageFailedToSend(deviceId: String, message: ByteArray, isTransient: Boolean) {}

    /** Invoked when a new [byte[]] message is received for this feature. */
    fun onMessageReceived(deviceId: String, message: ByteArray) {}

    /** Invoked when a new query is received for this feature. */
    fun onQueryReceived(
      deviceId: String,
      queryId: Int,
      request: ByteArray,
      parameters: ByteArray?,
    ) {}

    /** Invoked when an error has occurred with the connection. */
    fun onDeviceError(deviceId: String, error: Int) {}

    /** Invoked when a new [AssociatedDevice] is added for the given user. */
    fun onAssociatedDeviceAdded(device: AssociatedDevice) {}

    /** Invoked when an [AssociatedDevice] is removed for the given user. */
    fun onAssociatedDeviceRemoved(device: AssociatedDevice) {}

    /** Invoked when an [AssociatedDevice] is updated for the given user. */
    fun onAssociatedDeviceUpdated(device: AssociatedDevice) {}
  }

  /** Callback for a query response. */
  interface QueryCallback {
    /** Invoked with a successful response to a query. */
    fun onSuccess(response: ByteArray?) {}

    /** Invoked with an unsuccessful response to a query. */
    fun onError(response: ByteArray?) {}

    /**
     * Invoked when a query failed to send to the device. `isTransient` is set to `true` if cause of
     * failure is transient and can be retried, or `false` if failure is permanent.
     */
    fun onQueryFailedToSend(isTransient: Boolean) {}
  }

  /** Callback for a query for the name of the companion application on the connected device. */
  interface AppNameCallback {
    /** Invoked with the name of the companion application on the connected device. */
    fun onNameReceived(appName: String) {}

    /** Invoked when the name failed to be retrieved from the connected device. */
    fun onError() {}
  }

  companion object {
    /**
     * When a client calls [Context.bindService] to get the [ISafeFeatureCoordinator], this action
     * is required in the param [Intent].
     */
    const val ACTION_BIND_SAFE_FEATURE_COORDINATOR =
      "com.google.android.connecteddevice.api.BIND_SAFE_FEATURE_COORDINATOR"

    /**
     * When a client queries for the platform's API version, this action is required in the param
     * [Intent]
     */
    const val ACTION_QUERY_API_VERSION = "com.google.android.connecteddevice.api.QUERY_API_VERSION"

    /** Id for the system query feature. */
    val SYSTEM_FEATURE_ID = ParcelUuid.fromString("892ac5d9-e9a5-48dc-874a-c01e3cb00d5d")
  }
}
