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
package com.google.android.connecteddevice.core

import androidx.annotation.VisibleForTesting
import com.google.android.companionprotos.FeatureSupportResponse
import com.google.android.companionprotos.FeatureSupportStatus
import com.google.android.companionprotos.Query
import com.google.android.companionprotos.QueryResponse
import com.google.android.companionprotos.SystemQuery
import com.google.android.companionprotos.SystemQueryType
import com.google.android.connecteddevice.api.Connector.Companion.SYSTEM_FEATURE_ID
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.model.DeviceMessage.OperationType
import com.google.android.connecteddevice.util.ByteUtils
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logi
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * A cache for System Queries.
 *
 * We assume the responses for system query from a remote device are stable across queries, so their
 * results can be cached on IHU. Caching the responses for system queries enables short-circuiting
 * the query/response message flow when it's queried again.
 *
 * Responses of value `null` are ignored.
 */
interface SystemQueryCache {
  /**
   * Optionally caches the device message.
   *
   * Inspects the device message to check if it is a system query.
   */
  fun maybeCacheResponse(device: ConnectedDevice, message: DeviceMessage)

  /**
   * Checks if the message can be responded to by cached query response.
   *
   * Generates a response for the device message if a response has been cached. Returns `null` if
   * there is no cached response, or the message is not a query.
   */
  fun getCachedResponse(device: ConnectedDevice, message: DeviceMessage): DeviceMessage?

  /**
   * Checks if the feature is supported on the phone side.
   *
   * Returns `null` if the response is not cached.
   */
  fun isFeatureSupported(device: ConnectedDevice, featureId: UUID): Boolean?

  /** Clears cached response from device. */
  fun clearCache(device: ConnectedDevice)

  companion object {
    fun create(): SystemQueryCache = SystemQueryCacheImpl()
  }
}

internal class SystemQueryCacheImpl : SystemQueryCache {
  @VisibleForTesting internal val deviceCaches = mutableMapOf<UUID, DeviceSystemQueryCache>()

  override fun maybeCacheResponse(device: ConnectedDevice, message: DeviceMessage) {
    deviceCaches
      .getOrPut(UUID.fromString(device.deviceId)) { DeviceSystemQueryCache() }
      .cache(message)
  }

  override fun getCachedResponse(device: ConnectedDevice, message: DeviceMessage): DeviceMessage? {
    val deviceCache =
      deviceCaches.getOrPut(UUID.fromString(device.deviceId)) { DeviceSystemQueryCache() }
    return deviceCache.getCached(message)
  }

  override fun isFeatureSupported(device: ConnectedDevice, featureId: UUID): Boolean? {
    val deviceCache =
      deviceCaches.getOrPut(UUID.fromString(device.deviceId)) { DeviceSystemQueryCache() }
    return deviceCache.isFeatureSupported(featureId)
  }

  override fun clearCache(device: ConnectedDevice) {
    logi(TAG, "Clearing cache for $device.")
    val deviceCache = deviceCaches.remove(UUID.fromString(device.deviceId))
    deviceCache?.clear()
  }

  companion object {
    private const val TAG = "SystemQueryCache"
  }
}

/** Caches system query responses from a device. */
internal class DeviceSystemQueryCache {
  @VisibleForTesting internal var appName: String? = null
  @VisibleForTesting internal var deviceName: String? = null
  @VisibleForTesting internal val supportedFeatures = mutableSetOf<UUID>()

  // This map tracks the SystemQueryType sent by each <featureId, queryId>.
  //
  // This map is necessary due to the way the queries are answered: when a feature makes a system
  // query, it sends the query with its featureId as `query.sender`, and internally tracks the
  // answer with `queryId`. When the response is received, it's routed to `query.sender` with the
  // same `queryId`. The type of the system query is only known to the sender.
  // So here by inspecting the query, we can track the type of the response to parse it properly.
  @VisibleForTesting
  internal val trackedQueryTypes = mutableMapOf<Pair<UUID, Int>, SystemQueryType>()

