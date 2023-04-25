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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.IInterface
import android.os.Looper
import android.os.ParcelUuid
import android.os.RemoteException
import androidx.annotation.VisibleForTesting
import com.google.android.companionprotos.Query
import com.google.android.companionprotos.QueryResponse
import com.google.android.companionprotos.SystemQuery
import com.google.android.companionprotos.SystemQueryType
import com.google.android.connecteddevice.api.SafeConnector.AppNameCallback
import com.google.android.connecteddevice.api.SafeConnector.Companion.ACTION_BIND_FEATURE_COORDINATOR
import com.google.android.connecteddevice.api.SafeConnector.Companion.ACTION_QUERY_API_VERSION
import com.google.android.connecteddevice.api.SafeConnector.QueryCallback
import com.google.android.connecteddevice.api.external.ISafeBinderVersion
import com.google.android.connecteddevice.api.external.ISafeFeatureCoordinator
import com.google.android.connecteddevice.api.external.ISafeOnAssociatedDevicesRetrievedListener
import com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener
import com.google.android.connecteddevice.util.ByteUtils
import com.google.android.connecteddevice.util.Logger
import com.google.android.connecteddevice.util.SafeLog
import com.google.protobuf.ByteString
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

/**
 * Class for establishing and maintaining a connection between external features and the companion
 * device platform.
 *
 * @param context [Context] of the hosting process.
 * @param featureId Identifier of the feature that is running this connector.
 * @param callback Callback associated with this connector.
 * @param minSupportedVersion External feature's minimum supported Companion API version.
 */
