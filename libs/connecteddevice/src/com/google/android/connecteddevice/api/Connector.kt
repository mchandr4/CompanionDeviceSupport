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
package com.google.android.connecteddevice.api

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.ParcelUuid
import androidx.annotation.IntDef
import com.google.android.connecteddevice.model.AssociatedDevice
import com.google.android.connecteddevice.model.ConnectedDevice

/** Class for establishing and maintaining a connection to the companion device platform. */
interface Connector {

  /**
   * Optional feature id for sending and receiving messages. Connectors initialized without a
   * [featureId] will not be registered for device callbacks and will not be able to send or receive
   * messages.
   */
  var featureId: ParcelUuid?

  /** [Callback] for connection events. */
  var callback: Callback?

  /** List of the currently connected devices. */
  val connectedDevices: List<ConnectedDevice>

  /** Whether this [Connector] is currently connected and ready for interaction. */
  val isConnected: Boolean

  /** Establishes a connection to the companion platform. */
  fun connect()

  /** Disconnects from the companion platform. */
  fun disconnect()

  /** Returns the backing [IBinder] of the bound service for the given [action]. */
  fun binderForAction(action: String): IBinder?

  /** Securely sends message to a device. */
  fun sendMessageSecurely(deviceId: String, message: ByteArray)

  /** Securely sends message to a device. */
  fun sendMessageSecurely(device: ConnectedDevice, message: ByteArray)

  /** Securely send a query to a device and registers a [QueryCallback] for a response. */
  fun sendQuerySecurely(
    deviceId: String,
    request: ByteArray,
    parameters: ByteArray?,
    callback: QueryCallback
  )

  /** Securely sends a query to a device and registers a [QueryCallback] for a response. */
  fun sendQuerySecurely(
    device: ConnectedDevice,
    request: ByteArray,
    parameters: ByteArray?,
    callback: QueryCallback
  )

  /** Sends a secure response to a query with an indication of whether it was successful. */
  fun respondToQuerySecurely(
    device: ConnectedDevice,
    queryId: Int,
    success: Boolean,
    response: ByteArray?
  )

  /**
   * Returns the [ConnectedDevice] with a matching device id for the currently active user. Returns
   * `null` if no match found.
   */
  fun getConnectedDeviceById(deviceId: String): ConnectedDevice?

  /** Queries the [ConnectedDevice] for its companion application name. */
  fun retrieveCompanionApplicationName(device: ConnectedDevice, callback: AppNameCallback)

  /**
   * Starts the association with a new device with a [callback] to be notified for association
   * events.
   */
  fun startAssociation(callback: IAssociationCallback)

  /**
   * Starts the association with a new device using the specified [identifier] with a [callback] to
   * be notified for association events.
   */
  fun startAssociation(identifier: ParcelUuid, callback: IAssociationCallback)

  /** Stops the association process. */
  fun stopAssociation()

  /** Confirms the paring code. */
  fun acceptVerification()

  /** Remove the associated device of the given [deviceId]. */
  fun removeAssociatedDevice(deviceId: String)

  /** Enable connection on the associated device with the given [deviceId]. */
  fun enableAssociatedDeviceConnection(deviceId: String)

  /** Disable connection on the associated device with the given [deviceId]. */
  fun disableAssociatedDeviceConnection(deviceId: String)

  /**
   * Retrieves all associated devices with a [listener] that will be notified when the associated
   * devices are retrieved.
   */
  fun retrieveAssociatedDevices(listener: IOnAssociatedDevicesRetrievedListener)

  /**
   * Retrieves the associated devices belonging to the current driver with a [listener] that will be
   * notified when the associated devices are retrieved.
   */
  fun retrieveAssociatedDevicesForDriver(listener: IOnAssociatedDevicesRetrievedListener)

  /**
   * Retrieves all associated devices belonging to all passengers with a [listener] that will be
   * notified when the associated devices are retrieved.
   */
  fun retrieveAssociatedDevicesForPassengers(listener: IOnAssociatedDevicesRetrievedListener)

  /** Claim an associated device as belonging to the current user. */
  fun claimAssociatedDevice(deviceId: String)

  /** Remove the claim on the identified associated device. */
  fun removeAssociatedDeviceClaim(deviceId: String)

  /** Callbacks invoked on connection events. */
  interface Callback {
    /** Invoked when a connection has been successfully established. */
    fun onConnected() {}

    /** Invoked when the connection to the platform has been lost. */
    fun onDisconnected() {}

    /** Invoked when no connection to the platform could be established. */
    fun onFailedToConnect() {}

    /** Called when a new [ConnectedDevice] is connected. */
    fun onDeviceConnected(device: ConnectedDevice) {}

    /** Called when a [ConnectedDevice] disconnects. */
    fun onDeviceDisconnected(device: ConnectedDevice) {}

    /** Called when a secure channel has been established with a [ConnectedDevice]. */
    fun onSecureChannelEstablished(device: ConnectedDevice) {}

    /**
     * Called when a message fails to send to a device.
     *
     * @param deviceId Id of the device the message failed to send to.
     * @param message Message to send.
     * @param isTransient `true` if cause of failure is transient and can be retried. `false` if
     * failure is permanent.
     */
    fun onMessageFailedToSend(deviceId: String, message: ByteArray, isTransient: Boolean) {}

    /** Called when a new [byte[]] message is received for this feature. */
    fun onMessageReceived(device: ConnectedDevice, message: ByteArray) {}

    /** Called when a new query is received for this feature. */
    fun onQueryReceived(
      device: ConnectedDevice,
      queryId: Int,
      request: ByteArray,
      parameters: ByteArray?
    ) {}

    /** Called when an error has occurred with the connection. */
    fun onDeviceError(device: ConnectedDevice, error: Int) {}

    /** Called when a new [AssociatedDevice] is added for the given user. */
    fun onAssociatedDeviceAdded(device: AssociatedDevice) {}

    /** Called when an [AssociatedDevice] is removed for the given user. */
    fun onAssociatedDeviceRemoved(device: AssociatedDevice) {}

    /** Called when an [AssociatedDevice] is updated for the given user. */
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
     * When a client calls [Context.bindService] to get the [IFeatureCoordinator], this action is
     * required in the param [Intent].
     */
    const val ACTION_BIND_FEATURE_COORDINATOR =
      "com.google.android.connecteddevice.api.BIND_FEATURE_COORDINATOR"

    /**
     * When a client calls [Context.bindService] to get the [IFeatureCoordinator] from a service
     * running in the foreground user. Any process that resides outside of the service host
     * application must use this action in its [Intent].
     */
    const val ACTION_BIND_FEATURE_COORDINATOR_FG =
      "com.google.android.connecteddevice.api.BIND_FEATURE_COORDINATOR_FG"

    /** Type associated with a driver's device. */
    const val USER_TYPE_DRIVER = 1 shl 0

    /** Type associated with a passenger's device. */
    const val USER_TYPE_PASSENGER = 1 shl 1

    /** Type associated with all user types. */
    const val USER_TYPE_ALL = USER_TYPE_DRIVER or USER_TYPE_PASSENGER

    /** Id for the system query feature. */
    val SYSTEM_FEATURE_ID: ParcelUuid =
      ParcelUuid.fromString("892ac5d9-e9a5-48dc-874a-c01e3cb00d5d")

    /** User types that can be associated with a connected device. */
    @IntDef(USER_TYPE_DRIVER, USER_TYPE_PASSENGER, USER_TYPE_ALL)
    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.TYPE)
    annotation class UserType
  }
}