  /**
   * Attempts to cache an inbound message if it was a system query response.
   *
   * Parses the message to inspect its message type. Messages that are not system query responses
   * will be ignored. Infers the type of the system query response based on previous outbound system
   * queries. Caches the response if the type can be inferred.
   */
  fun cache(message: DeviceMessage) {
    if (!message.isSystemQueryResponse()) {
      // Do not log here - we check every message. It'd be very spammy.
      return
    }

    val queryResponse = message.parseMessageAsQueryResponse() ?: return

    val querySender = message.recipient
    val queryId = queryResponse.queryId

    val systemQueryType = trackedQueryTypes.remove(Pair(querySender, queryId))
    if (systemQueryType == null) {
      // All outgoing system queries should be cached so an unrecognized query response is
      // unexpected. Log in detail to debug.
      loge(
        TAG,
        "Received SystemQuery response with unrecognized query type. " +
          "Intended receiver is $querySender with $queryId.",
      )
      return
    }

    // Only query types intended for the phone side are listed.
    when (systemQueryType) {
      SystemQueryType.APP_NAME -> {
        appName = String(queryResponse.response.toByteArray(), StandardCharsets.UTF_8)
        logi(TAG, "Caching app name $appName.")
      }
      SystemQueryType.DEVICE_NAME -> {
        deviceName = String(queryResponse.response.toByteArray(), StandardCharsets.UTF_8)
        logi(TAG, "Caching device name $deviceName.")
      }
      SystemQueryType.IS_FEATURE_SUPPORTED -> {
        val featureSupportResponse = FeatureSupportResponse.parseFrom(queryResponse.response)
        logi(TAG, "Caching feature support response $featureSupportResponse.")
        for (status in featureSupportResponse.getStatusesList()) {
          if (status.isSupported) {
            supportedFeatures.add(UUID.fromString(status.featureId))
            logi(TAG, "Caching feature ${status.featureId} is supported.")
          } else {
            logi(TAG, "Feature ${status.featureId} is not supported.")
          }
        }
      }
      else -> {
        loge(TAG, "Could not cache unspported system query type $systemQueryType.")
      }
    }
  }

  /**
   * Attempts to generate an inbound message as the response to the outbound system query.
   *
   * Returns `null` if there is no cached response, or the message is not a system query.
   *
   * Parses the message to inspect its message type. Messages that are not system queries are
   * ignored. If the query does not have a cached response, internally tracks the query type so that
   * the eventual inbound response can be cached.
   */
  fun getCached(message: DeviceMessage): DeviceMessage? {
    if (!message.isSystemQuery()) {
      // Do not log here - we check every message. It'd be very spammy.
      return null
    }
    logi(TAG, "Looking for cached response for $message.")

    val query = message.parseMessageAsQuery() ?: return null
    val queryId = query.id
    val sender = ByteUtils.bytesToUUID(query.sender.toByteArray()) ?: return null

    val systemQuery = query.parseRequestAsSystemQuery() ?: return null
    val type = systemQuery.type

    val cachedResponse: DeviceMessage? =
      when (type) {
        SystemQueryType.APP_NAME -> handleAppNameSystemQuery(sender, queryId)
        SystemQueryType.DEVICE_NAME -> handleDeviceNameSystemQuery(sender, queryId)
        SystemQueryType.IS_FEATURE_SUPPORTED ->
          handleIsFeatureSupportedSystemQuery(sender, queryId, systemQuery)
        else -> {
          loge(TAG, "Unspported system query ${type} in cache.")
          null
        }
      }

    if (cachedResponse != null) {
      logi(TAG, "Returning cached response for $type to <$sender, $queryId>.")
      return cachedResponse
    }

    // The query hasn't been cached yet, track the query type so that when the response is
    // received, it can be properly parsed.
    logi(TAG, "No cached response for $type. Tracking by <$sender, $queryId>.")
    trackedQueryTypes[Pair(sender, queryId)] = type

    return null
  }

  fun isFeatureSupported(featureId: UUID): Boolean? {
    // The current implementation only keeps the supported features. But the interface returns
    // an optional boolean that could indicate true/false/unknown.
    return if (featureId in supportedFeatures) true else null
  }

  private fun handleAppNameSystemQuery(sender: UUID, queryId: Int): DeviceMessage? {
    val cached = appName
    if (cached == null) {
      logi(TAG, "No cached response for system query AppName.")
      return null
    }

    logi(TAG, "Returning cached app name: $cached")
    return createCachedResponseForString(cached, sender, queryId)
  }

