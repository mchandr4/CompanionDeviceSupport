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
import com.google.android.companionprotos.Query
import com.google.android.companionprotos.QueryResponse
import com.google.android.connecteddevice.api.CompanionApiProxy.Companion.LEGACY_VERSION
import com.google.android.connecteddevice.api.SafeConnector.QueryCallback
import com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.util.ByteUtils
import com.google.android.connecteddevice.util.Logger
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logw
import com.google.protobuf.InvalidProtocolBufferException
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/**
 * Wrapper for FeatureCoordinator APIs used to make sure that the Companion app's version is
 * compatible with the APIs it is attempting to call before actually making the call. This class
 * allows for backwards compatibility with legacy Companion platform versions, and will be deprecated
 * once all devices have Companion platform updated to version 1.
 */
class LegacyApiProxy (
  private val featureCoordinator: IFeatureCoordinator,
  private val recipientId: ParcelUuid,
  private val connectorCallback: SafeConnector.Callback,
  private val loggerId: Int,
  private val platformVersion: Int
) : CompanionApiProxy {

  // queryId -> callback
  override val queryCallbacks: MutableMap<Int, QueryCallback> = ConcurrentHashMap()

  // queryId -> original sender for response
  override val queryResponseRecipients: MutableMap<Int, ParcelUuid> = ConcurrentHashMap()

  private val connectionCallback =
    object : IConnectionCallback.Stub() {
      override fun onDeviceConnected(device: ConnectedDevice) {
        logd(TAG, "Device ${device.deviceId} has connected. Notifying callback.")
        connectorCallback.onDeviceConnected(device.deviceId)

        if (device.hasSecureChannel()) {
          connectorCallback.onSecureChannelEstablished(device.deviceId)
        }

        logd(TAG, "Registering device callback for $recipientId on device ${device.deviceId}.")
        featureCoordinator.registerDeviceCallback(device, recipientId, deviceCallback)
      }

      override fun onDeviceDisconnected(device: ConnectedDevice) {
        logd(TAG, "Device ${device.deviceId} has disconnected. Notifying callback.")
        connectorCallback.onDeviceDisconnected(device.deviceId)

        logd(TAG, "Unregistering device callback for $recipientId on device ${device.deviceId}.")
        featureCoordinator.unregisterDeviceCallback(device, recipientId, deviceCallback)
      }
    }

  @VisibleForTesting
  internal val deviceCallback =
    object : IDeviceCallback.Stub() {
      override fun onSecureChannelEstablished(device: ConnectedDevice) {
        logd(TAG, "Secure channel has been established on ${device.deviceId}. Notifying callback.")
        connectorCallback.onSecureChannelEstablished(device.deviceId)
      }

      override fun onMessageReceived(device: ConnectedDevice, message: DeviceMessage) {
        processIncomingMessage(device.deviceId, message)
      }

      override fun onDeviceError(device: ConnectedDevice, error: Int) {
        logw(TAG, "Received a device error of $error from ${device.deviceId}.")
        connectorCallback.onDeviceError(device.deviceId, error)
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
    if (platformVersion >= LEGACY_VERSION) {
      featureCoordinator.registerAllConnectionCallback(connectionCallback)

      val connectedDevices = getConnectedDevices() ?: emptyList()
      for (deviceId in connectedDevices) {
        val connectedDevice = getConnectedDeviceById(deviceId)
        featureCoordinator.registerDeviceCallback(connectedDevice, recipientId, deviceCallback)
      }

      featureCoordinator.registerOnLogRequestedListener(loggerId, listener)
    } else {
      logw(TAG, "Incompatible Companion platform version.")
    }
  }

  override fun getConnectedDevices(): List<String>? {
    if (platformVersion < LEGACY_VERSION) {
      logd(TAG, "getConnectedDevices invoked by outdated Companion platform.")
      return null
    }
    return featureCoordinator.getConnectedDevicesForDriver().map { it.deviceId }
  }

  override fun sendMessage(deviceId: String, message: ByteArray): Boolean {
    if (platformVersion < LEGACY_VERSION) {
      logd(TAG, "sendMessage invoked by outdated Companion platform.")
      return false
    }
    val connectedDevice = getConnectedDeviceById(deviceId)
    if (connectedDevice == null) {
      logw(TAG, "No connected device found with deviceId $deviceId. Cannot send message.")
      return false
    }
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        UUID.fromString(deviceId),
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.CLIENT_MESSAGE,
        message
      )
    return try {
      featureCoordinator.sendMessage(connectedDevice, deviceMessage)
    } catch (e: RemoteException) {
      loge(TAG, "sendMessage failed with RemoteException.", e)
      false
    }
  }

  private fun getConnectedDeviceById(deviceId: String): ConnectedDevice? {
    return featureCoordinator
      .getConnectedDevicesForDriver()
      .firstOrNull { it.deviceId == deviceId }
  }

  override fun processLogRecords(loggerId: Int, logRecords: ByteArray): Boolean {
    if (platformVersion < LEGACY_VERSION) {
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
    if (listener !is IOnAssociatedDevicesRetrievedListener) {
      logd(TAG, "retrieveAssociatedDevices invoked with incorrect callback type.")
      return false
    }
    if (platformVersion < LEGACY_VERSION) {
      logd(TAG, "retrieveAssociatedDevices invoked by outdated Companion platform.")
      return false
    }
    featureCoordinator.retrieveAssociatedDevices(listener)
    return true
  }

  private fun processIncomingMessage(deviceId: String, deviceMessage: DeviceMessage) {
    val operationType = deviceMessage.operationType
    val message = deviceMessage.message
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
            query.parameters.toByteArray()
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

  override fun cleanUp() {
    if (platformVersion >= LEGACY_VERSION) {
      featureCoordinator.unregisterConnectionCallback(connectionCallback)

      val connectedDevices = getConnectedDevices() ?: emptyList()
      for (deviceId in connectedDevices) {
        val connectedDevice = getConnectedDeviceById(deviceId)
        featureCoordinator.unregisterDeviceCallback(connectedDevice, recipientId, deviceCallback)
      }

      featureCoordinator.unregisterOnLogRequestedListener(loggerId, listener)
    } else {
      logw(TAG, "Incompatible Companion platform version.")
    }
  }

  companion object {
    private const val TAG = "LegacyApiProxy"
  }
}
