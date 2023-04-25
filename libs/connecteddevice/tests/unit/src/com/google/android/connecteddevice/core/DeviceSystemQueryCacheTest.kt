package com.google.android.connecteddevice.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.companionprotos.FeatureSupportResponse
import com.google.android.companionprotos.FeatureSupportStatus
import com.google.android.companionprotos.Query
import com.google.android.companionprotos.QueryResponse
import com.google.android.companionprotos.SystemQuery
import com.google.android.companionprotos.SystemQueryType
import com.google.android.connecteddevice.api.Connector.Companion.SYSTEM_FEATURE_ID
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.model.DeviceMessage.OperationType
import com.google.android.connecteddevice.util.ByteUtils
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.ByteString
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceSystemQueryCacheTest {

  private lateinit var cache: DeviceSystemQueryCache

  @Before
  fun setUp() {
    cache = DeviceSystemQueryCache()
  }

  @Test
  fun getCached_notSystemFeatureRecipient_noCachedMessage() {
    val message =
      DeviceMessage.createOutgoingMessage(
        /* recipient= */ UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        /* operationType= */ OperationType.QUERY,
        /* message= */ ByteArray(0),
      )

    assertThat(cache.getCached(message)).isNull()
  }

  @Test
  fun getCached_notQueryType_noCachedMessage() {
    val message =
      DeviceMessage.createOutgoingMessage(
        /* recipient= */ SYSTEM_FEATURE,
        /* isMessageEncrypted= */ true,
        /* operationType= */ OperationType.CLIENT_MESSAGE,
        /* message= */ ByteArray(0),
      )

    assertThat(cache.getCached(message)).isNull()
  }

  @Test
  fun getCached_nonParseableQuery_noCachedMessage() {
    val message =
      DeviceMessage.createOutgoingMessage(
        /* recipient= */ SYSTEM_FEATURE,
        /* isMessageEncrypted= */ true,
        /* operationType= */ OperationType.QUERY,
        // Query proto has non-optional fields so its serialization cannot be empty.
        /* message= */ ByteArray(0),
      )

    assertThat(cache.getCached(message)).isNull()
  }

  @Test
  fun getCached_noCachedResponse_queryTypeIsTracked() {
    val queryId = 4
    val sender = UUID.randomUUID()
    val message =
      createSystemQueryMessage(
        queryId = queryId,
        querySender = sender,
        systemQueryType = SystemQueryType.APP_NAME,
      )

    assertThat(cache.getCached(message)).isNull()

    val key = cache.trackedQueryTypes.keys.first()
    assertThat(key.first).isEqualTo(sender)
    assertThat(key.second).isEqualTo(queryId)
    assertThat(cache.trackedQueryTypes[key]!!).isEqualTo(SystemQueryType.APP_NAME)
  }

  @Test
  fun getCached_queryAppName_returnsResponse() {
    cache.appName = "appName"
    val queryMessage =
      createSystemQueryMessage(
        queryId = 4,
        querySender = UUID.randomUUID(),
        systemQueryType = SystemQueryType.APP_NAME,
      )

    val cached = checkNotNull(cache.getCached(queryMessage))
    val queryResponse = QueryResponse.parseFrom(cached.message)
    val cachedAppName = String(queryResponse.response.toByteArray(), StandardCharsets.UTF_8)
    assertThat(cachedAppName).isEqualTo("appName")
  }

  @Test
  fun getCached_queryDeviceName_returnsResponse() {
    cache.deviceName = "deviceName"
    val queryMessage =
      createSystemQueryMessage(
        queryId = 4,
        querySender = UUID.randomUUID(),
        systemQueryType = SystemQueryType.DEVICE_NAME,
      )

    val cached = checkNotNull(cache.getCached(queryMessage))
    val queryResponse = QueryResponse.parseFrom(cached.message)
    val cachedDeviceName = String(queryResponse.response.toByteArray(), StandardCharsets.UTF_8)
    assertThat(cachedDeviceName).isEqualTo("deviceName")
  }

  @Test
  fun getCached_querySupportedFeature_returnsResponse() {
    val supported = UUID.randomUUID()
    cache.supportedFeatures.add(supported)
    val queryMessage =
      createSystemQueryMessage(
        queryId = 4,
        querySender = UUID.randomUUID(),
        systemQueryType = SystemQueryType.IS_FEATURE_SUPPORTED,
        systemQueryPayloads = listOf(supported.toString().toByteArray()),
      )

    val cached = checkNotNull(cache.getCached(queryMessage))

    val queryResponse = QueryResponse.parseFrom(cached.message)
    val featureSupportResponse = FeatureSupportResponse.parseFrom(queryResponse.response)
    assertThat(featureSupportResponse.statusesList.size).isEqualTo(1)
    val status = featureSupportResponse.statusesList.first()
    assertThat(status.featureId).isEqualTo(supported.toString())
    assertThat(status.isSupported).isTrue()
  }

  @Test
  fun getCached_queryUnsupportedFeature_returnsNull() {
    // This feature ID is not cached.
    val unsupported = UUID.randomUUID()
    val queryMessage =
      createSystemQueryMessage(
        queryId = 4,
        querySender = UUID.randomUUID(),
        systemQueryType = SystemQueryType.IS_FEATURE_SUPPORTED,
        systemQueryPayloads = listOf(unsupported.toString().toByteArray()),
      )

    assertThat(cache.getCached(queryMessage)).isNull()
  }

  @Test
  fun getCached_queryMixedFeatures_returnsNull() {
    val supported = UUID.randomUUID()
    cache.supportedFeatures.add(supported)
    // This feature ID is not cached.
    val unsupported = UUID.randomUUID()
    val queryMessage =
      createSystemQueryMessage(
        queryId = 4,
        querySender = UUID.randomUUID(),
        systemQueryType = SystemQueryType.IS_FEATURE_SUPPORTED,
        systemQueryPayloads =
          listOf(unsupported.toString().toByteArray(), supported.toString().toByteArray()),
      )

    assertThat(cache.getCached(queryMessage)).isNull()
  }

  @Test
  fun cache_nonSystemQueryResponse_notCached() {
    val message =
      DeviceMessage.createIncomingMessage(
        /* recipient= */ UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        /* operationType= */ OperationType.CLIENT_MESSAGE,
        /* message= */ ByteArray(0),
        /* originalMessageSize= */ 0,
      )
    cache.cache(message)

    assertNothingIsCached()
  }

  @Test
  fun cache_nonParseableQueryResponse_notCached() {
    val message =
      DeviceMessage.createIncomingMessage(
        /* recipient= */ UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        /* operationType= */ OperationType.QUERY_RESPONSE,
        // Proto has non-optional fields so its serialization cannot be empty.
        /* message= */ ByteArray(0),
        /* originalMessageSize= */ 0,
      )
    cache.cache(message)

    assertNothingIsCached()
  }

  @Test
  fun cache_cachedAppName() {
    val queryId = 4
    val sender = UUID.randomUUID()
    val queryMessage =
      createSystemQueryMessage(
        queryId = queryId,
        querySender = sender,
        systemQueryType = SystemQueryType.APP_NAME,
      )

    // Try retrieving the cached response - allows the cache to track the query type.
    assertThat(cache.getCached(queryMessage)).isNull()
    // Then send the query response - the response should be cached.
    val responseMessage =
      createSystemQueryResponseMessage(
        queryId = queryId,
        querySender = sender,
        systemQueryPayload = "appName".toByteArray(),
      )
    cache.cache(responseMessage)

    assertThat(cache.appName).isEqualTo("appName")
  }

  @Test
  fun cache_cachedDeviceName() {
    val queryId = 4
    val sender = UUID.randomUUID()
    val queryMessage =
      createSystemQueryMessage(
        queryId = queryId,
        querySender = sender,
        systemQueryType = SystemQueryType.DEVICE_NAME,
      )

    // Try retrieving the cached response - allows the cache to track the query type.
    assertThat(cache.getCached(queryMessage)).isNull()
    // Then send the query response - the response should be cached.
    val responseMessage =
      createSystemQueryResponseMessage(
        queryId = queryId,
        querySender = sender,
        systemQueryPayload = "deviceName".toByteArray(),
      )
    cache.cache(responseMessage)

    assertThat(cache.deviceName).isEqualTo("deviceName")
  }

  @Test
  fun cache_cachedFeatureSupportStatus() {
    val queryId = 4
    val sender = UUID.randomUUID()
    val queriedFeature = UUID.randomUUID()
    val queryMessage =
      createSystemQueryMessage(
        queryId = queryId,
        querySender = sender,
        systemQueryType = SystemQueryType.IS_FEATURE_SUPPORTED,
        systemQueryPayloads = listOf(queriedFeature.toString().toByteArray()),
      )

    // Try retrieving the cached response - allows the cache to track the query type.
    assertThat(cache.getCached(queryMessage)).isNull()
    // Then send the query response - the response should be cached.
    val status =
      FeatureSupportStatus.newBuilder().run {
        featureId = queriedFeature.toString()
        isSupported = true
        build()
      }
    val featureSupportResponse =
      FeatureSupportResponse.newBuilder().run {
        addStatuses(status)
        build()
      }
    val responseMessage =
      createSystemQueryResponseMessage(
        queryId = queryId,
        querySender = sender,
        systemQueryPayload = featureSupportResponse.toByteArray(),
      )
    cache.cache(responseMessage)

    assertThat(queriedFeature in cache.supportedFeatures).isTrue()
  }

  @Test
  fun cache_unrecognizedSenderAndId_notCached() {
    val message =
      createSystemQueryResponseMessage(
        queryId = 4,
        querySender = UUID.randomUUID(),
        systemQueryPayload = "appName".toByteArray(),
      )

    cache.cache(message)

    assertNothingIsCached()
  }

  @Test
  fun clear_allCleared() {
    cache.appName = "appName"

    cache.clear()

    assertNothingIsCached()
  }

  private fun assertNothingIsCached() {
    assertThat(cache.appName).isNull()
    assertThat(cache.deviceName).isNull()
    assertThat(cache.supportedFeatures).isEmpty()
  }

  private fun createSystemQueryMessage(
    queryId: Int,
    querySender: UUID,
    systemQueryType: SystemQueryType,
    systemQueryPayloads: List<ByteArray> = emptyList(),
  ): DeviceMessage {
    val systemQuery =
      SystemQuery.newBuilder().run {
        type = systemQueryType
        addAllPayloads(systemQueryPayloads.map { ByteString.copyFrom(it) })
        build()
      }
    val query =
      Query.newBuilder().run {
        id = queryId
        sender = ByteString.copyFrom(ByteUtils.uuidToBytes(querySender))
        request = ByteString.copyFrom(systemQuery.toByteArray())
        build()
      }
    val message =
      DeviceMessage.createOutgoingMessage(
        /* recipient= */ SYSTEM_FEATURE,
        /* isMessageEncrypted= */ true,
        /* operationType= */ OperationType.QUERY,
        /* message= */ query.toByteArray(),
      )
    return message
  }

  private fun createSystemQueryResponseMessage(
    queryId: Int,
    querySender: UUID,
    systemQueryPayload: ByteArray,
  ): DeviceMessage {
    val queryResponse =
      QueryResponse.newBuilder().run {
        this.queryId = queryId
        success = true
        response = ByteString.copyFrom(systemQueryPayload)
        build()
      }
    val message =
      DeviceMessage.createIncomingMessage(
        /* recipient= */ querySender,
        /* isMessageEncrypted= */ true,
        /* operationType= */ OperationType.QUERY_RESPONSE,
        /* message= */ queryResponse.toByteArray(),
        /* originalMessageSize= */ 0,
      )
    return message
  }

  companion object {
    private val SYSTEM_FEATURE: UUID = SYSTEM_FEATURE_ID.uuid
  }
}