  private fun handleDeviceNameSystemQuery(sender: UUID, queryId: Int): DeviceMessage? {
    val cached = deviceName
    if (cached == null) {
      logi(TAG, "No cached response for system query DeviceName.")
      return null
    }

    logi(TAG, "Returning cached device name: $cached")
    return createCachedResponseForString(cached, sender, queryId)
  }

  private fun createCachedResponseForString(
    cached: String,
    sender: UUID,
    queryId: Int,
  ): DeviceMessage {
    // Generate a device message that contains a QueryResponse that contains the cached value.
    val queryResponse =
      QueryResponse.newBuilder()
        .setQueryId(queryId)
        .setSuccess(true)
        .setResponse(ByteString.copyFrom(cached.toByteArray()))
        .build()

    val deviceMessage =
      DeviceMessage.createIncomingMessage(
        /* recipient= */ sender,
        /* isMessageEncrypted= */ false,
        /* operationType= */ OperationType.QUERY_RESPONSE,
        /* message= */ queryResponse.toByteArray(),
        // OK to ignore original message size.
        /* originalMessageSize= */ 0,
      )

    return deviceMessage
  }

  private fun handleIsFeatureSupportedSystemQuery(
    sender: UUID,
    queryId: Int,
    systemQuery: SystemQuery,
  ): DeviceMessage? {
    val queriedFeatureIds =
      systemQuery.getPayloadsList().map {
        UUID.fromString(String(it.toByteArray(), StandardCharsets.UTF_8))
      }
    for (featureId in queriedFeatureIds) {
      // Default to false to allow features with a cached unsupported status to be queried again.
      if (featureId !in supportedFeatures) {
        logi(TAG, "Queried feature $featureId is not supported or cached. Returning null.")
        return null
      }
    }

    val cachedStatuses =
      queriedFeatureIds.map {
        logi(TAG, "Cached support status for feature $it.")
        FeatureSupportStatus.newBuilder()
          .setFeatureId(it.toString())
          // Since we ignore "unsupported" feature support status, all cached results are true.
          .setIsSupported(true)
          .build()
      }
    val featureSupportResponse =
      FeatureSupportResponse.newBuilder().addAllStatuses(cachedStatuses).build()

    val queryResponse =
      QueryResponse.newBuilder()
        .setQueryId(queryId)
        .setSuccess(true)
        .setResponse(ByteString.copyFrom(featureSupportResponse.toByteArray()))
        .build()

    val deviceMessage =
      DeviceMessage.createIncomingMessage(
        /* recipient= */ sender,
        /* isMessageEncrypted= */ false,
        /* operationType= */ OperationType.QUERY_RESPONSE,
        /* message= */ queryResponse.toByteArray(),
        // OK to ignore original message size.
        /* originalMessageSize= */ 0,
      )
    logi(TAG, "Returning cached support status for $queriedFeatureIds.")
    return deviceMessage
  }

  private fun DeviceMessage.parseMessageAsQuery(): Query? =
    try {
      Query.parseFrom(message)
    } catch (e: InvalidProtocolBufferException) {
      loge(TAG, "Unable to parse DeviceMessage as Query.", e)
      null
    }

  private fun DeviceMessage.isSystemQueryResponse(): Boolean =
    operationType == OperationType.QUERY_RESPONSE

  private fun DeviceMessage.isSystemQuery(): Boolean =
    operationType == OperationType.QUERY && recipient == SYSTEM_FEATURE

  private fun DeviceMessage.parseMessageAsQueryResponse(): QueryResponse? =
    try {
      QueryResponse.parseFrom(message)
    } catch (e: InvalidProtocolBufferException) {
      loge(TAG, "Unable to parse DeviceMessage as QueryResponse.", e)
      null
    }

  private fun Query.parseRequestAsSystemQuery(): SystemQuery? =
    try {
      SystemQuery.parseFrom(request)
    } catch (e: InvalidProtocolBufferException) {
      loge(TAG, "Unable to parse Query as SystemQuery.", e)
      null
    }

  fun clear() {
    logi(TAG, "Clearing cached responses.")

    trackedQueryTypes.clear()

    appName = null
    deviceName = null
    supportedFeatures.clear()
  }

  companion object {
    private const val TAG = "DeviceSystemQueryCache"
    private val SYSTEM_FEATURE: UUID = SYSTEM_FEATURE_ID.uuid
  }
}
