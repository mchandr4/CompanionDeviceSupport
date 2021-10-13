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
package com.google.android.connecteddevice.system

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.google.android.companionprotos.SystemQuery
import com.google.android.companionprotos.SystemQueryType.DEVICE_NAME
import com.google.android.connecteddevice.api.Connector
import com.google.android.connecteddevice.api.Connector.Companion.SYSTEM_FEATURE_ID
import com.google.android.connecteddevice.api.Connector.QueryCallback
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.protobuf.ExtensionRegistryLite
import com.google.protobuf.InvalidProtocolBufferException
import java.nio.charset.StandardCharsets

/** Feature responsible for system queries. */
open class SystemFeature(
  context: Context,
  private val storage: ConnectedDeviceStorage,
  private val connector: Connector
) {

  private val bluetoothAdapter: BluetoothAdapter =
    context.getSystemService(BluetoothManager::class.java).adapter

  init {
    connector.featureId = SYSTEM_FEATURE_ID
    connector.callback =
      object : Connector.Callback {
        override fun onSecureChannelEstablished(device: ConnectedDevice) =
          onSecureChannelEstablishedInternal(device)

        override fun onQueryReceived(
          device: ConnectedDevice,
          queryId: Int,
          request: ByteArray,
          parameters: ByteArray?
        ) = onQueryReceivedInternal(device, queryId, request)
      }
  }

  /** Start the feature. */
  fun start() {
    logd(TAG, "Starting SystemFeature $SYSTEM_FEATURE_ID.")
    connector.connect()
  }

  /** Stop the feature. */
  fun stop() {
    logd(TAG, "Stopping SystemFeature $SYSTEM_FEATURE_ID.")
    connector.disconnect()
  }

  private fun onSecureChannelEstablishedInternal(device: ConnectedDevice) {
    logd(TAG, "Secure channel has been established. Issuing device name query.")
    val deviceNameQuery = SystemQuery.newBuilder().setType(DEVICE_NAME).build()
    connector.sendQuerySecurely(
      device,
      deviceNameQuery.toByteArray(),
      /* parameters= */ null,
      object : QueryCallback {
        override fun onSuccess(response: ByteArray?) {
          if (response?.isNotEmpty() != true) {
            loge(TAG, "Received a null or empty device name query response. Ignoring.")
            return
          }
          val deviceName = String(response, StandardCharsets.UTF_8)
          logd(TAG, "Updating device ${device.deviceId}'s name to $deviceName.")
          storage.updateAssociatedDeviceName(device.deviceId, deviceName)
        }
      }
    )
  }

  private fun onQueryReceivedInternal(
    device: ConnectedDevice,
    queryId: Int,
    request: ByteArray,
  ) {
    val query =
      try {
        SystemQuery.parseFrom(request, ExtensionRegistryLite.getEmptyRegistry())
      } catch (e: InvalidProtocolBufferException) {
        loge(TAG, "Unable to parse system query.", e)
        respondWithError(device, queryId)
        return
      }

    when (query.type) {
      DEVICE_NAME -> respondWithDeviceName(device, queryId)
      else -> {
        loge(TAG, "Received unknown query type ${query.type}. Responding with error.")
        respondWithError(device, queryId)
      }
    }
  }

  private fun respondWithDeviceName(device: ConnectedDevice, queryId: Int) {
    val deviceName = bluetoothAdapter.name
    logd(TAG, "Responding to query for device name with $deviceName.")
    connector.respondToQuerySecurely(
      device,
      queryId,
      deviceName != null,
      deviceName?.toByteArray(StandardCharsets.UTF_8)
    )
  }

  private fun respondWithError(device: ConnectedDevice, queryId: Int) =
    connector.respondToQuerySecurely(device, queryId, /* success= */ false, /* response= */ null)

  companion object {
    private const val TAG = "SystemFeature"
  }
}
