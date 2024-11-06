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

import android.os.IInterface
import android.os.ParcelUuid
import android.os.RemoteException
import androidx.annotation.VisibleForTesting
import com.google.android.companionprotos.DeviceMessageProto
import com.google.android.companionprotos.Query
import com.google.android.companionprotos.QueryResponse
import com.google.android.connecteddevice.api.SafeConnector.QueryCallback
import com.google.android.connecteddevice.api.external.ISafeConnectionCallback
import com.google.android.connecteddevice.api.external.ISafeDeviceCallback
import com.google.android.connecteddevice.api.external.ISafeFeatureCoordinator
import com.google.android.connecteddevice.api.external.ISafeOnAssociatedDevicesRetrievedListener
import com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.util.ByteUtils
import com.google.android.connecteddevice.util.Logger
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logw
import com.google.protobuf.InvalidProtocolBufferException
import java.util.concurrent.ConcurrentHashMap

/**
 * Wrapper for SafeFeatureCoordinator APIs used to make sure that the Companion app's version is
 * compatible with the APIs it is attempting to call before actually making the call.
 */
class SafeApiProxy(
  private val featureCoordinator: ISafeFeatureCoordinator,
  private val recipientId: ParcelUuid,
  private val connectorCallback: SafeConnector.Callback,
  private val loggerId: Int,
  private val platformVersion: Int,
) : CompanionApiProxy {

  // queryId -> callback
  override val queryCallbacks: MutableMap<Int, QueryCallback> = ConcurrentHashMap()

  // queryId -> original sender for response
  override val queryResponseRecipients: MutableMap<Int, ParcelUuid> = ConcurrentHashMap()

  private val connectionCallback =
    object : ISafeConnectionCallback.Stub() {
      override fun onDeviceConnected(deviceId: String) {
        logd(TAG, "Device ${deviceId} has connected. Notifying callback.")
        connectorCallback.onDeviceConnected(deviceId)

        logd(TAG, "Registering device callback for $recipientId on device ${deviceId}.")
        featureCoordinator.registerDeviceCallback(deviceId, recipientId, deviceCallback)
      }

      override fun onDeviceDisconnected(deviceId: String) {
        logd(TAG, "Device ${deviceId} has disconnected. Notifying callback.")
        connectorCallback.onDeviceDisconnected(deviceId)

        logd(TAG, "Unregistering device callback for $recipientId on device ${deviceId}.")
        featureCoordinator.unregisterDeviceCallback(deviceId, recipientId, deviceCallback)
      }
    }

  @VisibleForTesting
  internal val deviceCallback =
    object : ISafeDeviceCallback.Stub() {
      override fun onSecureChannelEstablished(deviceId: String) {
        logd(TAG, "Secure channel has been established on ${deviceId}. Notifying callback.")
        connectorCallback.onSecureChannelEstablished(deviceId)
      }

      override fun onMessageReceived(deviceId: String, deviceMessage: ByteArray) {
        processIncomingMessage(deviceId, deviceMessage)
      }

      override fun onDeviceError(deviceId: String, error: Int) {
        logw(TAG, "Received a device error of $error from ${deviceId}.")
        connectorCallback.onDeviceError(deviceId, error)
      }
    }

  private fun processIncomingMessage(deviceId: String, deviceMessage: ByteArray) {
    val parsedMessage =
      try {
        DeviceMessageProto.Message.parseFrom(deviceMessage)
      } catch (e: InvalidProtocolBufferException) {
        loge(TAG, "Cannot parse device message to send.", e)
        return
      }
    val operationType = DeviceMessage.OperationType.fromValue(parsedMessage.operation.number)
    val message = parsedMessage.payload.toByteArray()
    when (operationType) {
      DeviceMessage.OperationType.CLIENT_MESSAGE -> {
        logd(TAG, "Received client message. Passing on to feature.")
        connectorCallback.onMessageReceived(deviceId, message)
        return
      }
      DeviceMessage.OperationType.QUERY -> {
        try {
          val query = Query.parseFrom(message)
          logd(TAG, "Received a new query with id ${query.id}. Passing on to feature.")
          val sender = ParcelUuid(ByteUtils.bytesToUUID(query.sender.toByteArray()))
          queryResponseRecipients[query.id] = sender
          connectorCallback.onQueryReceived(
            deviceId,
            query.id,
            query.request.toByteArray(),
            query.parameters.toByteArray(),
          )
        } catch (e: InvalidProtocolBufferException) {
          loge(TAG, "Unable to parse query.", e)
        }
        return
      }
      DeviceMessage.OperationType.QUERY_RESPONSE -> {
        try {
          val response = QueryResponse.parseFrom(message)
          logd(TAG, "Received a query response. Issuing registered callback.")
          val callback = queryCallbacks.remove(response.queryId)
          if (callback == null) {
            loge(TAG, "Unable to locate callback for query ${response.queryId}.")
            return
          }
          if (response.success) {
            callback.onSuccess(response.response.toByteArray())
          } else {
            callback.onError(response.response.toByteArray())
          }
        } catch (e: InvalidProtocolBufferException) {
          loge(TAG, "Unable to parse query response.", e)
        }
        return
      }
      else -> loge(TAG, "Received unknown type of message: $operationType. Ignoring.")
    }
  }

  override val listener =
    object : ISafeOnLogRequestedListener.Stub() {
      override fun onLogRecordsRequested() {
        val loggerBytes = Logger.getLogger().toByteArray()
        if (!processLogRecords(loggerId, loggerBytes)) {
          logw(TAG, "Failed to process log records for logger $loggerId.")
        }
      }
    }

  init {
    if (platformVersion >= 1) {
      featureCoordinator.registerConnectionCallback(connectionCallback)

      val connectedDevices = getConnectedDevices() ?: emptyList()
      for (deviceId in connectedDevices) {
        featureCoordinator.registerDeviceCallback(deviceId, recipientId, deviceCallback)
      }

      featureCoordinator.registerOnLogRequestedListener(loggerId, listener)
    } else {
      loge(TAG, "Feature coordinator created by outdated Companion platform. No-op")
    }
  }

  override fun getConnectedDevices(): List<String>? {
    val introducedVersion = 1
    if (platformVersion < introducedVersion) {
      logd(TAG, "getConnectedDevices invoked by outdated Companion platform.")
      return null
    }
    return featureCoordinator.getConnectedDevices()
  }

  override fun sendMessage(deviceId: String, message: ByteArray): Boolean {
    val introducedVersion = 1
    if (platformVersion < introducedVersion) {
      logd(TAG, "sendMessage invoked by outdated Companion platform.")
      return false
    }
    return try {
      featureCoordinator.sendMessage(deviceId, message)
    } catch (e: RemoteException) {
      loge(TAG, "sendMessage failed with RemoteException.", e)
      false
    }
  }

  override fun processLogRecords(loggerId: Int, logRecords: ByteArray): Boolean {
    val introducedVersion = 1
    if (platformVersion < introducedVersion) {
      logd(TAG, "processLogRecords invoked by outdated Companion platform.")
      return false
    }
    try {
      featureCoordinator.processLogRecords(loggerId, logRecords)
    } catch (e: RemoteException) {
      loge(TAG, "Failed to send log records for logger $loggerId.", e)
      return false
    }
    return true
  }

  override fun retrieveAssociatedDevices(listener: IInterface): Boolean {
    val introducedVersion = 1
    if (listener !is ISafeOnAssociatedDevicesRetrievedListener) {
      logd(TAG, "retrieveAssociatedDevices invoked with incorrect callback type.")
      return false
    }
    if (platformVersion < introducedVersion) {
      logd(TAG, "retrieveAssociatedDevices invoked by outdated Companion platform.")
      return false
    }
    featureCoordinator.retrieveAssociatedDevices(listener)
    return true
  }

  override fun cleanUp() {
    if (platformVersion >= 1) {
      featureCoordinator.unregisterConnectionCallback(connectionCallback)

      val connectedDevices = getConnectedDevices() ?: emptyList()
      for (deviceId in connectedDevices) {
        featureCoordinator.unregisterDeviceCallback(deviceId, recipientId, deviceCallback)
      }

      featureCoordinator.unregisterOnLogRequestedListener(loggerId, listener)
    } else {
      logd(
        TAG,
        "Attempting to clean up feature coordinator through an outdated Companion platform.",
      )
    }
  }

  companion object {
    private const val TAG = "SafeApiProxy"
  }
}
