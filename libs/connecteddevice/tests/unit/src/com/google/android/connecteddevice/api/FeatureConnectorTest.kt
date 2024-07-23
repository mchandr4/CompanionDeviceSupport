package com.google.android.connecteddevice.api

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.IInterface
import android.os.Looper
import android.os.ParcelUuid
import androidx.test.core.app.ApplicationProvider
import com.google.android.companionprotos.Query
import com.google.android.companionprotos.QueryResponse
import com.google.android.companionprotos.SystemQuery
import com.google.android.companionprotos.SystemQueryType
import com.google.android.connecteddevice.api.Connector.Companion.ACTION_BIND_FEATURE_COORDINATOR
import com.google.android.connecteddevice.api.SafeConnector.Companion.ACTION_BIND_SAFE_FEATURE_COORDINATOR
import com.google.android.connecteddevice.api.SafeConnector.Companion.ACTION_QUERY_API_VERSION
import com.google.android.connecteddevice.api.SafeConnector.QueryCallback
import com.google.android.connecteddevice.api.external.ISafeBinderVersion
import com.google.android.connecteddevice.api.external.ISafeFeatureCoordinator
import com.google.android.connecteddevice.api.external.ISafeOnAssociatedDevicesRetrievedListener
import com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener
import com.google.android.connecteddevice.core.util.mockToBeAlive
import com.google.android.connecteddevice.util.ByteUtils
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.ExtensionRegistryLite
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Answer
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class FeatureConnectorTest {
  private val mockPackageManager = mock<PackageManager>()

  private val context = FakeContext(mockPackageManager)

  private val mockCallback = mock<SafeConnector.Callback>()

  private val mockSafeFeatureCoordinator = mockToBeAlive<ISafeFeatureCoordinator>()

  private val mockFeatureCoordinator = mockToBeAlive<IFeatureCoordinator>()

  protected var mockPlatformVersion = 1

  private val testFeatureId = ParcelUuid(UUID.randomUUID())

  private val testDeviceId = UUID.randomUUID().toString()

  private val testMessage = ByteUtils.randomBytes(10)

  private val testCoordinatorProxy = spy(TestCompanionApiProxy(true, listOf(testDeviceId)))

  private lateinit var versionZeroConnector: FeatureConnector
  private lateinit var versionOneConnector: FeatureConnector
  private lateinit var versionTwoConnector: FeatureConnector

  @Before
  fun setUp() {
    versionZeroConnector = FeatureConnector(context, testFeatureId, minSupportedVersion = 0)
    versionOneConnector = FeatureConnector(context, testFeatureId, minSupportedVersion = 1)
    versionTwoConnector = FeatureConnector(context, testFeatureId, minSupportedVersion = 2)
  }

  @Test
  fun onConnect_negativeVersions_aborts() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = -1
    val connector = FeatureConnector(context, testFeatureId, minSupportedVersion = -1)
    connector.connect(mockCallback)

    assertThat(context.bindingActions)
      .containsExactly(ACTION_QUERY_API_VERSION, ACTION_BIND_SAFE_FEATURE_COORDINATOR)
    assertThat(connector.coordinatorProxy).isNull()
  }

  @Test
  fun onConnect_clientV1_platformV0_bindToFeatureCoordinator() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 0
    val connector = versionOneConnector
    connector.connect(mockCallback)

    assertThat(connector.coordinatorProxy is LegacyApiProxy).isTrue()
    assertThat(context.bindingActions)
      .containsExactly(ACTION_QUERY_API_VERSION, ACTION_BIND_FEATURE_COORDINATOR)
  }

  @Test
  fun onConnect_clientV1_platformV1_bindToFeatureCoordinator() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)

    assertThat(connector.coordinatorProxy is SafeApiProxy).isTrue()
    assertThat(context.bindingActions)
      .containsExactly(ACTION_QUERY_API_VERSION, ACTION_BIND_SAFE_FEATURE_COORDINATOR)
  }

  @Test
  fun onConnect_clientV1_platformV2_bindToFeatureCoordinator() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 2
    val connector = versionOneConnector
    connector.connect(mockCallback)

    assertThat(connector.coordinatorProxy is SafeApiProxy).isTrue()
    assertThat(context.bindingActions)
      .containsExactly(ACTION_QUERY_API_VERSION, ACTION_BIND_SAFE_FEATURE_COORDINATOR)
  }

  @Test
  fun onConnect_clientV2_platformV0_bindToFeatureCoordinator() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 0
    val connector = versionTwoConnector
    connector.connect(mockCallback)

    assertThat(connector.coordinatorProxy is LegacyApiProxy).isTrue()
    assertThat(context.bindingActions)
      .containsExactly(ACTION_QUERY_API_VERSION, ACTION_BIND_FEATURE_COORDINATOR)
  }

  @Test
  fun onConnect_clientV1_platformV1_incorrectServiceIssuesApiNotSupportedCallback() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val incorrectServiceContext = IncorrectServiceContext(mockPackageManager)
    val connector = FeatureConnector(incorrectServiceContext, testFeatureId, 1)
    connector.connect(mockCallback)

    assertThat(connector.coordinatorProxy).isNull()
    assertThat(connector.platformVersion).isNull()
    assertThat(incorrectServiceContext.bindingActions).containsExactly(ACTION_QUERY_API_VERSION)
    verify(mockCallback).onApiNotSupported()
  }

  @Test
  fun onConnect_clientV2_platformV1_issuesApiNotSupportedCallback() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionTwoConnector
    connector.connect(mockCallback)

    assertThat(connector.coordinatorProxy).isNull()
    assertThat(connector.platformVersion).isNull()
    assertThat(context.bindingActions).containsExactly(ACTION_QUERY_API_VERSION)
    verify(mockCallback).onApiNotSupported()
  }

  @Test
  fun onConnect_clientV2_platformV2_bindToFeatureCoordinator() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 2
    val connector = versionTwoConnector
    connector.connect(mockCallback)

    assertThat(connector.coordinatorProxy is SafeApiProxy).isTrue()
    assertThat(context.bindingActions)
      .containsExactly(ACTION_QUERY_API_VERSION, ACTION_BIND_SAFE_FEATURE_COORDINATOR)
  }

  @Test
  fun onConnect_clientV2_platformV3_bindToFeatureCoordinator() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 3
    val connector = versionTwoConnector
    connector.connect(mockCallback)

    assertThat(connector.coordinatorProxy is SafeApiProxy).isTrue()
    assertThat(context.bindingActions)
      .containsExactly(ACTION_QUERY_API_VERSION, ACTION_BIND_SAFE_FEATURE_COORDINATOR)
  }

  @Test
  fun onFailedToConnect_invokedIfNullIntent() {
    val nullIntentAnswer = Answer {
      val intent = it.arguments.first() as Intent
      val resolveInfo =
        ResolveInfo().apply { serviceInfo = ServiceInfo().apply { packageName = PACKAGE_NAME } }
      when (intent.action) {
        ACTION_BIND_FEATURE_COORDINATOR -> {
          resolveInfo.serviceInfo.name = FC_NAME
          listOf(resolveInfo)
        }
        else -> listOf()
      }
    }
    setQueryIntentServicesAnswer(nullIntentAnswer)
    mockPlatformVersion = 0
    val connector = versionOneConnector
    connector.connect(mockCallback)

    assertThat(connector.coordinatorProxy).isNull()
    verify(mockCallback).onFailedToConnect()
  }

  @Test
  fun onSuccessfulConnect_doesNotRetryBind() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)

    assertThat(connector.bindAttempts == 0).isTrue()
    assertThat(context.bindingActions)
      .containsExactly(ACTION_QUERY_API_VERSION, ACTION_BIND_SAFE_FEATURE_COORDINATOR)
  }

  @Test
  fun onFailedToConnect_invokedAfterBindingRetryLimitExceeded() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val failingContext = FailingContext(mockPackageManager)
    val shadowLooper = Shadows.shadowOf(Looper.getMainLooper())
    val connector = FeatureConnector(failingContext, testFeatureId, 1)
    connector.connect(mockCallback)

    repeat(FeatureConnector.MAX_BIND_ATTEMPTS) { shadowLooper.runToEndOfTasks() }

    assertThat(connector.coordinatorProxy).isNull()
    assertThat(failingContext.serviceConnection).contains(connector.versionCheckConnection)
    verify(mockCallback).onFailedToConnect()
  }

  @Test
  fun onFailedToConnect_invokedAfterBindingRetryLimitExceeded_afterSuccessfulVersionQueryBind() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 0
    val flakyContext = FailingLegacyContext(mockPackageManager)
    val shadowLooper = Shadows.shadowOf(Looper.getMainLooper())
    val connector = FeatureConnector(flakyContext, testFeatureId, 1)
    connector.connect(mockCallback)

    repeat(FeatureConnector.MAX_BIND_ATTEMPTS) { shadowLooper.runToEndOfTasks() }

    assertThat(connector.coordinatorProxy).isNull()
    assertThat(connector.bindAttempts).isEqualTo(FeatureConnector.MAX_BIND_ATTEMPTS + 1)
    assertThat(flakyContext.serviceConnection).contains(connector.versionCheckConnection)
    assertThat(flakyContext.serviceConnection).contains(connector.featureCoordinatorConnection)
    verify(mockCallback).onFailedToConnect()
  }

  @Test
  fun bindAttemptsRetainsCorrectValue_afterVersionQueryBindAndFeatureCoordinatorBindExperienceFailures() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 0
    val flakyContext = FlakyLegacyContext(mockPackageManager)
    val shadowLooper = Shadows.shadowOf(Looper.getMainLooper())
    val connector = FeatureConnector(flakyContext, testFeatureId, 1)
    connector.connect(mockCallback)

    // Should repeat once for version query bind attempt, then reset, then MAX_BIND_ATTEMPTS times
    // for feature coordinator bind attempt.
    repeat(FeatureConnector.MAX_BIND_ATTEMPTS + 1) { shadowLooper.runToEndOfTasks() }

    assertThat(connector.coordinatorProxy).isNull()
    assertThat(connector.bindAttempts).isEqualTo(FeatureConnector.MAX_BIND_ATTEMPTS + 1)
    assertThat(flakyContext.serviceConnection)
      .containsExactly(
        connector.versionCheckConnection,
        connector.versionCheckConnection,
        connector.featureCoordinatorConnection,
        connector.featureCoordinatorConnection,
        connector.featureCoordinatorConnection,
        connector.featureCoordinatorConnection,
      )
    verify(mockCallback).onFailedToConnect()
  }

  @Test
  fun onDisconnected_invokedOnVersionCheckServiceDisconnected() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)

    connector.versionCheckConnection.onServiceDisconnected(
      ComponentName(PACKAGE_NAME, VERSION_NAME)
    )
    verify(mockCallback).onDisconnected()
  }

  @Test
  fun onDisconnected_invokedOnVersionCheckDeadBinding() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)

    connector.versionCheckConnection.onBindingDied(ComponentName(PACKAGE_NAME, VERSION_NAME))
    verify(mockCallback).onDisconnected()
  }

  @Test
  fun onDisconnected_invokedOnFeatureCoordinatorServiceDisconnected() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)

    connector.featureCoordinatorConnection.onServiceDisconnected(
      ComponentName(PACKAGE_NAME, FC_NAME)
    )
    verify(mockCallback).onDisconnected()
  }

  @Test
  fun onFailedToConnect_invokedOnFeatureCoordinatorNullBinding() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)

    connector.featureCoordinatorConnection.onNullBinding(ComponentName(PACKAGE_NAME, FC_NAME))
    verify(mockCallback).onFailedToConnect()
  }

  @Test
  fun onDisconnected_invokedOnFeatureCoordinatorDeadBinding() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)

    connector.featureCoordinatorConnection.onBindingDied(ComponentName(PACKAGE_NAME, FC_NAME))
    verify(mockCallback).onDisconnected()
  }

  @Test
  fun onConnected_invokedAfterSuccessfulBind() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)

    assertThat(connector.coordinatorProxy is SafeApiProxy).isTrue()
    verify(mockCallback).onConnected()
  }

  @Test
  fun onConnected_invokedAfterSuccessfulLegacyBind() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 0
    val connector = versionOneConnector
    connector.connect(mockCallback)

    assertThat(connector.coordinatorProxy is LegacyApiProxy).isTrue()
    verify(mockCallback).onConnected()
  }

  @Test
  fun sendMessage_sendsMessage() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)
    connector.coordinatorProxy = testCoordinatorProxy
    connector.sendMessage(testDeviceId, testMessage)

    verify(testCoordinatorProxy).sendMessage(testDeviceId, testMessage)
  }

  @Test
  fun onMessageFailedToSend_invokedOnNullProxy() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)
    connector.coordinatorProxy = null
    connector.sendMessage(testDeviceId, testMessage)

    verify(mockCallback).onMessageFailedToSend(testDeviceId, testMessage, isTransient = true)
  }

  @Test
  fun onMessageFailedToSend_invokedWhenDeviceNotFound() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)
    connector.coordinatorProxy = testCoordinatorProxy
    val unexpectedDevice = UUID.randomUUID().toString()
    connector.sendMessage(unexpectedDevice, testMessage)

    assertThat(connector.connectedDevices.contains(unexpectedDevice)).isFalse()
    verify(mockCallback).onMessageFailedToSend(unexpectedDevice, testMessage, isTransient = false)
    verify(testCoordinatorProxy, never()).sendMessage(unexpectedDevice, testMessage)
  }

  @Test
  fun onMessageFailedToSend_invokedWhenSendMessageFails() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)
    val coordinatorProxy = TestCompanionApiProxy(false, listOf(testDeviceId))
    connector.coordinatorProxy = coordinatorProxy
    connector.sendMessage(testDeviceId, testMessage)

    verify(mockCallback).onMessageFailedToSend(testDeviceId, testMessage, isTransient = false)
  }

  @Test
  fun sendMessage_onMessageFailedToSend_invokedOnVersionMismatch() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 0
    val connector = versionOneConnector
    connector.connect(mockCallback)
    connector.sendMessage(testDeviceId, testMessage)

    verify(mockCallback).onMessageFailedToSend(testDeviceId, testMessage, isTransient = false)
  }

  @Test
  fun sendQuery_sendsQueryToOwnFeatureId() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)
    connector.coordinatorProxy = testCoordinatorProxy
    val request = ByteUtils.randomBytes(10)
    val parameters = ByteUtils.randomBytes(10)
    val queryCallback = mock<SafeConnector.QueryCallback>()
    connector.sendQuery(testDeviceId, request, parameters, queryCallback)

    argumentCaptor<ByteArray> {
      verify(testCoordinatorProxy).sendMessage(eq(testDeviceId), capture())
      val query = Query.parseFrom(firstValue, ExtensionRegistryLite.getEmptyRegistry())
      assertThat(query.request.toByteArray()).isEqualTo(request)
      assertThat(query.parameters.toByteArray()).isEqualTo(parameters)
    }
  }

  @Test
  fun sendQuery_addsCallbackToCallbacksMap() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)
    connector.coordinatorProxy = testCoordinatorProxy
    val request = ByteUtils.randomBytes(10)
    val parameters = ByteUtils.randomBytes(10)
    val queryCallback = mock<SafeConnector.QueryCallback>()
    connector.sendQuery(testDeviceId, request, parameters, queryCallback)

    assertThat(testCoordinatorProxy.queryCallbacks.containsValue(queryCallback)).isTrue()
  }

  @Test
  fun sendQuery_onQueryFailedToSend_invokedOnNullProxy() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)
    connector.coordinatorProxy = null
    val request = ByteUtils.randomBytes(10)
    val parameters = ByteUtils.randomBytes(10)
    val queryCallback = mock<SafeConnector.QueryCallback>()
    connector.sendQuery(testDeviceId, request, parameters, queryCallback)

    verify(queryCallback).onQueryFailedToSend(isTransient = false)
  }

  @Test
  fun sendQuery_onQueryFailedToSend_invokedOnDeviceIdNotFound() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)
    connector.coordinatorProxy = testCoordinatorProxy
    val request = ByteUtils.randomBytes(10)
    val parameters = ByteUtils.randomBytes(10)
    val queryCallback = mock<SafeConnector.QueryCallback>()
    connector.sendQuery(UUID.randomUUID().toString(), request, parameters, queryCallback)

    verify(queryCallback).onQueryFailedToSend(isTransient = false)
  }

  @Test
  fun respondToQuery_doesNotSendResponseWithNullProxy() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)
    connector.coordinatorProxy = null
    val response = ByteUtils.randomBytes(10)
    connector.respondToQuery(testDeviceId, 1, success = true, response)

    verify(testCoordinatorProxy, never()).sendMessage(any(), any())
  }

  @Test
  fun respondToQuery_doesNotSendResponseWithUnrecognizedQueryId() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)
    connector.coordinatorProxy = testCoordinatorProxy
    val nonExistentQueryId = 0
    val response = ByteUtils.randomBytes(10)
    connector.respondToQuery(testDeviceId, nonExistentQueryId, success = true, response)

    verify(testCoordinatorProxy, never()).sendMessage(any(), any())
  }

  @Test
  fun respondToQuery_sendsMessageWithNonNullResponse() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val queryId = 1
    val response = ByteUtils.randomBytes(10)
    val connector = versionOneConnector
    connector.connect(mockCallback)
    connector.coordinatorProxy = testCoordinatorProxy
    testCoordinatorProxy.queryResponseRecipients.put(queryId, testFeatureId)
    connector.respondToQuery(testDeviceId, queryId, success = true, response)

    argumentCaptor<ByteArray> {
      verify(testCoordinatorProxy).sendMessage(any(), capture())
      val queryResponse =
        QueryResponse.parseFrom(firstValue, ExtensionRegistryLite.getEmptyRegistry())
      assertThat(queryResponse.response.toByteArray()).isEqualTo(response)
    }
  }

  @Test
  fun respondToQuery_sendsMessageWithNullResponse() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val queryId = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)
    connector.coordinatorProxy = testCoordinatorProxy
    testCoordinatorProxy.queryResponseRecipients.put(queryId, testFeatureId)
    connector.respondToQuery(testDeviceId, queryId, success = true, null)

    argumentCaptor<ByteArray> {
      verify(testCoordinatorProxy).sendMessage(any(), capture())
      val queryResponse =
        QueryResponse.parseFrom(firstValue, ExtensionRegistryLite.getEmptyRegistry())
      assertThat(queryResponse.response).isEmpty()
    }
  }

  @Test
  fun respondToQuery_onMessageFailedToSend_invokedWhenSendMessageFailed() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)
    val proxy = spy(TestCompanionApiProxy(false, listOf(testDeviceId)))
    connector.coordinatorProxy = proxy
    val queryId = 1
    proxy.queryResponseRecipients.put(queryId, testFeatureId)
    val response = ByteUtils.randomBytes(10)
    connector.respondToQuery(testDeviceId, queryId, success = true, response)

    verify(mockCallback).onMessageFailedToSend(eq(testDeviceId), any(), eq(false))
  }

  @Test
  fun retrieveCompanionApplicationName_sendsAppNameQueryToSystemFeature() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)
    connector.coordinatorProxy = testCoordinatorProxy
    val callback = mock<SafeConnector.AppNameCallback>()

    connector.retrieveCompanionApplicationName(testDeviceId, callback)
    argumentCaptor<ByteArray> {
      verify(testCoordinatorProxy).sendMessage(eq(testDeviceId), capture())
      val query = Query.parseFrom(firstValue, ExtensionRegistryLite.getEmptyRegistry())
      assertThat(query.sender.toByteArray())
        .isEqualTo(ByteUtils.uuidToBytes(connector.featureId.uuid))
      val systemQuery =
        SystemQuery.parseFrom(query.request, ExtensionRegistryLite.getEmptyRegistry())
      assertThat(systemQuery.type).isEqualTo(SystemQueryType.APP_NAME)
    }
  }

  @Test
  fun retrieveCompanionApplicationName_onMessageFailedToSend_invokedWhenSendMessageFailed() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)
    val proxy = spy(TestCompanionApiProxy(false, listOf(testDeviceId)))
    connector.coordinatorProxy = proxy
    val appNameCallback = mock<SafeConnector.AppNameCallback>()
    connector.retrieveCompanionApplicationName(testDeviceId, appNameCallback)

    verify(appNameCallback).onError()
  }

  @Test
  fun retrieveAssociatedDevices_worksWithSafeListener() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)
    val mockListener = mock<ISafeOnAssociatedDevicesRetrievedListener>()
    connector.coordinatorProxy = testCoordinatorProxy

    connector.retrieveAssociatedDevices(mockListener)

    verify(testCoordinatorProxy).retrieveAssociatedDevices(mockListener)
  }

  @Test
  fun retrieveAssociatedDevices_worksWithLegacyListener() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 0
    val connector = versionOneConnector
    connector.connect(mockCallback)
    val mockListener = mock<IOnAssociatedDevicesRetrievedListener>()
    connector.coordinatorProxy = testCoordinatorProxy

    connector.retrieveAssociatedDevices(mockListener)

    verify(testCoordinatorProxy).retrieveAssociatedDevices(mockListener)
  }

  @Test
  fun retrieveAssociatedDevices_returnsSilentlyOnNullCoordinator() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)
    val mockListener = mock<ISafeOnAssociatedDevicesRetrievedListener>()
    connector.coordinatorProxy = null

    connector.retrieveAssociatedDevices(mockListener)

    verify(testCoordinatorProxy, never()).retrieveAssociatedDevices(mockListener)
  }

  @Test
  fun disconnect_cleansUpFeatureCoordinator() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)

    assertThat(connector.coordinatorProxy).isNotNull()
    connector.disconnect()
    assertThat(connector.coordinatorProxy).isNull()
  }

  @Test
  fun disconnect_cleansUpFeatureCoordinator_legacyPlatform() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 0
    val connector = versionOneConnector
    connector.connect(mockCallback)

    assertThat(connector.coordinatorProxy).isNotNull()
    connector.disconnect()
    assertThat(connector.coordinatorProxy).isNull()
  }

  @Test
  fun disconnect_callsCallbackDisconnected() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)

    connector.disconnect()
    verify(mockCallback).onDisconnected()
  }

  @Test
  fun disconnect_callsCallbackDisconnected_legacyPlatform() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 0
    val connector = versionOneConnector
    connector.connect(mockCallback)

    connector.disconnect()
    verify(mockCallback).onDisconnected()
  }

  @Test
  fun disconnect_unbindsFromContext() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 1
    val connector = versionOneConnector
    connector.connect(mockCallback)

    connector.disconnect()
    assertThat(context.unbindServiceConnection)
      .containsExactly(connector.featureCoordinatorConnection)
  }

  @Test
  fun disconnect_unbindsFromContext_legacyPlatform() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    mockPlatformVersion = 0
    val connector = versionOneConnector
    connector.connect(mockCallback)

    connector.disconnect()
    assertThat(context.unbindServiceConnection)
      .containsExactly(connector.featureCoordinatorConnection)
  }

  private fun setQueryIntentServicesAnswer(answer: Answer<List<ResolveInfo>>) {
    whenever(mockPackageManager.queryIntentServices(any(), any<Int>())).thenAnswer(answer)
  }

  private val defaultServiceAnswer = Answer {
    val intent = it.arguments.first() as Intent
    val resolveInfo =
      ResolveInfo().apply { serviceInfo = ServiceInfo().apply { packageName = PACKAGE_NAME } }
    when (intent.action) {
      ACTION_QUERY_API_VERSION -> {
        resolveInfo.serviceInfo.name = VERSION_NAME
        listOf(resolveInfo)
      }
      ACTION_BIND_SAFE_FEATURE_COORDINATOR -> {
        resolveInfo.serviceInfo.name = FC_NAME
        listOf(resolveInfo)
      }
      ACTION_BIND_FEATURE_COORDINATOR -> {
        resolveInfo.serviceInfo.name = FC_NAME
        listOf(resolveInfo)
      }
      else -> listOf()
    }
  }

  private open inner class FakeContext(val mockPackageManager: PackageManager) :
    ContextWrapper(ApplicationProvider.getApplicationContext()) {

    val bindingActions = mutableListOf<String>()

    var serviceConnection = mutableListOf<ServiceConnection>()

    var unbindServiceConnection = mutableListOf<ServiceConnection>()

    override fun getPackageManager(): PackageManager = mockPackageManager

    override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
      service.action?.let { bindingActions.add(it) }
      serviceConnection.add(conn)

      when (service.action) {
        ACTION_QUERY_API_VERSION -> bindVersionService(conn)
        ACTION_BIND_SAFE_FEATURE_COORDINATOR,
        ACTION_BIND_FEATURE_COORDINATOR -> bindFeatureCoordinatorService(conn)
      }

      return super.bindService(service, conn, flags)
    }

    protected fun bindVersionService(conn: ServiceConnection) {
      val mockPlatformVersion = this@FeatureConnectorTest.mockPlatformVersion
      val binder =
        object : ISafeBinderVersion.Stub() {
          override fun getVersion(): Int {
            return mockPlatformVersion
          }
        }

      val componentName = ComponentName(PACKAGE_NAME, VERSION_NAME)
      if (mockPlatformVersion == 0) {
        conn.onNullBinding(componentName)
      } else {
        conn.onServiceConnected(componentName, binder.asBinder())
      }
    }

    private fun bindFeatureCoordinatorService(conn: ServiceConnection) {
      val mockPlatformVersion = this@FeatureConnectorTest.mockPlatformVersion
      val binder =
        if (mockPlatformVersion == 0) {
          mockFeatureCoordinator
        } else {
          mockSafeFeatureCoordinator
        }

      val componentName = ComponentName(PACKAGE_NAME, VERSION_NAME)
      conn.onServiceConnected(componentName, binder.asBinder())
    }

    override fun unbindService(conn: ServiceConnection) {
      unbindServiceConnection.add(conn)
      super.unbindService(conn)
    }
  }

  private inner class FailingContext(mockPackageManager: PackageManager) :
    FakeContext(mockPackageManager) {

    override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
      serviceConnection.add(conn)
      return false
    }

    override fun unbindService(conn: ServiceConnection) {
      throw IllegalArgumentException()
    }
  }

  /** For testing a successful connection that returns an incorrect service */
  private inner class IncorrectServiceContext(mockPackageManager: PackageManager) :
    FakeContext(mockPackageManager) {

    override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
      service.action?.let { bindingActions.add(it) }
      serviceConnection.add(conn)
      val binder = mockSafeFeatureCoordinator
      val componentName = ComponentName(PACKAGE_NAME, VERSION_NAME)

      conn.onServiceConnected(componentName, binder.asBinder())
      return true
    }
  }

  /**
   * For testing successful connections during binds with ACTION_QUERY_API_VERSION but failures
   * during binds using ACTION_BIND_FEATURE_COORDINATOR
   */
  private inner class FailingLegacyContext(mockPackageManager: PackageManager) :
    FakeContext(mockPackageManager) {

    override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
      service.action?.let { bindingActions.add(it) }
      serviceConnection.add(conn)
      if (service.action == ACTION_QUERY_API_VERSION) {
        bindVersionService(conn)
        return true
      } else if (service.action == ACTION_BIND_FEATURE_COORDINATOR) {
        return false
      }
      return super.bindService(service, conn, flags)
    }

    override fun unbindService(conn: ServiceConnection) {
      throw IllegalArgumentException()
    }
  }

  /**
   * For testing binding with ACTION_QUERY_API_VERSION failing on the first attempt but succeeeding
   * thereafter, as well as failing binds using ACTION_BIND_FEATURE_COORDINATOR
   */
  private inner class FlakyLegacyContext(mockPackageManager: PackageManager) :
    FakeContext(mockPackageManager) {

    private var bindAttempts = 0

    override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
      service.action?.let { bindingActions.add(it) }
      serviceConnection.add(conn)
      if (service.action == ACTION_QUERY_API_VERSION) {
        if (bindAttempts++ == 0) {
          return false
        } else {
          bindVersionService(conn)
          return true
        }
      } else if (service.action == ACTION_BIND_FEATURE_COORDINATOR) {
        return false
      }
      return super.bindService(service, conn, flags)
    }

    override fun unbindService(conn: ServiceConnection) {
      throw IllegalArgumentException()
    }
  }

  private open class TestCompanionApiProxy(
    val defaultReturnValue: Boolean = true,
    val deviceIdList: List<String> = emptyList(),
  ) : CompanionApiProxy {

    override val queryCallbacks = ConcurrentHashMap<Int, QueryCallback>()

    override val queryResponseRecipients = ConcurrentHashMap<Int, ParcelUuid>()

    override val listener: ISafeOnLogRequestedListener = mock()

    override fun getConnectedDevices() = deviceIdList

    override fun sendMessage(deviceId: String, message: ByteArray) = defaultReturnValue

    override fun processLogRecords(loggerId: Int, logRecords: ByteArray) = defaultReturnValue

    override fun retrieveAssociatedDevices(listener: IInterface) = defaultReturnValue

    override fun cleanUp() {}
  }

  companion object {
    private const val PACKAGE_NAME = "com.test.package"
    private const val FC_NAME = "feature_coordinator"
    private const val VERSION_NAME = "version"
  }
}
