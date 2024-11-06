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
import com.google.android.companionprotos.DeviceVersionsResponse
import com.google.android.companionprotos.SystemQuery
import com.google.android.companionprotos.SystemQueryType.DEVICE_NAME
import com.google.android.companionprotos.SystemQueryType.DEVICE_OS
import com.google.android.companionprotos.SystemQueryType.USER_ROLE
import com.google.android.companionprotos.SystemUserRole
import com.google.android.companionprotos.SystemUserRoleResponse
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
import java.util.UUID
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Feature responsible for system queries.
 *
 * @param queryFeatureSupportOnConnection Feature IDs that should be queried when this SystemFeature
 *   is notified of a connected device. The support status should be cached internally so that when
 *   the support status is queried again later (by the actual feature), the result is immediately
 *   available.
 */
open class SystemFeature
// @VisibleForTesting
internal constructor(
  context: Context,
  private val storage: ConnectedDeviceStorage,
  private val connector: Connector,
  private val queryFeatureSupportOnConnection: List<UUID>,
) {

  private val bluetoothAdapter: BluetoothAdapter =
    context.getSystemService(BluetoothManager::class.java).adapter

  constructor(
    context: Context,
    storage: ConnectedDeviceStorage,
    connector: Connector,
  ) : this(
    context,
    storage,
    connector,
    listOf(
      // SecondDeviceSignInUrlFeature
      //   This feature is started by UI (not always running in the background), so it has a limited
      //   amount of time to initialize. We can speed up the process by preheating the system query
      //   cache, namely querying the feature status here so when the feature checks, the response
      //   is cached.
      UUID.fromString("524a5d28-b208-449c-bb54-cd89498d3b1b"),
      // RemoteSetupPreferenceFeature
      UUID.fromString("3f112c6f-37b3-4a73-8505-1268394b409d"),
    ),
  )

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
          parameters: ByteArray?,
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
    logd(TAG, "Secure channel has been established. ")
    queryDeviceName(device)
    queryDeviceOs(device)
    queryFeatureSupportStatusToPreheatCache(device)
  }

  private fun queryDeviceName(device: ConnectedDevice) {
    logd(TAG, "Issuing device name query.")
    val deviceNameQuery = SystemQuery.newBuilder().setType(DEVICE_NAME).build()
    connector.sendQuerySecurely(
      device,
      deviceNameQuery.toByteArray(),
      parameters = null,
      object : QueryCallback {
        override fun onSuccess(response: ByteArray) {
          if (response.isEmpty()) {
            loge(TAG, "Received an empty device name query response. Ignoring.")
            return
          }
          val deviceName = String(response, StandardCharsets.UTF_8)
          logd(TAG, "Updating device ${device.deviceId}'s name to $deviceName.")
          storage.updateAssociatedDeviceName(device.deviceId, deviceName)
        }
      },
    )
  }

  private fun queryDeviceOs(device: ConnectedDevice) {
    logd(TAG, "Issuing device OS query.")
    val deviceOSQuery = SystemQuery.newBuilder().setType(DEVICE_OS).build()
    connector.sendQuerySecurely(
      device,
      deviceOSQuery.toByteArray(),
      parameters = null,
      object : QueryCallback {
        override fun onSuccess(response: ByteArray) {
          if (response.isEmpty()) {
            loge(TAG, "Received an empty device OS query response. Ignoring.")
            return
          }
          val versionsResponse =
            try {
              DeviceVersionsResponse.parseFrom(response)
            } catch (e: InvalidProtocolBufferException) {
              loge(TAG, "Could not parse query response as proto.", e)
              return
            }
          val deviceOs = versionsResponse.os
          val deviceOsName = deviceOs.name
          val deviceOsVersion = versionsResponse.osVersion
          val deviceSdkVersion = versionsResponse.companionSdkVersion

          logd(TAG, "Updating device ${device.deviceId}'s OS to $deviceOsName.")
          storage.updateAssociatedDeviceOs(device.deviceId, deviceOs)

          logd(TAG, "Updating device ${device.deviceId}'s OS version to $deviceOsVersion.")
          storage.updateAssociatedDeviceOsVersion(device.deviceId, deviceOsVersion)

          logd(
            TAG,
            "Updating device ${device.deviceId}'s Companion SDK version to $deviceSdkVersion.",
          )
          storage.updateAssociatedDeviceCompanionSdkVersion(device.deviceId, deviceSdkVersion)
        }
      },
    )
  }

  private fun queryFeatureSupportStatusToPreheatCache(device: ConnectedDevice) {
    logd(TAG, "Issuing query for feature support status.")
    // Ignore the result because we are only calling to preheat the status cache.
    MainScope().launch {
      val unused = connector.queryFeatureSupportStatuses(device, queryFeatureSupportOnConnection)
    }
  }

  private fun onQueryReceivedInternal(device: ConnectedDevice, queryId: Int, request: ByteArray) {
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
      USER_ROLE -> respondWithUserRole(device, queryId)
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
      deviceName?.toByteArray(StandardCharsets.UTF_8),
    )
  }

  private fun respondWithUserRole(device: ConnectedDevice, queryId: Int) {
    val role =
      if (device.isAssociatedWithDriver) SystemUserRole.DRIVER else SystemUserRole.PASSENGER
    val response = SystemUserRoleResponse.newBuilder().setRole(role).build()
    logd(TAG, "Responding to query for user role with $role.")
    connector.respondToQuerySecurely(device, queryId, success = true, response.toByteArray())
  }

  private fun respondWithError(device: ConnectedDevice, queryId: Int) =
    connector.respondToQuerySecurely(device, queryId, success = false, response = null)

  companion object {
    private const val TAG = "SystemFeature"
  }
}
