package com.google.android.connecteddevice.api

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.os.RemoteException
import androidx.test.core.app.ApplicationProvider
import com.google.android.companionprotos.Query
import com.google.android.companionprotos.QueryResponse
import com.google.android.companionprotos.SystemQuery
import com.google.android.companionprotos.SystemQueryType
import com.google.android.connecteddevice.api.Connector.Companion.ACTION_BIND_FEATURE_COORDINATOR
import com.google.android.connecteddevice.api.Connector.Companion.ACTION_BIND_FEATURE_COORDINATOR_FG
import com.google.android.connecteddevice.api.Connector.Companion.ACTION_BIND_REMOTE_FEATURE
import com.google.android.connecteddevice.api.Connector.Companion.ACTION_BIND_REMOTE_FEATURE_FG
import com.google.android.connecteddevice.api.Connector.Companion.USER_TYPE_ALL
import com.google.android.connecteddevice.api.Connector.Companion.USER_TYPE_DRIVER
import com.google.android.connecteddevice.api.Connector.Companion.USER_TYPE_PASSENGER
import com.google.android.connecteddevice.model.AssociatedDevice
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.util.ByteUtils
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.ByteString
import com.google.protobuf.ExtensionRegistryLite
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.test.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.stubbing.Answer
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class CompanionConnectorTest {
  private val mockPackageManager = mock<PackageManager>()

  private val mockCallback = mock<Connector.Callback>()

  private val mockFeatureCoordinator = mock<IFeatureCoordinator.Stub>()

  private val context = FakeContext(mockPackageManager)

  private val aliveBinder = mock<IBinder>()

  private lateinit var defaultConnector: CompanionConnector

  @Before
  fun setUp() {
    whenever(aliveBinder.isBinderAlive).thenReturn(true)
    whenever(mockFeatureCoordinator.asBinder()).thenReturn(aliveBinder)
    defaultConnector =
      CompanionConnector(context).apply {
        featureCoordinator = mockFeatureCoordinator
        callback = mockCallback
        featureId = ParcelUuid(UUID.randomUUID())
      }
  }

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

    assertThat(context.bindingActions).containsExactly(ACTION_BIND_REMOTE_FEATURE_FG)
  }

  @Test
  fun connect_bgRetriesBindWithRemoteFeatureActionIfFeatureCoordinatorActionMissing() {
    whenever(mockPackageManager.queryIntentServices(any(), any()))
      .thenAnswer(connectedDeviceManagerOnlyAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false)

    connector.connect()
    context.serviceConnection?.onNullBinding(ComponentName(PACKAGE_NAME, BG_NAME))

    assertThat(context.bindingActions).containsExactly(ACTION_BIND_REMOTE_FEATURE)
  }

  @Test
  fun disconnect_invokesOnDisconnectedWhenFeatureCoordinatorConnected() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val connector =
      CompanionConnector(context, isForegroundProcess = false).apply { callback = mockCallback }

    connector.connect()
    context.serviceConnection?.onServiceConnected(
      ComponentName(PACKAGE_NAME, BG_NAME),
      mockFeatureCoordinator
    )
    connector.disconnect()

    verify(mockCallback).onDisconnected()
  }

  @Test
  fun disconnect_invokesOnDisconnectedWhenConnectedDeviceManagerConnected() {
    val mockConnectedDeviceManager = mock<IConnectedDeviceManager.Stub>()
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val connector =
      CompanionConnector(context, isForegroundProcess = false).apply { callback = mockCallback }

    connector.connect()
    context.serviceConnection?.onServiceConnected(
      ComponentName(PACKAGE_NAME, BG_NAME),
      mockConnectedDeviceManager
    )
    connector.disconnect()

    verify(mockCallback).onDisconnected()
  }

  @Test
  fun disconnect_notInvokedWithNoBoundService() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val connector =
      CompanionConnector(context, isForegroundProcess = false).apply { callback = mockCallback }

    connector.connect()
    connector.disconnect()

    verify(mockCallback, never()).onDisconnected()
  }

  @Test
  fun disconnect_nullsOutFeatureCoordinatorAndConnectedDeviceManager() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false)

    connector.connect()
    context.serviceConnection?.onServiceConnected(
      ComponentName(PACKAGE_NAME, BG_NAME),
      mockFeatureCoordinator
    )
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
    val connector =
      CompanionConnector(context, isForegroundProcess = false).apply { callback = mockCallback }

    connector.connect()
    context.serviceConnection?.onServiceConnected(
      ComponentName(PACKAGE_NAME, BG_NAME),
      mockFeatureCoordinator
    )

    verify(mockCallback).onConnected()
  }

  @Test
  fun onDisconnected_invokedWhenServiceDisconnects() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val connector =
      CompanionConnector(context, isForegroundProcess = false).apply { callback = mockCallback }

    connector.connect()
    val componentName = ComponentName(PACKAGE_NAME, BG_NAME)
    context.serviceConnection?.onServiceConnected(componentName, mockFeatureCoordinator)
    context.serviceConnection?.onServiceDisconnected(componentName)

    verify(mockCallback).onDisconnected()
  }

  @Test
  fun onFailedToConnect_invokedWhenServiceIsNotFound() {
    whenever(mockPackageManager.queryIntentServices(any(), any()))
      .thenAnswer(featureCoordinatorOnlyAnswer)
    val connector =
      CompanionConnector(context, isForegroundProcess = false).apply { callback = mockCallback }

    connector.connect()
    context.serviceConnection?.onNullBinding(ComponentName(PACKAGE_NAME, BG_NAME))

    verify(mockCallback).onFailedToConnect()
  }

  @Test
  fun onFailedToConnect_invokedWithNullBindingFromFeatureCoordinatorAndNoCDMService() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(notFoundAnswer)
    val connector =
      CompanionConnector(context, isForegroundProcess = false).apply { callback = mockCallback }

    connector.connect()

    verify(mockCallback).onFailedToConnect()
  }

  @Test
  fun onFailedToConnect_invokedAfterBindingRetryLimitExceeded() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val failingContext = FailingContext(mockPackageManager)
    val connector =
      CompanionConnector(failingContext, isForegroundProcess = false).apply {
        callback = mockCallback
      }
    val shadowLooper = Shadows.shadowOf(Looper.getMainLooper())

    connector.connect()
    repeat(CompanionConnector.MAX_BIND_ATTEMPTS) { shadowLooper.runToEndOfTasks() }

    assertThat(failingContext.serviceConnection).isNotNull()
    verify(mockCallback).onFailedToConnect()
  }

  @Test
  fun featureCoordinatorAndConnectedDeviceManager_areNotNullAfterServiceConnection() {
    whenever(mockPackageManager.queryIntentServices(any(), any())).thenAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false)

    connector.connect()
    context.serviceConnection?.onServiceConnected(
      ComponentName(PACKAGE_NAME, BG_NAME),
      mockFeatureCoordinator
    )

    assertThat(connector.featureCoordinator).isNotNull()
    assertThat(connector.connectedDeviceManager).isNotNull()
  }

  @Test
  fun connectedDeviceManager_isNotNullAfterServiceConnectionWhenLegacy() {
    val mockConnectedDeviceManager = mock<IConnectedDeviceManager.Stub>()
    whenever(mockConnectedDeviceManager.asBinder()).thenReturn(aliveBinder)
    whenever(mockPackageManager.queryIntentServices(any(), any()))
      .thenAnswer(connectedDeviceManagerOnlyAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false)

    connector.connect()
    context.serviceConnection?.onServiceConnected(
      ComponentName(PACKAGE_NAME, BG_NAME),
      mockConnectedDeviceManager
    )

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

    val connectionCallback = mock<IConnectionCallback>()
    connectedDeviceManager.registerActiveUserConnectionCallback(connectionCallback)
    verify(mockFeatureCoordinator).registerDriverConnectionCallback(connectionCallback)
    connectedDeviceManager.unregisterConnectionCallback(connectionCallback)
    verify(mockFeatureCoordinator).unregisterConnectionCallback(connectionCallback)

    val deviceAssociationCallback = mock<IDeviceAssociationCallback>()
    connectedDeviceManager.registerDeviceAssociationCallback(deviceAssociationCallback)
    verify(mockFeatureCoordinator).registerDeviceAssociationCallback(deviceAssociationCallback)
    connectedDeviceManager.unregisterDeviceAssociationCallback(deviceAssociationCallback)
    verify(mockFeatureCoordinator).unregisterDeviceAssociationCallback(deviceAssociationCallback)

    val deviceCallback = mock<IDeviceCallback>()
    val recipientId = ParcelUuid(UUID.randomUUID())
    connectedDeviceManager.registerDeviceCallback(connectedDevice, recipientId, deviceCallback)
    verify(mockFeatureCoordinator)
      .registerDeviceCallback(connectedDevice, recipientId, deviceCallback)
    connectedDeviceManager.unregisterDeviceCallback(connectedDevice, recipientId, deviceCallback)
    verify(mockFeatureCoordinator)
      .unregisterDeviceCallback(connectedDevice, recipientId, deviceCallback)

    val logRequestedListener = mock<IOnLogRequestedListener>()
    connectedDeviceManager.registerOnLogRequestedListener(loggerId, logRequestedListener)
    verify(mockFeatureCoordinator).registerOnLogRequestedListener(loggerId, logRequestedListener)
    connectedDeviceManager.unregisterOnLogRequestedListener(loggerId, logRequestedListener)
    verify(mockFeatureCoordinator).unregisterOnLogRequestedListener(loggerId, logRequestedListener)

    val message =
      DeviceMessage.createOutgoingMessage(
        recipientId.uuid,
        /* isMessageEncrypted= */ false,
        DeviceMessage.OperationType.CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )
    connectedDeviceManager.sendMessage(connectedDevice, message)
    verify(mockFeatureCoordinator).sendMessage(connectedDevice, message)
  }

  @Test
  fun connect_registersCommonCallbacks() {
    defaultConnector.connect()

    verify(mockFeatureCoordinator).registerDeviceAssociationCallback(any())
    verify(mockFeatureCoordinator).registerOnLogRequestedListener(any(), any())
  }

  @Test
  fun connect_userTypeDriverRegistersDriverConnectionCallbacks() {
    val connector =
      CompanionConnector(context, userType = USER_TYPE_DRIVER).apply {
        featureCoordinator = mockFeatureCoordinator
      }

    connector.connect()
    verify(mockFeatureCoordinator).registerDriverConnectionCallback(any())
  }

  @Test
  fun connect_userTypePassengerRegistersPassengerConnectionCallbacks() {
    val connector =
      CompanionConnector(context, userType = USER_TYPE_PASSENGER).apply {
        featureCoordinator = mockFeatureCoordinator
      }

    connector.connect()
    verify(mockFeatureCoordinator).registerPassengerConnectionCallback(any())
  }

  @Test
  fun connect_userTypeAllRegistersAllConnectionCallbacks() {
    val connector =
      CompanionConnector(context, userType = USER_TYPE_ALL).apply {
        featureCoordinator = mockFeatureCoordinator
      }

    connector.connect()
    verify(mockFeatureCoordinator).registerAllConnectionCallback(any())
  }

  @Test
  fun connect_userTypeDriverRegistersDeviceCallbacksForAlreadyConnectedDriverDevices() {
    val featureId = ParcelUuid(UUID.randomUUID())
    val connector =
      CompanionConnector(context, userType = USER_TYPE_DRIVER).apply {
        featureCoordinator = mockFeatureCoordinator
        this.featureId = featureId
      }
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ false
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))

    connector.connect()

    verify(mockFeatureCoordinator).registerDeviceCallback(eq(device), eq(featureId), any())
  }

  @Test
  fun connect_userTypePassengerRegistersDeviceCallbacksForAlreadyConnectedPassengerDevices() {
    val featureId = ParcelUuid(UUID.randomUUID())
    val connector =
      CompanionConnector(context, userType = USER_TYPE_PASSENGER).apply {
        featureCoordinator = mockFeatureCoordinator
        this.featureId = featureId
      }
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ false,
        /* hasSecureChannel= */ false
      )
    whenever(mockFeatureCoordinator.connectedDevicesForPassengers).thenReturn(listOf(device))

    connector.connect()

    verify(mockFeatureCoordinator).registerDeviceCallback(eq(device), eq(featureId), any())
  }

  @Test
  fun connect_userTypeAllRegistersDeviceCallbacksForAllAlreadyConnectedDevices() {
    val featureId = ParcelUuid(UUID.randomUUID())
    val connector =
      CompanionConnector(context, userType = USER_TYPE_ALL).apply {
        featureCoordinator = mockFeatureCoordinator
        this.featureId = featureId
      }
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ false
      )
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))

    connector.connect()

    verify(mockFeatureCoordinator).registerDeviceCallback(eq(device), eq(featureId), any())
  }

  @Test
  fun connect_deviceCallbacksNotRegisteredWhenMissingFeatureId() {
    defaultConnector.featureId = null
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ false
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))

    defaultConnector.connect()

    verify(mockFeatureCoordinator, never()).registerDeviceCallback(any(), any(), any())
  }

  @Test
  fun disconnect_unregistersFeatureCoordinatorCallbacks() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ false
      )
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))

    defaultConnector.disconnect()

    verify(mockFeatureCoordinator).unregisterConnectionCallback(any())
    verify(mockFeatureCoordinator).unregisterDeviceAssociationCallback(any())
    verify(mockFeatureCoordinator).unregisterOnLogRequestedListener(any(), any())
    verify(mockFeatureCoordinator)
      .unregisterDeviceCallback(eq(device), eq(defaultConnector.featureId), any())
  }

  @Test
  fun disconnect_featureCoordinatorRemoteExceptionIsCaught() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ false
      )
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.unregisterConnectionCallback(any()))
      .thenThrow(RemoteException())

    defaultConnector.disconnect()
  }

  @Test
  fun disconnect_unregistersConnectedDeviceManagerCallbacks() {
    val mockConnectedDeviceManager = mock<IConnectedDeviceManager.Stub>()
    whenever(mockConnectedDeviceManager.asBinder()).thenReturn(aliveBinder)
    defaultConnector.apply {
      featureCoordinator = null
      connectedDeviceManager = mockConnectedDeviceManager
    }
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ false
      )
    whenever(mockConnectedDeviceManager.activeUserConnectedDevices).thenReturn(listOf(device))

    defaultConnector.disconnect()

    verify(mockConnectedDeviceManager).unregisterConnectionCallback(any())
    verify(mockConnectedDeviceManager).unregisterDeviceAssociationCallback(any())
    verify(mockConnectedDeviceManager).unregisterOnLogRequestedListener(any(), any())
    verify(mockConnectedDeviceManager)
      .unregisterDeviceCallback(eq(device), eq(defaultConnector.featureId), any())
  }

  @Test
  fun disconnect_connectedDeviceManagerRemoteExceptionIsCaught() {
    val mockConnectedDeviceManager = mock<IConnectedDeviceManager.Stub>()
    whenever(mockConnectedDeviceManager.asBinder()).thenReturn(aliveBinder)
    whenever(mockConnectedDeviceManager.unregisterConnectionCallback(any()))
      .thenThrow(RemoteException())
    defaultConnector.apply {
      featureCoordinator = null
      connectedDeviceManager = mockConnectedDeviceManager
    }
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ false
      )
    whenever(mockConnectedDeviceManager.activeUserConnectedDevices).thenReturn(listOf(device))

    defaultConnector.disconnect()
  }

  @Test
  fun onDeviceConnected_registersDeviceCallbackWithFeatureId() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ false
      )

    defaultConnector.connect()

    argumentCaptor<IConnectionCallback> {
      verify(mockFeatureCoordinator).registerDriverConnectionCallback(capture())
      firstValue.onDeviceConnected(device)
    }

    verify(mockFeatureCoordinator)
      .registerDeviceCallback(eq(device), eq(defaultConnector.featureId), any())
  }

  @Test
  fun onDeviceConnected_doesNotRegisterDeviceCallbackWithoutFeatureId() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ false
      )
    defaultConnector.featureId = null
    defaultConnector.connect()

    argumentCaptor<IConnectionCallback> {
      verify(mockFeatureCoordinator).registerDriverConnectionCallback(capture())
      firstValue.onDeviceConnected(device)
    }

    verify(mockFeatureCoordinator, never()).registerDeviceCallback(any(), any(), any())
  }

  @Test
  fun onDeviceDisconnected_invokedWhenDeviceDisconnects() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ false
      )
    defaultConnector.connect()

    argumentCaptor<IConnectionCallback> {
      verify(mockFeatureCoordinator).registerDriverConnectionCallback(capture())
      firstValue.onDeviceDisconnected(device)
    }

    verify(mockCallback).onDeviceDisconnected(eq(device))
  }

  @Test
  fun onDeviceDisconnected_unregistersDeviceCallback() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ false
      )
    defaultConnector.connect()

    argumentCaptor<IConnectionCallback> {
      verify(mockFeatureCoordinator).registerDriverConnectionCallback(capture())
      firstValue.onDeviceDisconnected(device)
    }

    verify(mockFeatureCoordinator)
      .unregisterDeviceCallback(eq(device), eq(defaultConnector.featureId), any())
  }

  @Test
  fun onDeviceDisconnected_unregistersDeviceCallbackConnectedDeviceManager() {
    val mockConnectedDeviceManager = mock<IConnectedDeviceManager.Stub>()
    whenever(mockConnectedDeviceManager.asBinder()).thenReturn(aliveBinder)
    defaultConnector.apply {
      featureCoordinator = null
      connectedDeviceManager = mockConnectedDeviceManager
    }
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ false
      )
    defaultConnector.connect()

    argumentCaptor<IConnectionCallback> {
      verify(mockConnectedDeviceManager).registerActiveUserConnectionCallback(capture())
      firstValue.onDeviceDisconnected(device)
    }

    verify(mockConnectedDeviceManager)
      .unregisterDeviceCallback(eq(device), eq(defaultConnector.featureId), any())
  }

  @Test
  fun onSecureChannelEstablished_invokedWhenChannelEstablished() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ false
      )
    val secureDevice =
      ConnectedDevice(
        device.deviceId,
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    defaultConnector.connect()

    argumentCaptor<IDeviceCallback> {
      verify(mockFeatureCoordinator)
        .registerDeviceCallback(eq(device), eq(defaultConnector.featureId), capture())
      firstValue.onSecureChannelEstablished(secureDevice)
      verify(mockCallback).onSecureChannelEstablished(secureDevice)
    }
  }

  @Test
  fun onSecureChannelEstablished_invokedOnStartupIfChannelAlreadyEstablished() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    defaultConnector.connect()

    verify(mockCallback).onSecureChannelEstablished(device)
  }

  @Test
  fun onSecureChannelEstablished_invokedOnDeviceConnectedIfChannelAlreadyEstablished() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    defaultConnector.connect()

    argumentCaptor<IConnectionCallback> {
      verify(mockFeatureCoordinator).registerDriverConnectionCallback(capture())
      firstValue.onDeviceConnected(device)
    }
    verify(mockCallback).onSecureChannelEstablished(device)
  }

  @Test
  fun onDeviceError_invokedOnError() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ false
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    defaultConnector.connect()
    val error = -1

    argumentCaptor<IDeviceCallback> {
      verify(mockFeatureCoordinator)
        .registerDeviceCallback(eq(device), eq(defaultConnector.featureId), capture())
      firstValue.onDeviceError(device, error)
      verify(mockCallback).onDeviceError(device, error)
    }
  }

  @Test
  fun onAssociatedDeviceAdded_invokedWhenNewAssociatedDeviceAdded() {
    val device =
      AssociatedDevice(
        UUID.randomUUID().toString(),
        /* deviceAddress= */ "",
        /* deviceName= */ null,
        /* isConnectionEnabled= */ true
      )
    defaultConnector.connect()

    argumentCaptor<IDeviceAssociationCallback> {
      verify(mockFeatureCoordinator).registerDeviceAssociationCallback(capture())
      firstValue.onAssociatedDeviceAdded(device)
    }

    verify(mockCallback).onAssociatedDeviceAdded(device)
  }

  @Test
  fun onAssociatedDeviceRemoved_invokedWhenAssociatedDeviceRemoved() {
    val device =
      AssociatedDevice(
        UUID.randomUUID().toString(),
        /* deviceAddress= */ "",
        /* deviceName= */ null,
        /* isConnectionEnabled= */ true
      )
    defaultConnector.connect()

    argumentCaptor<IDeviceAssociationCallback> {
      verify(mockFeatureCoordinator).registerDeviceAssociationCallback(capture())
      firstValue.onAssociatedDeviceRemoved(device)
    }

    verify(mockCallback).onAssociatedDeviceRemoved(device)
  }

  @Test
  fun onAssociatedDeviceUpdated_invokedWhenAssociatedDeviceUpdated() {
    val device =
      AssociatedDevice(
        UUID.randomUUID().toString(),
        /* deviceAddress= */ "",
        /* deviceName= */ null,
        /* isConnectionEnabled= */ true
      )
    defaultConnector.connect()

    argumentCaptor<IDeviceAssociationCallback> {
      verify(mockFeatureCoordinator).registerDeviceAssociationCallback(capture())
      firstValue.onAssociatedDeviceUpdated(device)
    }

    verify(mockCallback).onAssociatedDeviceUpdated(device)
  }

  @Test
  fun sendMessageSecurelyWithId_sendsMessageSecurelyToDevice() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    defaultConnector.connect()
    val message = ByteUtils.randomBytes(10)

    defaultConnector.sendMessageSecurely(device.deviceId, message)

    argumentCaptor<DeviceMessage> {
      verify(mockFeatureCoordinator).sendMessage(eq(device), capture())
      val deviceMessage = firstValue
      assertThat(deviceMessage.recipient).isEqualTo(defaultConnector.featureId?.uuid)
      assertThat(deviceMessage.isMessageEncrypted).isTrue()
      assertThat(deviceMessage.operationType).isEqualTo(DeviceMessage.OperationType.CLIENT_MESSAGE)
      assertThat(deviceMessage.message).isEqualTo(message)
    }
  }

  @Test
  fun sendMessageSecurely_sendsMessageSecurelyToDevice() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    defaultConnector.connect()
    val message = ByteUtils.randomBytes(10)

    defaultConnector.sendMessageSecurely(device, message)

    argumentCaptor<DeviceMessage> {
      verify(mockFeatureCoordinator).sendMessage(eq(device), capture())
      val deviceMessage = firstValue
      assertThat(deviceMessage.recipient).isEqualTo(defaultConnector.featureId?.uuid)
      assertThat(deviceMessage.isMessageEncrypted).isTrue()
      assertThat(deviceMessage.operationType).isEqualTo(DeviceMessage.OperationType.CLIENT_MESSAGE)
      assertThat(deviceMessage.message).isEqualTo(message)
    }
  }

  @Test
  fun onMessageFailedToSend_invokedWhenDeviceNotFound() {
    val deviceId = UUID.randomUUID().toString()
    val message = ByteUtils.randomBytes(10)
    defaultConnector.connect()

    defaultConnector.sendMessageSecurely(deviceId, message)

    verify(mockCallback).onMessageFailedToSend(deviceId, message, /* isTransient= */ false)
  }

  @Test
  fun onMessageFailedToSend_invokedWhenRemoteExceptionThrownId() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.sendMessage(any(), any())).thenThrow(RemoteException())
    val message = ByteUtils.randomBytes(10)
    defaultConnector.connect()

    defaultConnector.sendMessageSecurely(device.deviceId, message)

    verify(mockCallback).onMessageFailedToSend(device.deviceId, message, /* isTransient= */ false)
  }

  @Test
  fun onMessageFailedToSend_invokedWhenRemoteExceptionThrown() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.sendMessage(any(), any())).thenThrow(RemoteException())
    val message = ByteUtils.randomBytes(10)
    defaultConnector.connect()

    defaultConnector.sendMessageSecurely(device, message)

    verify(mockCallback).onMessageFailedToSend(device.deviceId, message, /* isTransient= */ false)
  }

  @Test
  fun onMessageFailedToSend_invokedWhenErrorRetrievingDevices() {
    val deviceId = UUID.randomUUID().toString()
    val message = ByteUtils.randomBytes(10)
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenThrow(RemoteException())
    defaultConnector.connect()

    defaultConnector.sendMessageSecurely(deviceId, message)

    verify(mockCallback).onMessageFailedToSend(deviceId, message, /* isTransient= */ false)
  }

  @Test
  fun onMessageFailedToSend_invokedWhenSendMessageIdCalledBeforeServiceConnection() {
    defaultConnector.featureCoordinator = null
    defaultConnector.connectedDeviceManager = null
    val deviceId = UUID.randomUUID().toString()
    val message = ByteUtils.randomBytes(10)

    defaultConnector.sendMessageSecurely(deviceId, message)

    verify(mockCallback).onMessageFailedToSend(deviceId, message, /* isTransient= */ true)
  }

  @Test
  fun onMessageFailedToSend_invokedWhenSendMessageCalledBeforeServiceConnection() {
    defaultConnector.featureCoordinator = null
    defaultConnector.connectedDeviceManager = null
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val message = ByteUtils.randomBytes(10)

    defaultConnector.sendMessageSecurely(device, message)

    verify(mockCallback).onMessageFailedToSend(device.deviceId, message, /* isTransient= */ true)
  }

  @Test
  fun onMessageReceived_invokedWhenGenericMessageReceived() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val message = ByteUtils.randomBytes(10)
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    defaultConnector.connect()

    argumentCaptor<IDeviceCallback> {
      verify(mockFeatureCoordinator)
        .registerDeviceCallback(eq(device), eq(defaultConnector.featureId), capture())
      val deviceMessage =
        DeviceMessage.createOutgoingMessage(
          defaultConnector.featureId?.uuid,
          /* isMessageEncrypted= */ true,
          DeviceMessage.OperationType.CLIENT_MESSAGE,
          message
        )
      firstValue.onMessageReceived(device, deviceMessage)
    }

    verify(mockCallback).onMessageReceived(device, message)
  }

  @Test
  fun getConnectedDeviceById_returnsDeviceWhenConnected() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ false
      )
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    defaultConnector.connect()

    assertThat(defaultConnector.getConnectedDeviceById(device.deviceId)).isEqualTo(device)
  }

  @Test
  fun getConnectedDeviceById_returnsNullWhenNotConnected() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ false
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    defaultConnector.connect()

    assertThat(defaultConnector.getConnectedDeviceById(UUID.randomUUID().toString())).isNull()
  }

  @Test
  fun getConnectedDeviceById_returnsNullWhenRemoteExceptionThrown() {
    whenever(mockFeatureCoordinator.allConnectedDevices).thenThrow(RemoteException())
    defaultConnector.connect()

    assertThat(defaultConnector.getConnectedDeviceById(UUID.randomUUID().toString())).isNull()
  }

  @Test
  fun getConnectedDeviceById_returnsNullBeforeServiceConnection() {
    defaultConnector.featureCoordinator = null
    defaultConnector.connectedDeviceManager = null

    assertThat(defaultConnector.getConnectedDeviceById(UUID.randomUUID().toString())).isNull()
  }

  @Test
  fun sendQuerySecurely_sendsQueryToOwnFeatureId() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    val request = ByteUtils.randomBytes(10)
    val parameters = ByteUtils.randomBytes(10)
    defaultConnector.connect()
    val callback = mock<Connector.QueryCallback>()

    defaultConnector.sendQuerySecurely(device.deviceId, request, parameters, callback)

    argumentCaptor<DeviceMessage> {
      verify(mockFeatureCoordinator).sendMessage(eq(device), capture())
      val query = Query.parseFrom(firstValue.message, ExtensionRegistryLite.getEmptyRegistry())
      assertThat(query.request.toByteArray()).isEqualTo(request)
      assertThat(query.parameters.toByteArray()).isEqualTo(parameters)
    }
  }

  @Test
  fun sendQuery_queryCallbackOnSuccessInvoked() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val request = ByteUtils.randomBytes(10)
    val response = ByteUtils.randomBytes(10)
    val parameters = ByteUtils.randomBytes(10)
    val callback = mock<Connector.QueryCallback>()
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    defaultConnector.connect()
    val callbackCaptor =
      argumentCaptor<IDeviceCallback> {
        verify(mockFeatureCoordinator)
          .registerDeviceCallback(eq(device), eq(defaultConnector.featureId), capture())
      }

    defaultConnector.sendQuerySecurely(device.deviceId, request, parameters, callback)
    val messageCaptor =
      argumentCaptor<DeviceMessage> {
        verify(mockFeatureCoordinator).sendMessage(eq(device), capture())
      }
    val query =
      Query.parseFrom(messageCaptor.firstValue.message, ExtensionRegistryLite.getEmptyRegistry())
    val queryResponse =
      QueryResponse.newBuilder()
        .setQueryId(query.id)
        .setSuccess(true)
        .setResponse(ByteString.copyFrom(response))
        .build()
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        defaultConnector.featureId?.uuid,
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.QUERY_RESPONSE,
        queryResponse.toByteArray()
      )
    callbackCaptor.firstValue.onMessageReceived(device, deviceMessage)

    verify(callback).onSuccess(response)
  }

  @Test
  fun sendQuery_queryCallbackOnErrorInvoked() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    defaultConnector.connect()
    val callbackCaptor =
      argumentCaptor<IDeviceCallback> {
        verify(mockFeatureCoordinator)
          .registerDeviceCallback(eq(device), eq(defaultConnector.featureId), capture())
      }
    val request = ByteUtils.randomBytes(10)
    val parameters = ByteUtils.randomBytes(10)
    val callback = mock<Connector.QueryCallback>()

    defaultConnector.sendQuerySecurely(device.deviceId, request, parameters, callback)
    val messageCaptor =
      argumentCaptor<DeviceMessage> {
        verify(mockFeatureCoordinator).sendMessage(eq(device), capture())
      }
    val query =
      Query.parseFrom(messageCaptor.firstValue.message, ExtensionRegistryLite.getEmptyRegistry())
    val response = ByteUtils.randomBytes(10)
    val queryResponse =
      QueryResponse.newBuilder()
        .setQueryId(query.id)
        .setSuccess(false)
        .setResponse(ByteString.copyFrom(response))
        .build()
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        defaultConnector.featureId?.uuid,
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.QUERY_RESPONSE,
        queryResponse.toByteArray()
      )
    callbackCaptor.firstValue.onMessageReceived(device, deviceMessage)

    verify(callback).onError(response)
  }

  @Test
  fun sendQuery_queryCallbackNotInvokedOnDifferentQueryIdResponse() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    defaultConnector.connect()
    val callbackCaptor =
      argumentCaptor<IDeviceCallback> {
        verify(mockFeatureCoordinator)
          .registerDeviceCallback(eq(device), eq(defaultConnector.featureId), capture())
      }
    val request = ByteUtils.randomBytes(10)
    val parameters = ByteUtils.randomBytes(10)
    val callback = mock<Connector.QueryCallback>()

    defaultConnector.sendQuerySecurely(device.deviceId, request, parameters, callback)
    val messageCaptor =
      argumentCaptor<DeviceMessage> {
        verify(mockFeatureCoordinator).sendMessage(eq(device), capture())
      }
    val query =
      Query.parseFrom(messageCaptor.firstValue.message, ExtensionRegistryLite.getEmptyRegistry())
    val response = ByteUtils.randomBytes(10)
    val queryResponse =
      QueryResponse.newBuilder()
        .setQueryId(query.id + 1)
        .setSuccess(false)
        .setResponse(ByteString.copyFrom(response))
        .build()
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        defaultConnector.featureId?.uuid,
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.QUERY_RESPONSE,
        queryResponse.toByteArray()
      )
    callbackCaptor.firstValue.onMessageReceived(device, deviceMessage)

    verify(callback, never()).onSuccess(any())
    verify(callback, never()).onError(any())
  }

  @Test
  fun sendQuery_queryCallbackOnQueryNotSentInvokedBeforeServiceConnectionWithId() {
    defaultConnector.featureCoordinator = null
    defaultConnector.connectedDeviceManager = null
    val deviceId = UUID.randomUUID().toString()
    val request = ByteUtils.randomBytes(10)
    val parameters = ByteUtils.randomBytes(10)
    val callback = mock<Connector.QueryCallback>()

    defaultConnector.sendQuerySecurely(deviceId, request, parameters, callback)

    verify(callback).onQueryFailedToSend(/* isTransient= */ true)
  }

  @Test
  fun sendQuery_queryCallbackOnQueryNotSentInvokedBeforeServiceConnectionWithDevice() {
    defaultConnector.featureCoordinator = null
    defaultConnector.connectedDeviceManager = null
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val request = ByteUtils.randomBytes(10)
    val parameters = ByteUtils.randomBytes(10)
    val callback = mock<Connector.QueryCallback>()

    defaultConnector.sendQuerySecurely(device, request, parameters, callback)

    verify(callback).onQueryFailedToSend(/* isTransient= */ true)
  }

  @Test
  fun sendQuery_queryCallbackOnQueryNotSentInvokedIfSendMessageThrowsRemoteException() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.sendMessage(any(), any())).thenThrow(RemoteException())
    defaultConnector.connect()
    val request = ByteUtils.randomBytes(10)
    val parameters: ByteArray? = null
    val callback = mock<Connector.QueryCallback>()

    defaultConnector.sendQuerySecurely(device.deviceId, request, parameters, callback)

    verify(callback).onQueryFailedToSend(/* isTransient= */ false)
  }

  @Test
  fun sendQuery_queryCallbackOnQueryNotSentInvokedIfDeviceNotFound() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    defaultConnector.connect()
    val request = ByteUtils.randomBytes(10)
    val parameters = ByteUtils.randomBytes(10)
    val callback = mock<Connector.QueryCallback>()

    defaultConnector.sendQuerySecurely(device.deviceId, request, parameters, callback)

    verify(callback).onQueryFailedToSend(/* isTransient= */ false)
  }

  @Test
  fun onQueryReceived_invokedWithQueryFields() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    defaultConnector.connect()
    val callbackCaptor =
      argumentCaptor<IDeviceCallback> {
        verify(mockFeatureCoordinator)
          .registerDeviceCallback(eq(device), eq(defaultConnector.featureId), capture())
      }
    val queryId = 1
    val request = ByteUtils.randomBytes(10)
    val parameters = ByteUtils.randomBytes(10)
    val featureId = defaultConnector.featureId?.uuid ?: fail("Null feature id")
    val query =
      Query.newBuilder()
        .setId(queryId)
        .setRequest(ByteString.copyFrom(request))
        .setParameters(ByteString.copyFrom(parameters))
        .setSender(ByteString.copyFrom(ByteUtils.uuidToBytes(featureId)))
        .build()
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        featureId,
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.QUERY,
        query.toByteArray()
      )
    callbackCaptor.firstValue.onMessageReceived(device, deviceMessage)

    verify(mockCallback).onQueryReceived(device, queryId, request, parameters)
  }

  @Test
  fun respondToQuery_doesNotSendResponseWithUnrecognizedQueryId() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    defaultConnector.connect()
    val nonExistentQueryId = 0
    val response = ByteUtils.randomBytes(10)

    defaultConnector.respondToQuerySecurely(
      device,
      nonExistentQueryId,
      /* success= */ true,
      response
    )

    verify(mockFeatureCoordinator, never()).sendMessage(any(), any())
  }

  @Test
  fun respondToQuery_sendsResponseToSenderIfSameFeatureId() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    defaultConnector.connect()
    val callbackCaptor =
      argumentCaptor<IDeviceCallback> {
        verify(mockFeatureCoordinator)
          .registerDeviceCallback(eq(device), eq(defaultConnector.featureId), capture())
      }
    val queryId = 1
    val request = ByteUtils.randomBytes(10)
    val parameters = ByteUtils.randomBytes(10)
    val featureId = defaultConnector.featureId?.uuid ?: fail("Null feature id")
    val query =
      Query.newBuilder()
        .setId(queryId)
        .setRequest(ByteString.copyFrom(request))
        .setParameters(ByteString.copyFrom(parameters))
        .setSender(ByteString.copyFrom(ByteUtils.uuidToBytes(featureId)))
        .build()
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        featureId,
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.QUERY,
        query.toByteArray()
      )

    callbackCaptor.firstValue.onMessageReceived(device, deviceMessage)
    val response = ByteUtils.randomBytes(10)
    defaultConnector.respondToQuerySecurely(device, queryId, /* success= */ true, response)
    val messageCaptor =
      argumentCaptor<DeviceMessage> {
        verify(mockFeatureCoordinator).sendMessage(eq(device), capture())
      }
    val queryResponse =
      QueryResponse.parseFrom(
        messageCaptor.firstValue.message,
        ExtensionRegistryLite.getEmptyRegistry()
      )

    assertThat(queryResponse.queryId).isEqualTo(queryId)
    assertThat(queryResponse.success).isTrue()
    assertThat(queryResponse.response.toByteArray()).isEqualTo(response)
  }

  @Test
  fun respondToQuery_sendResponseToSenderIfDifferentFeatureId() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    defaultConnector.connect()
    val featureId = defaultConnector.featureId ?: fail("Null feature id")
    val callbackCaptor =
      argumentCaptor<IDeviceCallback> {
        verify(mockFeatureCoordinator)
          .registerDeviceCallback(eq(device), eq(defaultConnector.featureId), capture())
      }
    val sender = ParcelUuid(UUID.randomUUID())
    val queryId = 1
    val request = ByteUtils.randomBytes(10)
    val parameters = ByteUtils.randomBytes(10)
    val query =
      Query.newBuilder()
        .setId(queryId)
        .setRequest(ByteString.copyFrom(request))
        .setParameters(ByteString.copyFrom(parameters))
        .setSender(ByteString.copyFrom(ByteUtils.uuidToBytes(sender.uuid)))
        .build()
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        featureId.uuid,
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.QUERY,
        query.toByteArray()
      )
    callbackCaptor.firstValue.onMessageReceived(device, deviceMessage)
    val response = ByteUtils.randomBytes(10)

    defaultConnector.respondToQuerySecurely(device, queryId, /* success= */ true, response)
    val messageCaptor =
      argumentCaptor<DeviceMessage> {
        verify(mockFeatureCoordinator).sendMessage(eq(device), capture())
      }
    val queryResponse =
      QueryResponse.parseFrom(
        messageCaptor.firstValue.message,
        ExtensionRegistryLite.getEmptyRegistry()
      )

    assertThat(queryResponse.queryId).isEqualTo(queryId)
    assertThat(queryResponse.success).isTrue()
    assertThat(queryResponse.response.toByteArray()).isEqualTo(response)
  }

  @Test
  fun respondToQuery_doesNotThrowBeforeServiceConnectionWithDevice() {
    defaultConnector.featureCoordinator = null
    defaultConnector.connectedDeviceManager = null
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val queryId = 0
    val response = ByteUtils.randomBytes(10)

    defaultConnector.respondToQuerySecurely(device, queryId, /* success= */ true, response)
  }

  @Test
  fun retrieveCompanionApplicationName_sendsAppNameQueryToSystemFeature() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    defaultConnector.connect()
    val callback = mock<Connector.AppNameCallback>()

    defaultConnector.retrieveCompanionApplicationName(device, callback)
    val messageCaptor =
      argumentCaptor<DeviceMessage> {
        verify(mockFeatureCoordinator).sendMessage(eq(device), capture())
      }

    val deviceMessage = messageCaptor.firstValue
    assertThat(deviceMessage.recipient).isEqualTo(Connector.SYSTEM_FEATURE_ID.uuid)
    val query = Query.parseFrom(deviceMessage.message, ExtensionRegistryLite.getEmptyRegistry())
    assertThat(query.sender.toByteArray())
      .isEqualTo(ByteUtils.uuidToBytes(defaultConnector.featureId?.uuid ?: fail("Null feature id")))
    val systemQuery = SystemQuery.parseFrom(query.request, ExtensionRegistryLite.getEmptyRegistry())
    assertThat(systemQuery.type).isEqualTo(SystemQueryType.APP_NAME)
  }

  @Test
  fun retrieveCompanionApplicationName_appNameCallbackOnNameReceivedInvokedWithName() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    defaultConnector.connect()
    val callbackCaptor =
      argumentCaptor<IDeviceCallback> {
        verify(mockFeatureCoordinator)
          .registerDeviceCallback(eq(device), eq(defaultConnector.featureId), capture())
      }
    val callback = mock<Connector.AppNameCallback>()

    defaultConnector.retrieveCompanionApplicationName(device, callback)
    val messageCaptor =
      argumentCaptor<DeviceMessage> {
        verify(mockFeatureCoordinator).sendMessage(eq(device), capture())
      }
    val query =
      Query.parseFrom(messageCaptor.firstValue.message, ExtensionRegistryLite.getEmptyRegistry())
    val appName = "Companion"
    val response = appName.toByteArray(StandardCharsets.UTF_8)
    val queryResponse =
      QueryResponse.newBuilder()
        .setQueryId(query.id)
        .setSuccess(true)
        .setResponse(ByteString.copyFrom(response))
        .build()
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        defaultConnector.featureId?.uuid,
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.QUERY_RESPONSE,
        queryResponse.toByteArray()
      )
    callbackCaptor.firstValue.onMessageReceived(device, deviceMessage)

    verify(callback).onNameReceived(appName)
  }

  @Test
  fun retrieveCompanionApplicationName_appNameCallbackOnErrorInvokedWithEmptyResponse() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    defaultConnector.connect()
    val callbackCaptor =
      argumentCaptor<IDeviceCallback> {
        verify(mockFeatureCoordinator)
          .registerDeviceCallback(eq(device), eq(defaultConnector.featureId), capture())
      }
    val deviceCallback = callbackCaptor.firstValue
    assertThat(deviceCallback).isNotNull()

    val callback = mock<Connector.AppNameCallback>()
    defaultConnector.retrieveCompanionApplicationName(device, callback)
    val messageCaptor =
      argumentCaptor<DeviceMessage> {
        verify(mockFeatureCoordinator).sendMessage(eq(device), capture())
      }
    val sentMessage = messageCaptor.firstValue
    assertThat(sentMessage).isNotNull()
    val query = Query.parseFrom(sentMessage.message, ExtensionRegistryLite.getEmptyRegistry())
    val queryResponse =
      QueryResponse.newBuilder()
        .setQueryId(query.id)
        .setSuccess(true)
        .setResponse(ByteString.EMPTY)
        .build()
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        defaultConnector.featureId?.uuid,
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.QUERY_RESPONSE,
        queryResponse.toByteArray()
      )
    deviceCallback.onMessageReceived(device, deviceMessage)

    verify(callback).onError()
  }

  @Test
  fun retrieveCompanionApplicationName_appNameCallbackOnErrorInvokedWithErrorResponse() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    defaultConnector.connect()
    val callbackCaptor =
      argumentCaptor<IDeviceCallback> {
        verify(mockFeatureCoordinator)
          .registerDeviceCallback(eq(device), eq(defaultConnector.featureId), capture())
      }
    val callback = mock<Connector.AppNameCallback>()

    defaultConnector.retrieveCompanionApplicationName(device, callback)
    val messageCaptor =
      argumentCaptor<DeviceMessage> {
        verify(mockFeatureCoordinator).sendMessage(eq(device), capture())
      }
    val query =
      Query.parseFrom(messageCaptor.firstValue.message, ExtensionRegistryLite.getEmptyRegistry())
    val appName = "Companion"
    val response = appName.toByteArray(StandardCharsets.UTF_8)
    val queryResponse =
      QueryResponse.newBuilder()
        .setQueryId(query.id)
        .setSuccess(false)
        .setResponse(ByteString.copyFrom(response))
        .build()
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        defaultConnector.featureId?.uuid,
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.QUERY_RESPONSE,
        queryResponse.toByteArray()
      )
    callbackCaptor.firstValue.onMessageReceived(device, deviceMessage)

    verify(callback).onError()
  }

  @Test
  fun retrieveCompanionApplicationName_appNameCallbackOnErrorInvokedWhenQueryFailedToSend() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.sendMessage(any(), any())).thenThrow(RemoteException())
    defaultConnector.connect()
    val callback = mock<Connector.AppNameCallback>()

    defaultConnector.retrieveCompanionApplicationName(device, callback)

    verify(callback).onError()
  }

  @Test
  fun createLocalConnector_createsConnectorWithFeatureCoordinator() {
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))

    val connector =
      CompanionConnector.createLocalConnector(context, USER_TYPE_ALL, mockFeatureCoordinator)

    assertThat(connector.featureCoordinator).isEqualTo(mockFeatureCoordinator)
    assertThat(connector.connectedDevices).containsExactly(device)
  }

  @Test
  fun createLocalConnector_createsConnectorWithConnectedDeviceManager() {
    val mockConnectedDeviceManager = mock<IConnectedDeviceManager.Stub>()
    whenever(mockConnectedDeviceManager.asBinder()).thenReturn(aliveBinder)
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockConnectedDeviceManager.activeUserConnectedDevices).thenReturn(listOf(device))

    val connector =
      CompanionConnector.createLocalConnector(context, USER_TYPE_ALL, mockConnectedDeviceManager)

    assertThat(connector.connectedDeviceManager).isEqualTo(mockConnectedDeviceManager)
    assertThat(connector.connectedDevices).containsExactly(device)
  }

  @Test
  fun connectedDevices_returnsEmptyListWhenPlatformIsDisconnected() {
    defaultConnector.apply {
      featureCoordinator = null
      connectedDeviceManager = null
    }

    assertThat(defaultConnector.connectedDevices).isEmpty()
  }

  @Test
  fun connectedDevices_userTypeDriverReturnsDriverDevices() {
    val connector =
      CompanionConnector(context, userType = USER_TYPE_DRIVER).apply {
        featureCoordinator = mockFeatureCoordinator
      }
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))

    assertThat(connector.connectedDevices).containsExactly(device)
  }

  @Test
  fun connectedDevices_userTypePassengerReturnsPassengerDevices() {
    val connector =
      CompanionConnector(context, userType = USER_TYPE_PASSENGER).apply {
        featureCoordinator = mockFeatureCoordinator
      }
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.connectedDevicesForPassengers).thenReturn(listOf(device))

    assertThat(connector.connectedDevices).containsExactly(device)
  }

  @Test
  fun connectedDevices_userTypeAllReturnsAllDevices() {
    val connector =
      CompanionConnector(context, userType = USER_TYPE_ALL).apply {
        featureCoordinator = mockFeatureCoordinator
      }
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))

    assertThat(connector.connectedDevices).containsExactly(device)
  }

  @Test
  fun binderForAction_returnsCorrectTypeForActions() {
    assertThat(defaultConnector.binderForAction(ACTION_BIND_FEATURE_COORDINATOR))
      .isEqualTo(defaultConnector.featureCoordinator?.asBinder())
    assertThat(defaultConnector.binderForAction(ACTION_BIND_FEATURE_COORDINATOR_FG))
      .isEqualTo(defaultConnector.featureCoordinator?.asBinder())
    assertThat(defaultConnector.binderForAction(ACTION_BIND_REMOTE_FEATURE))
      .isEqualTo(defaultConnector.connectedDeviceManager?.asBinder())
    assertThat(defaultConnector.binderForAction(ACTION_BIND_REMOTE_FEATURE_FG))
      .isEqualTo(defaultConnector.connectedDeviceManager?.asBinder())
    assertThat(defaultConnector.binderForAction("")).isNull()
  }

  /** All companion actions are resolved. */
  private val defaultServiceAnswer = Answer {
    val intent = it.arguments.first() as Intent
    val resolveInfo =
      ResolveInfo().apply { serviceInfo = ServiceInfo().apply { packageName = PACKAGE_NAME } }
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
  private val featureCoordinatorOnlyAnswer = Answer {
    val intent = it.arguments.first() as Intent
    val resolveInfo =
      ResolveInfo().apply { serviceInfo = ServiceInfo().apply { packageName = PACKAGE_NAME } }
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
  private val connectedDeviceManagerOnlyAnswer = Answer {
    val intent = it.arguments.first() as Intent
    val resolveInfo =
      ResolveInfo().apply { serviceInfo = ServiceInfo().apply { packageName = PACKAGE_NAME } }
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
