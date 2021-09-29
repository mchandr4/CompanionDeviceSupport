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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.os.RemoteException
import androidx.annotation.VisibleForTesting
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logw
import java.lang.IllegalStateException
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Class for establishing and maintaining a connection to the companion device platform.
 *
 * @property context [Context] of the hosting process.
 * @property isForegroundProcess Set to `true` if running from outside of the companion application.
 */
class CompanionConnector @JvmOverloads constructor(
  private val context: Context,
  private val isForegroundProcess: Boolean = false
) {
  private val isLegacyOnly = AtomicBoolean(false)

  private val retryHandler = Handler(Looper.getMainLooper())

  private val featureCoordinatorAction =
    if (isForegroundProcess) {
      ACTION_BIND_FEATURE_COORDINATOR_FG
    } else {
      ACTION_BIND_FEATURE_COORDINATOR
    }

  private val connectedDeviceManagerAction =
    if (isForegroundProcess) {
      ACTION_BIND_REMOTE_FEATURE_FG
    } else {
      ACTION_BIND_REMOTE_FEATURE
    }

  private val serviceConnection: ServiceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, service: IBinder) {
      this@CompanionConnector.onServiceConnected(service)
    }

    override fun onServiceDisconnected(name: ComponentName) {
      this@CompanionConnector.onServiceDisconnected()
    }

    override fun onNullBinding(name: ComponentName) {
      this@CompanionConnector.onNullBinding()
    }
  }

  private var bindAttempts = 0

  /** [Callback] for connection events. */
  var callback: Callback? = null

  /**
   * [IFeatureCoordinator] retrieved after connecting to service. Will be `null` if the platform
   * has not been enabled for FeatureCoordinator support.
   */
  var featureCoordinator: IFeatureCoordinator? = null
    private set

  /**
   * [IConnectedDeviceManager] retrieved after connecting to service. This will either be a wrapping
   * of the [IFeatureCoordinator] or the object returned by the service depending on which version
   * the service supports.
   */
  var connectedDeviceManager: IConnectedDeviceManager? = null
    private set

  /** Establish a connection to the companion platform. */
  fun connect() {
    logd(TAG, "Initiating connection to companion platform.")
    val intent =
      if (isLegacyOnly.get()) {
        resolveIntent(connectedDeviceManagerAction)
      } else {
        resolveIntent(featureCoordinatorAction)
          ?: resolveIntent(connectedDeviceManagerAction).also { isLegacyOnly.set(true) }
      }

    if (intent == null) {
      loge(TAG, "No services found supporting companion device. Aborting.")
      callback?.onFailedToConnect()
      return
    }

    val success = context.bindService(intent, serviceConnection, /* flag= */ 0)
    if (success) {
      logd(TAG, "Successfully started binding with ${intent.action}.")
      return
    }

    bindAttempts++
    if (bindAttempts > MAX_BIND_ATTEMPTS) {
      loge(TAG, "Failed to bind to service after $bindAttempts attempts. Aborting.")
      callback?.onFailedToConnect()
      return
    }
    logw(TAG, "Unable to bind to service with action ${intent.action}. Trying again.")
    retryHandler.postDelayed(::connect, BIND_RETRY_DURATION.toMillis())
  }

  /** Disconnect from the companion platform. */
  fun disconnect() {
    logd(TAG, "Disconnecting from the companion platform.")
    if (connectedDeviceManager != null || featureCoordinator != null) {
      callback?.onDisconnected()
    }
    connectedDeviceManager = null
    featureCoordinator = null
    retryHandler.removeCallbacksAndMessages(/* token= */ null)
    try {
      context.unbindService(serviceConnection)
    } catch (e: IllegalArgumentException) {
      logw(TAG, "Attempted to unbind an already unbound service.")
    }
    bindAttempts = 0
    isLegacyOnly.set(false)
  }

  private fun onServiceConnected(service: IBinder?) {
    logd(TAG, "Service connected.")
    if (isLegacyOnly.get()) {
      logd(TAG, "Setting up legacy manager.")
      connectedDeviceManager = IConnectedDeviceManager.Stub.asInterface(service)
    } else {
      logd(TAG, "Setting up coordinator and legacy wrapper.")
      featureCoordinator = IFeatureCoordinator.Stub.asInterface(service)
      val coordinator = featureCoordinator
        ?: throw IllegalStateException("Cannot create wrapper of a null feature coordinator.")
      connectedDeviceManager = createConnectedDeviceManagerWrapper(coordinator)
    }
    callback?.onConnected()
  }

  private fun onServiceDisconnected() {
    logd(TAG, "Service has disconnected. Cleaning up.")
    disconnect()
  }

  private fun onNullBinding() {
    if (!isLegacyOnly.compareAndSet(false, true)) {
      loge(TAG, "Received a null binding. Alerting callbacks of failed connection.")
      callback?.onFailedToConnect()
      return
    }
    logd(
      TAG,
      "Service does not support feature coordinator despite defining the action. " +
        "Attempting to bind again in legacy mode."
    )
    connect()
  }

  private fun resolveIntent(action: String): Intent? {
    val packageManager = context.packageManager
    val intent = Intent(action)
    val services = packageManager.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY)
    if (services.isEmpty()) {
      logw(TAG, "There are no services supporting the $action action installed on this device.")
      return null
    }
    logd(TAG, "Found ${services.size} service(s) supporting $action. Choosing the first one.")
    val service = services[0]
    return intent.apply {
      component = ComponentName(service.serviceInfo.packageName, service.serviceInfo.name)
    }
  }

  /** Callbacks invoked on connection events. */
  interface Callback {
    /** Invoked when a connection has been successfully established. */
    fun onConnected()
    /** Invoked when the connection to the platform has been lost. */
    fun onDisconnected()
    /** Invoked when no connection to the platform could be established. */
    fun onFailedToConnect()
  }

  companion object {
    /**
     * When a client calls [Context.bindService] to get the
     * [IConnectedDeviceManager], this action is required in the param [Intent].
     */
    const val ACTION_BIND_REMOTE_FEATURE =
      "com.google.android.connecteddevice.api.BIND_REMOTE_FEATURE"

    /**
     * When a client calls [Context.bindService] to get the
     * [IConnectedDeviceManager] from a service running in the foreground user. Any process that
     * resides outside of the service host application must use this action in its [Intent].
     */
    const val ACTION_BIND_REMOTE_FEATURE_FG =
      "com.google.android.connecteddevice.api.BIND_REMOTE_FEATURE_FG"

    /**
     * When a client calls [Context.bindService] to get the
     * [IFeatureCoordinator], this action is required in the param [Intent].
     */
    const val ACTION_BIND_FEATURE_COORDINATOR =
      "com.google.android.connecteddevice.api.BIND_FEATURE_COORDINATOR"

    /**
     * When a client calls [Context.bindService] to get the
     * [IFeatureCoordinator] from a service running in the foreground user. Any process that
     * resides outside of the service host application must use this action in its [Intent].
     */
    const val ACTION_BIND_FEATURE_COORDINATOR_FG =
      "com.google.android.connecteddevice.api.BIND_FEATURE_COORDINATOR_FG"

    private const val TAG = "CompanionConnector"
    private val BIND_RETRY_DURATION = Duration.ofSeconds(1)

    @VisibleForTesting
    internal const val MAX_BIND_ATTEMPTS = 3

    /**
     * Generate an [IConnectedDeviceManager] wrapper around the [fromFeatureCoordinator] to support
     * legacy functionality.
     */
    @VisibleForTesting
    internal fun createConnectedDeviceManagerWrapper(
      fromFeatureCoordinator: IFeatureCoordinator
    ): IConnectedDeviceManager {

      return object : IConnectedDeviceManager.Stub() {
        @Throws(RemoteException::class)
        override fun getActiveUserConnectedDevices(): List<ConnectedDevice> {
          return fromFeatureCoordinator.connectedDevicesForDriver
        }

        @Throws(RemoteException::class)
        override fun registerActiveUserConnectionCallback(callback: IConnectionCallback) {
          fromFeatureCoordinator.registerDriverConnectionCallback(callback)
        }

        @Throws(RemoteException::class)
        override fun unregisterConnectionCallback(callback: IConnectionCallback) {
          fromFeatureCoordinator.unregisterConnectionCallback(callback)
        }

        @Throws(RemoteException::class)
        override fun registerDeviceCallback(
          connectedDevice: ConnectedDevice,
          recipientId: ParcelUuid,
          callback: IDeviceCallback
        ) {
          fromFeatureCoordinator.registerDeviceCallback(connectedDevice, recipientId, callback)
        }

        @Throws(RemoteException::class)
        override fun unregisterDeviceCallback(
          connectedDevice: ConnectedDevice,
          recipientId: ParcelUuid,
          callback: IDeviceCallback
        ) {
          fromFeatureCoordinator.unregisterDeviceCallback(connectedDevice, recipientId, callback)
        }

        @Throws(RemoteException::class)
        override fun sendMessage(
          connectedDevice: ConnectedDevice,
          message: DeviceMessage
        ): Boolean {
          return fromFeatureCoordinator.sendMessage(connectedDevice, message)
        }

        @Throws(RemoteException::class)
        override fun registerDeviceAssociationCallback(callback: IDeviceAssociationCallback) {
          fromFeatureCoordinator.registerDeviceAssociationCallback(callback)
        }

        @Throws(RemoteException::class)
        override fun unregisterDeviceAssociationCallback(callback: IDeviceAssociationCallback) {
          fromFeatureCoordinator.unregisterDeviceAssociationCallback(callback)
        }

        @Throws(RemoteException::class)
        override fun registerOnLogRequestedListener(
          loggerId: Int,
          listener: IOnLogRequestedListener
        ) {
          fromFeatureCoordinator.registerOnLogRequestedListener(loggerId, listener)
        }

        @Throws(RemoteException::class)
        override fun unregisterOnLogRequestedListener(
          loggerId: Int,
          listener: IOnLogRequestedListener
        ) {
          fromFeatureCoordinator.unregisterOnLogRequestedListener(loggerId, listener)
        }

        @Throws(RemoteException::class)
        override fun processLogRecords(loggerId: Int, logRecords: ByteArray) {
          fromFeatureCoordinator.processLogRecords(loggerId, logRecords)
        }
      }
    }
  }
}