class FeatureConnector (
  private val context: Context,
  override val featureId: ParcelUuid,
  override val callback: SafeConnector.Callback,
  private val minSupportedVersion: Int = 0
) : SafeConnector {

  @VisibleForTesting internal var bindAttempts = 0

  private val lock = ReentrantLock()

  private val retryHandler = Handler(Looper.getMainLooper())

  private val loggerId = Logger.getLogger().loggerId

  private val waitingForConnection = AtomicBoolean(true)

  private val queryIdGenerator = QueryIdGenerator()

  override val connectedDevices: List<String>
    get() = coordinatorProxy?.getConnectedDevices() ?: emptyList()

  /** [CompanionApiProxy] acting as a wrapper for feature coordinator calls */
  @VisibleForTesting internal var coordinatorProxy: CompanionApiProxy? = null

  @VisibleForTesting internal var platformVersion: Int? = null

  @VisibleForTesting
  internal val versionCheckConnection =
    object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName, service: IBinder) {
        if (service !is ISafeBinderVersion) {
          logd("Unexpected binder received from platform. Aborting.")
          callback.onFailedToConnect()
          return
        } else if (minSupportedVersion > service.getVersion()) {
          loge("Incompatible client and platform versions detected. Aborting.")
          callback.onApiNotSupported()
          return
        } else {
          platformVersion = service.getVersion()
          bindAttempts = 0
          bindToService(ACTION_BIND_FEATURE_COORDINATOR, featureCoordinatorConnection)
        }
      }

      override fun onServiceDisconnected(name: ComponentName) {
        this@FeatureConnector.onServiceDisconnected()
      }

      override fun onNullBinding(name: ComponentName) {
        logd("Connecting to a companion platform of version 0.")
        platformVersion = 0
        bindAttempts = 0
        bindToService(ACTION_BIND_FEATURE_COORDINATOR, featureCoordinatorConnection)
      }

      override fun onBindingDied(name: ComponentName?) {
        this@FeatureConnector.onBindingDied()
      }
    }

  @VisibleForTesting
  internal val featureCoordinatorConnection =
    object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName, service: IBinder) {
        logd("Feature coordinator binder connected.")
        val platformVersion = platformVersion
        if (platformVersion == null) {
          loge("Incompatible companion platform version. Aborting.")
          return
        }
        coordinatorProxy =
          when {
            platformVersion > 0 ->
              SafeApiProxy(
                ISafeFeatureCoordinator.Stub.asInterface(service),
                featureId,
                callback,
                loggerId,
                platformVersion
              )
            platformVersion == 0 ->
              LegacyApiProxy(
                IFeatureCoordinator.Stub.asInterface(service),
                featureId,
                callback,
                loggerId,
                platformVersion
              )
            else -> {
              loge("Incompatible companion platform version. Aborting.")
              return
            }
          }
        logd("FeatureCoordinator initialized.")
        waitingForConnection.set(false)
        callback.onConnected()
      }

      override fun onServiceDisconnected(name: ComponentName) {
        this@FeatureConnector.onServiceDisconnected()
      }

      override fun onNullBinding(name: ComponentName) {
        this@FeatureConnector.onNullBinding()
      }

      override fun onBindingDied(name: ComponentName?) {
        this@FeatureConnector.onBindingDied()
      }
    }

  init {
    logd("Initiating connection to companion platform.")
    bindToService(ACTION_QUERY_API_VERSION, versionCheckConnection)
  }

  private fun bindToService(action: String, serviceConnection: ServiceConnection) {
    val intent = resolveIntent(action)

    if (intent == null) {
      loge("No services found supporting companion device. Aborting.")
      callback.onFailedToConnect()
      return
    }

    val success = context.bindService(intent, serviceConnection, /* flag= */ 0)
    if (success) {
      logd("Successfully started binding with ${intent.action}.")
      return
    }

    bindAttempts++
    if (bindAttempts > MAX_BIND_ATTEMPTS) {
      loge("Failed to bind to service after $bindAttempts attempts. Aborting.")
      waitingForConnection.set(false)
      callback.onFailedToConnect()
      return
    }
    retryHandler.postDelayed(
      {
        logw("Unable to bind to service with action ${intent.action}. Trying again.")
        bindToService(action, serviceConnection)
      },
      BIND_RETRY_DURATION.toMillis()
    )
  }

  private fun resolveIntent(action: String): Intent? {
    val packageManager = context.packageManager
    val intent = Intent(action)
    val services = packageManager.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY)
    if (services.isEmpty()) {
      logw("There are no services supporting the $action action installed on this device.")
      return null
    }
    logd("Found ${services.size} service(s) supporting $action. Choosing the first one.")
    val service = services[0]
    return intent.apply {
      component = ComponentName(service.serviceInfo.packageName, service.serviceInfo.name)
    }
  }

  override fun cleanUp() {
    logd("Disconnecting from the companion platform.")
    coordinatorProxy?.cleanUp()
    coordinatorProxy = null
    unbindFromService()
    callback.onDisconnected()
  }

  private fun unbindFromService() {
    retryHandler.removeCallbacksAndMessages(/* token= */ null)
    try {
      context.unbindService(featureCoordinatorConnection)
    } catch (e: IllegalArgumentException) {
      logw("Attempted to unbind an already unbound service.")
    }
    waitingForConnection.set(false)
  }

  private fun onServiceDisconnected() {
    logd("Service has disconnected. Cleaning up.")
    cleanUp()
  }

  private fun onNullBinding() {
    loge("Received a null binding for FeatureCoordinator. Unbinding service.")
    unbindFromService()
    callback.onFailedToConnect()
  }

  private fun onBindingDied() {
    logw("FeatureCoordinator binding died. Unbinding service.")
    unbindFromService()
    callback.onDisconnected()
  }

  override fun sendMessage(deviceId: String, message: ByteArray) {
    val coordinatorProxy = coordinatorProxy
    if (coordinatorProxy == null) {
      loge("Unable to send message with a null feature coordinator.")
      callback.onMessageFailedToSend(deviceId, message, isTransient = true)
      return
    }
    if (!connectedDevices.contains(deviceId)) {
      loge("No matching device found with id $deviceId when trying to send secure message.")
      callback.onMessageFailedToSend(deviceId, message, isTransient = false)
      return
    }
    if (!coordinatorProxy.sendMessage(deviceId, message)) {
      loge("Feature coordinator failed to send message.")
      callback.onMessageFailedToSend(deviceId, message, isTransient = false)
    }
  }

  override fun sendQuery(
    deviceId: String,
    request: ByteArray,
    parameters: ByteArray?,
    queryCallback: QueryCallback
  ) {
    val coordinatorProxy = coordinatorProxy
    if (coordinatorProxy == null) {
      loge("Unable to send query with a null feature coordinator.")
      queryCallback.onQueryFailedToSend(isTransient = false)
      return
    }
    if (!connectedDevices.contains(deviceId)) {
      loge("No matching device found with id $deviceId when trying to send a query.")
      queryCallback.onQueryFailedToSend(isTransient = false)
      return
    }
    val id = queryIdGenerator.next()
    val builder =
      Query.newBuilder()
        .setId(id)
        .setSender(ByteString.copyFrom(ByteUtils.uuidToBytes(featureId.uuid)))
        .setRequest(ByteString.copyFrom(request))
    if (parameters != null) {
      builder.parameters = ByteString.copyFrom(parameters)
    }
    logd("Sending secure query with id $id.")
    if (!coordinatorProxy.sendMessage(deviceId, builder.build().toByteArray())) {
      loge("Error while sending query.")
      queryCallback.onQueryFailedToSend(isTransient = false)
    }
    coordinatorProxy.queryCallbacks[id] = queryCallback
  }

  override fun respondToQuery(
    deviceId: String,
    queryId: Int,
    success: Boolean,
    response: ByteArray?
  ) {
    val coordinatorProxy = coordinatorProxy
    if (coordinatorProxy == null) {
      loge("Unable to respond to query with a null feature coordinator.")
      return
    }
    val recipientId = coordinatorProxy.queryResponseRecipients.remove(queryId)
    if (recipientId == null) {
      loge("Unable to send response to unrecognized query $queryId.")
      return
    }
    val builder = QueryResponse.newBuilder().setQueryId(queryId).setSuccess(success)
    if (response != null) {
      builder.response = ByteString.copyFrom(response)
    }
    val queryResponse = builder.build()
    logd("Sending response to query $queryId to $recipientId.")
    if (!coordinatorProxy.sendMessage(deviceId, queryResponse.toByteArray())) {
      loge("Feature coordinator failed to send query.")
      callback.onMessageFailedToSend(deviceId, queryResponse.toByteArray(), isTransient = false)
    }
  }

  override fun retrieveCompanionApplicationName(
    deviceId: String,
    appNameCallback: AppNameCallback
  ) {
    val systemQuery = SystemQuery.newBuilder().setType(SystemQueryType.APP_NAME).build()
    sendQuery(
      deviceId,
      systemQuery.toByteArray(),
      parameters = null,
      object : QueryCallback {
        override fun onSuccess(response: ByteArray?) {
          if (response == null || response.isEmpty()) {
            loge("Received a null or empty response for the application name.")
            appNameCallback.onError()
            return
          }
          val appName = String(response, StandardCharsets.UTF_8)
          logd("Received successful app name query response of $appName.")
          appNameCallback.onNameReceived(appName)
        }

        override fun onError(response: ByteArray?) {
          loge("Received an error response when querying for application name.")
          appNameCallback.onError()
        }

        override fun onQueryFailedToSend(isTransient: Boolean) {
          loge("Failed to send the query for the application name.")
          appNameCallback.onError()
        }
      }
    )
  }

  override fun retrieveAssociatedDevices(listener: IOnAssociatedDevicesRetrievedListener) {
    retrieveAssociatedDevicesInternal(listener)
  }

  override fun retrieveAssociatedDevices(listener: ISafeOnAssociatedDevicesRetrievedListener) {
    retrieveAssociatedDevicesInternal(listener)
  }

  private fun retrieveAssociatedDevicesInternal(listener: IInterface) {
    val coordinatorProxy = coordinatorProxy
    if (coordinatorProxy == null) {
      loge("Unable to retrieve associated devices with a null feature coordinator.")
      return
    }
    if (!coordinatorProxy.retrieveAssociatedDevices(listener)) {
      logw("Failed to retrieve associated devices.")
    }
  }

  private fun logd(message: String) {
    SafeLog.logd(TAG, "$message [Feature ID: $featureId]")
  }

  private fun logw(message: String) {
    SafeLog.logw(TAG, "$message [Feature ID: $featureId]")
  }

  private fun loge(message: String, e: Exception? = null) {
    SafeLog.loge(TAG, "$message [Feature ID: $featureId]", e)
  }

  companion object {
    private const val TAG = "FeatureConnector"

    private val BIND_RETRY_DURATION = Duration.ofSeconds(1)

    @VisibleForTesting internal const val MAX_BIND_ATTEMPTS = 3

    // TODO(alwa) Move this (and QueryIdGenerator in CompanionConnector) to its own internal class.
    /** A generator of unique IDs for queries. */
    private class QueryIdGenerator {
      private val messageId = AtomicInteger(0)
      fun next(): Int {
        val current = messageId.getAndIncrement()
        messageId.compareAndSet(Int.MAX_VALUE, 0)
        return current
      }
    }
  }
}
