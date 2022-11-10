/*
 * Copyright (C) 2020 The Android Open Source Project
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
import android.os.ParcelUuid
import androidx.annotation.CallSuper
import com.google.android.connecteddevice.api.Connector.QueryCallback
import com.google.android.connecteddevice.model.AssociatedDevice
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge

/**
 * Base class for a feature that must bind to `ConnectedDeviceService`. Callbacks are registered
 * automatically and events are forwarded to internal methods. Override these to add custom logic
 * for callback triggers.
 */
abstract class RemoteFeature
protected constructor(
  /** Returns the [Context] registered with the feature. */
  val context: Context,
  /** Returns the [ParcelUuid] feature id registered for the feature. */
  val featureId: ParcelUuid,
  val connector: Connector
) {

  /**
   * Creates a new RemoteFeature.
   *
   * @param context [Context] of the application process
   * @param featureId The id for this feature
   * @param forceFgUserBind Force binding to the foreground user service to avoid a cross-user bind
   */
  @JvmOverloads
  protected constructor(
    context: Context,
    featureId: ParcelUuid,
    forceFgUserBind: Boolean = false
  ) : this(context, featureId, CompanionConnector(context, forceFgUserBind)) {}

  /** Starts setup process and begins connecting to the platform. */
  @CallSuper
  open fun start() {
    connector.connect()
  }

  /** Stops this feature and disconnects from the platform. */
  @CallSuper
  open fun stop() {
    connector.disconnect()
  }

  /** Securely sends message to a device. */
  open fun sendMessageSecurely(deviceId: String, message: ByteArray) {
    connector.sendMessageSecurely(deviceId, message)
  }

  /** Securely sends message to a device. */
  open fun sendMessageSecurely(device: ConnectedDevice, message: ByteArray) {
    connector.sendMessageSecurely(device, message)
  }

  /** Securely sends a query to a device and register a [QueryCallback] for a response. */
  open fun sendQuerySecurely(
    deviceId: String,
    request: ByteArray,
    parameters: ByteArray?,
    callback: Connector.QueryCallback
  ) {
    connector.sendQuerySecurely(deviceId, request, parameters, callback)
  }

  /** Securely sends a query to a device and register a [QueryCallback] for a response. */
  open fun sendQuerySecurely(
    device: ConnectedDevice,
    request: ByteArray,
    parameters: ByteArray?,
    callback: Connector.QueryCallback
  ) {
    connector.sendQuerySecurely(device, request, parameters, callback)
  }

  /** Sends a secure response to a query with an indication of whether it was successful. */
  open fun respondToQuerySecurely(
    device: ConnectedDevice,
    queryId: Int,
    success: Boolean,
    response: ByteArray?
  ) {
    connector.respondToQuerySecurely(device, queryId, success, response)
  }

  /**
   * Returns the [ConnectedDevice] with a matching device id for the currently active user. Returns
   * `null` if no match found.
   */
  open fun getConnectedDeviceById(deviceId: String): ConnectedDevice? {
    return connector.getConnectedDeviceById(deviceId)
  }

  /** Queries the [ConnectedDevice] for its companion application name. */
  open fun getCompanionApplicationName(
    device: ConnectedDevice,
    callback: Connector.AppNameCallback
  ) {
    connector.retrieveCompanionApplicationName(device, callback)
  }

  /** Returns all the currently connected devices. */
  open val connectedDevices: List<ConnectedDevice>
    get() = connector.connectedDevices
  // These can be overridden to perform custom actions.
  /** Called when the platform has connected and is ready for interaction. */
  protected open fun onReady() {}

  /** Called when the platform has disconnected and is no longer ready for interaction. */
  protected open fun onNotReady() {}

  /** Called when a new [ConnectedDevice] is connected. */
  protected open fun onDeviceConnected(device: ConnectedDevice) {}

  /** Called when a [ConnectedDevice] disconnects. */
  protected open fun onDeviceDisconnected(device: ConnectedDevice) {}

  /** Called when a secure channel has been established with a [ConnectedDevice]. */
  protected open fun onSecureChannelEstablished(device: ConnectedDevice) {}

  /**
   * Called when a message fails to send to a device.
   *
   * @param deviceId Id of the device the message failed to send to.
   * @param message Message to send.
   * @param isTransient `true` if cause of failure is transient and can be retried. `false` if
   * failure is permanent.
   */
  protected open fun onMessageFailedToSend(
    deviceId: String,
    message: ByteArray,
    isTransient: Boolean
  ) {}

  /** Called when a new [byte[]] message is received for this feature. */
  protected open fun onMessageReceived(device: ConnectedDevice, message: ByteArray) {}

  /** Called when a new query is received for this feature. */
  protected open fun onQueryReceived(
    device: ConnectedDevice,
    queryId: Int,
    request: ByteArray,
    parameters: ByteArray?
  ) {}

  /** Called when an error has occurred with the connection. */
  protected open fun onDeviceError(device: ConnectedDevice, error: Int) {}

  /** Called when a new [AssociatedDevice] is added for the given user. */
  protected open fun onAssociatedDeviceAdded(device: AssociatedDevice) {}

  /** Called when an [AssociatedDevice] is removed for the given user. */
  protected open fun onAssociatedDeviceRemoved(device: AssociatedDevice) {}

  /** Called when an [AssociatedDevice] is updated for the given user. */
  protected open fun onAssociatedDeviceUpdated(device: AssociatedDevice) {}

  init {
    connector.featureId = featureId
    connector.callback =
      object : Connector.Callback {
        override fun onConnected() {
          logd(TAG, "Successfully connected. Initializing feature.")
          onReady()
        }

        override fun onDisconnected() {
          logd(TAG, "Disconnected from companion. Stopping feature.")
          onNotReady()
        }

        override fun onFailedToConnect() {
          loge(TAG, "Failed to connect. Stopping feature.")
          onNotReady()
        }

        override fun onAssociatedDeviceUpdated(device: AssociatedDevice) {
          this@RemoteFeature.onAssociatedDeviceUpdated(device)
        }

        override fun onAssociatedDeviceRemoved(device: AssociatedDevice) {
          this@RemoteFeature.onAssociatedDeviceRemoved(device)
        }

        override fun onAssociatedDeviceAdded(device: AssociatedDevice) {
          this@RemoteFeature.onAssociatedDeviceAdded(device)
        }

        override fun onDeviceError(device: ConnectedDevice, error: Int) {
          this@RemoteFeature.onDeviceError(device, error)
        }

        override fun onQueryReceived(
          device: ConnectedDevice,
          queryId: Int,
          request: ByteArray,
          parameters: ByteArray?
        ) {
          this@RemoteFeature.onQueryReceived(device, queryId, request, parameters)
        }

        override fun onMessageReceived(device: ConnectedDevice, message: ByteArray) {
          this@RemoteFeature.onMessageReceived(device, message)
        }

        override fun onMessageFailedToSend(
          deviceId: String,
          message: ByteArray,
          isTransient: Boolean
        ) {
          this@RemoteFeature.onMessageFailedToSend(deviceId, message, isTransient)
        }

        override fun onSecureChannelEstablished(device: ConnectedDevice) {
          this@RemoteFeature.onSecureChannelEstablished(device)
        }

        override fun onDeviceDisconnected(device: ConnectedDevice) {
          this@RemoteFeature.onDeviceDisconnected(device)
        }

        override fun onDeviceConnected(device: ConnectedDevice) {
          this@RemoteFeature.onDeviceConnected(device)
        }
      }
  }

  companion object {
    private const val TAG = "RemoteFeature"

    /** Intent action used to request a device be associated. */
    const val ACTION_ASSOCIATION_SETTING =
      "com.google.android.connecteddevice.api.ASSOCIATION_ACTIVITY"

    /** Data name for associated device. */
    const val ASSOCIATED_DEVICE_DATA_NAME_EXTRA =
      "com.google.android.connecteddevice.api.ASSOCIATED_DEVICE"
  }
}
