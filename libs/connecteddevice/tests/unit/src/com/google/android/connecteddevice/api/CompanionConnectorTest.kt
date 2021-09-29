package com.google.android.connecteddevice.api

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Looper
import android.os.ParcelUuid
import androidx.test.core.app.ApplicationProvider
import com.google.android.connecteddevice.api.CompanionConnector.Companion.ACTION_BIND_FEATURE_COORDINATOR
import com.google.android.connecteddevice.api.CompanionConnector.Companion.ACTION_BIND_FEATURE_COORDINATOR_FG
import com.google.android.connecteddevice.api.CompanionConnector.Companion.ACTION_BIND_REMOTE_FEATURE
import com.google.android.connecteddevice.api.CompanionConnector.Companion.ACTION_BIND_REMOTE_FEATURE_FG
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.util.ByteUtils
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.stubbing.Answer
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class CompanionConnectorTest {
  private val mockPackageManager: PackageManager = mock()

  private val mockCallback: CompanionConnector.Callback = mock()

  private val mockFeatureCoordinator: IFeatureCoordinator.Stub = mock()

  private val mockConnectedDeviceManager: IConnectedDeviceManager.Stub = mock()

  private val context = FakeContext(mockPackageManager)

  @Test
  fun connect_bindsWithFgActionWhenIsForegroundProcessIsTrue() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = true)

    connector.connect()

    assertThat(context.bindingActions).containsExactly(ACTION_BIND_FEATURE_COORDINATOR_FG)
  }

  @Test
  fun connect_bindsWithBgActionWhenIsForegroundProcessIsFalse() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false)

    connector.connect()

    assertThat(context.bindingActions).containsExactly(ACTION_BIND_FEATURE_COORDINATOR)
  }

  @Test
  fun connect_fgRetriesBindWithRemoteFeatureActionIfFeatureCoordinatorReturnsNullBinding() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = true)

    connector.connect()
    context.serviceConnection?.onNullBinding(ComponentName(PACKAGE_NAME, FG_NAME))

    assertThat(context.bindingActions)
      .containsExactly(ACTION_BIND_FEATURE_COORDINATOR_FG, ACTION_BIND_REMOTE_FEATURE_FG)
  }

  @Test
  fun connect_bgRetriesBindWithRemoteFeatureActionIfFeatureCoordinatorReturnsNullBinding() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false)

    connector.connect()
    context.serviceConnection?.onNullBinding(ComponentName(PACKAGE_NAME, BG_NAME))

    assertThat(context.bindingActions)
      .containsExactly(ACTION_BIND_FEATURE_COORDINATOR, ACTION_BIND_REMOTE_FEATURE)
  }

  @Test
  fun connect_fgRetriesBindWithRemoteFeatureActionIfFeatureCoordinatorActionMissing() {
    whenever(mockPackageManager.queryIntentServices(any(), any()))
      .thenAnswer(connectedDeviceManagerOnlyAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = true)

    connector.connect()
    context.serviceConnection?.onNullBinding(ComponentName(PACKAGE_NAME, BG_NAME))

    assertThat(context.bindingActions)
      .containsExactly(ACTION_BIND_REMOTE_FEATURE_FG)
  }

  @Test
  fun connect_bgRetriesBindWithRemoteFeatureActionIfFeatureCoordinatorActionMissing() {
    whenever(mockPackageManager.queryIntentServices(any(), any()))
      .thenAnswer(connectedDeviceManagerOnlyAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false)

    connector.connect()
    context.serviceConnection?.onNullBinding(ComponentName(PACKAGE_NAME, BG_NAME))

    assertThat(context.bindingActions)
      .containsExactly(ACTION_BIND_REMOTE_FEATURE)
  }

  @Test
  fun disconnect_invokesOnDisconnectedWhenFeatureCoordinatorConnected() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false).apply {
      callback = mockCallback
    }

    connector.connect()
    context
      .serviceConnection
      ?.onServiceConnected(ComponentName(PACKAGE_NAME, BG_NAME), mockFeatureCoordinator)
    connector.disconnect()

    verify(mockCallback).onDisconnected()
  }

  @Test
  fun disconnect_invokesOnDisconnectedWhenConnectedDeviceManagerConnected() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false).apply {
      callback = mockCallback
    }

    connector.connect()
    context
      .serviceConnection
      ?.onServiceConnected(ComponentName(PACKAGE_NAME, BG_NAME), mockConnectedDeviceManager)
    connector.disconnect()

    verify(mockCallback).onDisconnected()
  }

  @Test
  fun disconnect_notInvokedWithNoBoundService() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false).apply {
      callback = mockCallback
    }

    connector.connect()
    connector.disconnect()

    verify(mockCallback, never()).onDisconnected()
  }

  @Test
  fun disconnect_nullsOutFeatureCoordinatorAndConnectedDeviceManager() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false)

    connector.connect()
    context
      .serviceConnection
      ?.onServiceConnected(ComponentName(PACKAGE_NAME, BG_NAME), mockFeatureCoordinator)
    connector.disconnect()

    assertThat(connector.featureCoordinator).isNull()
    assertThat(connector.connectedDeviceManager).isNull()
  }

  @Test
  fun disconnect_doesNotThrowWhileUnbindingUnboundService() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val connector =
      CompanionConnector(FailingContext(mockPackageManager), isForegroundProcess = false).apply {
        callback = mockCallback
      }

    connector.disconnect()
  }

  @Test
  fun onConnected_invokedWhenBindingSucceeds() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false).apply {
      callback = mockCallback
    }

    connector.connect()
    context
      .serviceConnection
      ?.onServiceConnected(ComponentName(PACKAGE_NAME, BG_NAME), mockFeatureCoordinator)

    verify(mockCallback).onConnected()
  }

  @Test
  fun onDisconnected_invokedWhenServiceDisconnects() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false).apply {
      callback = mockCallback
    }

    connector.connect()
    val componentName = ComponentName(PACKAGE_NAME, BG_NAME)
    context
      .serviceConnection?.onServiceConnected(componentName, mockFeatureCoordinator)
    context.serviceConnection?.onServiceDisconnected(componentName)

    verify(mockCallback).onDisconnected()
  }

  @Test
  fun onFailedToConnect_invokedWhenServiceIsNotFound() {
    whenever(mockPackageManager.queryIntentServices(any(), any()))
      .thenAnswer(featureCoordinatorOnlyAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false).apply {
      callback = mockCallback
    }

    connector.connect()
    context.serviceConnection?.onNullBinding(ComponentName(PACKAGE_NAME, BG_NAME))

    verify(mockCallback).onFailedToConnect()
  }

  @Test
  fun onFailedToConnect_invokedWithNullBindingFromFeatureCoordinatorAndNoCDMService() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(notFoundAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false).apply {
      callback = mockCallback
    }

    connector.connect()

    verify(mockCallback).onFailedToConnect()
  }

  @Test
  fun onFailedToConnect_invokedAfterBindingRetryLimitExceeded() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val failingContext = FailingContext(mockPackageManager)
    val connector = CompanionConnector(failingContext, isForegroundProcess = false).apply {
      callback = mockCallback
    }
    val shadowLooper = Shadows.shadowOf(Looper.getMainLooper())

    connector.connect()
    repeat(CompanionConnector.MAX_BIND_ATTEMPTS) {
      shadowLooper.runToEndOfTasks()
    }

    assertThat(failingContext.serviceConnection).isNotNull()
    verify(mockCallback).onFailedToConnect()
  }

  @Test
  fun featureCoordinatorAndConnectedDeviceManager_areNotNullAfterServiceConnection() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false)

    connector.connect()
    context
      .serviceConnection
      ?.onServiceConnected(ComponentName(PACKAGE_NAME, BG_NAME), mockFeatureCoordinator)

    assertThat(connector.featureCoordinator).isNotNull()
    assertThat(connector.connectedDeviceManager).isNotNull()
  }

  @Test
  fun connectedDeviceManager_isNotNullAfterServiceConnectionWhenLegacy() {
    whenever(mockPackageManager.queryIntentServices(any(), any()))
      .thenAnswer(connectedDeviceManagerOnlyAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false)

    connector.connect()
    context
      .serviceConnection
      ?.onServiceConnected(ComponentName(PACKAGE_NAME, BG_NAME), mockConnectedDeviceManager)

    assertThat(connector.connectedDeviceManager).isNotNull()
  }

  @Test
  fun connectedDeviceManager_wrapsFeatureCoordinatorWhenNotLegacy() {
    val connectedDeviceManager =
      CompanionConnector.createConnectedDeviceManagerWrapper(mockFeatureCoordinator)
    val connectedDevice =
      ConnectedDevice(
        /* deviceId= */ UUID.randomUUID().toString(),
        /* deviceName= */ null,
        /* belongsToDriver= */ true,
        /* isConnectionEnabled= */ true
      )

    connectedDeviceManager.activeUserConnectedDevices
    verify(mockFeatureCoordinator).connectedDevicesForDriver

    val loggerId = 0
    val logRecords = ByteArray(0)
    connectedDeviceManager.processLogRecords(loggerId, logRecords)
    verify(mockFeatureCoordinator).processLogRecords(loggerId, logRecords)

    val connectionCallback: IConnectionCallback = mock()
    connectedDeviceManager.registerActiveUserConnectionCallback(connectionCallback)
    verify(mockFeatureCoordinator).registerDriverConnectionCallback(connectionCallback)
    connectedDeviceManager.unregisterConnectionCallback(connectionCallback)
    verify(mockFeatureCoordinator).unregisterConnectionCallback(connectionCallback)

    val deviceAssociationCallback: IDeviceAssociationCallback = mock()
    connectedDeviceManager.registerDeviceAssociationCallback(deviceAssociationCallback)
    verify(mockFeatureCoordinator).registerDeviceAssociationCallback(deviceAssociationCallback)
    connectedDeviceManager.unregisterDeviceAssociationCallback(deviceAssociationCallback)
    verify(mockFeatureCoordinator).unregisterDeviceAssociationCallback(deviceAssociationCallback)

    val deviceCallback: IDeviceCallback = mock()
    val recipientId = ParcelUuid(UUID.randomUUID())
    connectedDeviceManager.registerDeviceCallback(connectedDevice, recipientId, deviceCallback)
    verify(mockFeatureCoordinator)
      .registerDeviceCallback(connectedDevice, recipientId, deviceCallback)
    connectedDeviceManager.unregisterDeviceCallback(connectedDevice, recipientId, deviceCallback)
    verify(mockFeatureCoordinator)
      .unregisterDeviceCallback(connectedDevice, recipientId, deviceCallback)

    val logRequestedListener: IOnLogRequestedListener = mock()
    connectedDeviceManager.registerOnLogRequestedListener(loggerId, logRequestedListener)
    verify(mockFeatureCoordinator).registerOnLogRequestedListener(loggerId, logRequestedListener)
    connectedDeviceManager.unregisterOnLogRequestedListener(loggerId, logRequestedListener)
    verify(mockFeatureCoordinator).unregisterOnLogRequestedListener(loggerId, logRequestedListener)

    val message =
      DeviceMessage(
        recipientId.uuid,
        /* isMessageEncrypted= */ false,
        DeviceMessage.OperationType.CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )
    connectedDeviceManager.sendMessage(connectedDevice, message)
    verify(mockFeatureCoordinator).sendMessage(connectedDevice, message)
  }

  /** All companion actions are resolved. */
  private val defaultServiceAnswer =
    Answer {
      val intent = it.arguments.first() as Intent
      val resolveInfo = ResolveInfo().apply {
        serviceInfo = ServiceInfo().apply { packageName = PACKAGE_NAME }
      }
      when (intent.action) {
        ACTION_BIND_FEATURE_COORDINATOR_FG, ACTION_BIND_REMOTE_FEATURE_FG -> {
          resolveInfo.serviceInfo.name = FG_NAME
          listOf(resolveInfo)
        }
        ACTION_BIND_FEATURE_COORDINATOR, ACTION_BIND_REMOTE_FEATURE -> {
          resolveInfo.serviceInfo.name = BG_NAME
          listOf(resolveInfo)
        }
        else -> listOf()
      }
    }

  /** Only the feature coordinator actions are resolved. */
  private val featureCoordinatorOnlyAnswer =
    Answer {
      val intent = it.arguments.first() as Intent
      val resolveInfo = ResolveInfo().apply {
        serviceInfo = ServiceInfo().apply { packageName = PACKAGE_NAME }
      }
      when (intent.action) {
        ACTION_BIND_FEATURE_COORDINATOR_FG -> {
          resolveInfo.serviceInfo.name = FG_NAME
          listOf(resolveInfo)
        }
        ACTION_BIND_FEATURE_COORDINATOR -> {
          resolveInfo.serviceInfo.name = BG_NAME
          listOf(resolveInfo)
        }
        else -> listOf()
      }
    }

  /** Only the connected device manager actions are resolved. */
  private val connectedDeviceManagerOnlyAnswer =
    Answer {
      val intent = it.arguments.first() as Intent
      val resolveInfo = ResolveInfo().apply {
        serviceInfo = ServiceInfo().apply { packageName = PACKAGE_NAME }
      }
      when (intent.action) {
        ACTION_BIND_REMOTE_FEATURE_FG -> {
          resolveInfo.serviceInfo.name = FG_NAME
          listOf(resolveInfo)
        }
        ACTION_BIND_REMOTE_FEATURE -> {
          resolveInfo.serviceInfo.name = BG_NAME
          listOf(resolveInfo)
        }
        else -> listOf()
      }
    }

  /** No companion actions are resolved. */
  private val notFoundAnswer = Answer<List<ResolveInfo>> { listOf() }

  private open class FakeContext(val mockPackageManager: PackageManager) :
    ContextWrapper(ApplicationProvider.getApplicationContext()) {

    val bindingActions = mutableListOf<String>()

    var serviceConnection: ServiceConnection? = null

    var unbindServiceConnection: ServiceConnection? = null

    override fun getPackageManager(): PackageManager = mockPackageManager

    override fun bindService(service: Intent?, conn: ServiceConnection, flags: Int): Boolean {
      service?.action?.let { bindingActions.add(it) }
      serviceConnection = conn
      return super.bindService(service, conn, flags)
    }

    override fun unbindService(conn: ServiceConnection) {
      unbindServiceConnection = conn
      super.unbindService(conn)
    }
  }

  private class FailingContext(mockPackageManager: PackageManager) :
    FakeContext(mockPackageManager) {

    override fun bindService(service: Intent?, conn: ServiceConnection, flags: Int): Boolean {
      serviceConnection = conn
      return false
    }

    override fun unbindService(conn: ServiceConnection) {
      throw IllegalArgumentException()
    }
  }

  companion object {
    private const val PACKAGE_NAME = "com.test.package"
    private const val BG_NAME = "background"
    private const val FG_NAME = "foreground"
  }
}
