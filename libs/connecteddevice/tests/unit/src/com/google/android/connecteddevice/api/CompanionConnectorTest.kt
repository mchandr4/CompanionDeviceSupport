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
import android.os.RemoteException
import androidx.test.core.app.ApplicationProvider
import com.google.android.companionprotos.FeatureSupportResponse
import com.google.android.companionprotos.FeatureSupportStatus
import com.google.android.companionprotos.Query
import com.google.android.companionprotos.QueryResponse
import com.google.android.companionprotos.SystemQuery
import com.google.android.companionprotos.SystemQueryType
import com.google.android.connecteddevice.api.Connector.Companion.ACTION_BIND_FEATURE_COORDINATOR
import com.google.android.connecteddevice.api.Connector.Companion.ACTION_BIND_FEATURE_COORDINATOR_FG
import com.google.android.connecteddevice.api.Connector.Companion.USER_TYPE_ALL
import com.google.android.connecteddevice.api.Connector.Companion.USER_TYPE_DRIVER
import com.google.android.connecteddevice.api.Connector.Companion.USER_TYPE_PASSENGER
import com.google.android.connecteddevice.core.util.mockToBeAlive
import com.google.android.connecteddevice.core.util.mockToBeDead
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

  private val mockFeatureCoordinator = mockToBeAlive<IFeatureCoordinator>()

  private val mockFeatureCoordinatorListener = mockToBeAlive<IFeatureCoordinatorListener.Stub>()

  private val context = FakeContext(mockPackageManager)

  private lateinit var featureId: UUID
  private lateinit var defaultConnector: CompanionConnector

  @Before
  fun setUp() {
    featureId = UUID.randomUUID()
    defaultConnector =
      CompanionConnector(context).apply {
        featureCoordinator = mockFeatureCoordinator
        callback = mockCallback
        featureId = ParcelUuid(this@CompanionConnectorTest.featureId)
      }
  }

  @Test
  fun connect_bindsWithFgActionWhenIsForegroundProcessIsTrue() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = true)

    connector.connect()

    assertThat(context.bindingActions).containsExactly(ACTION_BIND_FEATURE_COORDINATOR_FG)
  }

  @Test
  fun connect_bindsWithBgActionWhenIsForegroundProcessIsFalse() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false)

    connector.connect()

    assertThat(context.bindingActions).containsExactly(ACTION_BIND_FEATURE_COORDINATOR)
  }

  @Test
  fun connect_bindOnceOnlyForMultipleCalls() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = true)

    connector.connect()
    connector.connect()

    assertThat(context.bindingActions).containsExactly(ACTION_BIND_FEATURE_COORDINATOR_FG)
  }

  @Test
  fun connect_fgRetriesBindWithRemoteFeatureActionIfFeatureCoordinatorReturnsNullBinding() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = true)

    connector.connect()
    context.serviceConnection.firstOrNull()!!.onNullBinding(ComponentName(PACKAGE_NAME, FG_NAME))

    assertThat(context.bindingActions).containsExactly(ACTION_BIND_FEATURE_COORDINATOR_FG)
  }

  @Test
  fun connect_bgRetriesBindWithRemoteFeatureActionIfFeatureCoordinatorReturnsNullBinding() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false)

    connector.connect()
    context.serviceConnection.firstOrNull()!!.onNullBinding(ComponentName(PACKAGE_NAME, BG_NAME))

    assertThat(context.bindingActions).containsExactly(ACTION_BIND_FEATURE_COORDINATOR)
  }

  @Test
  fun connect_canConnectAfterServiceDisconnect() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = true)
    connector.connect()
    val connection = context.serviceConnection.firstOrNull()
    val component = ComponentName(PACKAGE_NAME, BG_NAME)
    connection?.onServiceConnected(component, mockFeatureCoordinator.asBinder())
    connection?.onServiceDisconnected(component)

    connector.connect()

    assertThat(context.bindingActions)
      .containsExactly(ACTION_BIND_FEATURE_COORDINATOR_FG, ACTION_BIND_FEATURE_COORDINATOR_FG)
  }

  @Test
  fun connect_canConnectAfterBindingDied() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = true)
    connector.connect()
    val connection = context.serviceConnection.firstOrNull()
    val component = ComponentName(PACKAGE_NAME, BG_NAME)
    connection?.onServiceConnected(component, mockFeatureCoordinator.asBinder())
    connection?.onBindingDied(component)

    connector.connect()

    assertThat(context.bindingActions)
      .containsExactly(ACTION_BIND_FEATURE_COORDINATOR_FG, ACTION_BIND_FEATURE_COORDINATOR_FG)
  }

  @Test
  fun connect_canConnectAfterNullBinding() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = true)
    connector.connect()
    val connection = context.serviceConnection.firstOrNull()
    val component = ComponentName(PACKAGE_NAME, BG_NAME)
    connection?.onServiceConnected(component, mockFeatureCoordinator.asBinder())
    connection?.onNullBinding(component)

    connector.connect()

    assertThat(context.bindingActions)
      .containsExactly(ACTION_BIND_FEATURE_COORDINATOR_FG, ACTION_BIND_FEATURE_COORDINATOR_FG)
  }

  @Test
  fun disconnect_invokesOnDisconnectedWhenFeatureCoordinatorConnected() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector =
      CompanionConnector(context, isForegroundProcess = false).apply { callback = mockCallback }

    connector.connect()
    context.serviceConnection
      .firstOrNull()
      ?.onServiceConnected(ComponentName(PACKAGE_NAME, BG_NAME), mockFeatureCoordinator.asBinder())
    connector.disconnect()

    verify(mockCallback).onDisconnected()
  }

  @Test
  fun disconnect_notInvokedWithNoBoundService() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector =
      CompanionConnector(context, isForegroundProcess = false).apply { callback = mockCallback }

    connector.connect()
    connector.disconnect()

    verify(mockCallback, never()).onDisconnected()
  }

  @Test
  fun disconnect_doesNotThrowWhileUnbindingUnboundService() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector =
      CompanionConnector(FailingContext(mockPackageManager), isForegroundProcess = false).apply {
        callback = mockCallback
      }

    connector.disconnect()
  }

  @Test
  fun onConnected_invokedWhenBindingSucceeds() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector =
      CompanionConnector(context, isForegroundProcess = false).apply { callback = mockCallback }

    connector.connect()
    context.serviceConnection
      .firstOrNull()
      ?.onServiceConnected(ComponentName(PACKAGE_NAME, BG_NAME), mockFeatureCoordinator.asBinder())

    verify(mockCallback).onConnected()
  }

  @Test
  fun onDisconnected_invokedWhenServiceDisconnects() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector =
      CompanionConnector(context, isForegroundProcess = false).apply { callback = mockCallback }

    connector.connect()
    val componentName = ComponentName(PACKAGE_NAME, BG_NAME)
    context.serviceConnection
      .firstOrNull()
      ?.onServiceConnected(componentName, mockFeatureCoordinator.asBinder())
    context.serviceConnection.firstOrNull()!!.onServiceDisconnected(componentName)

    verify(mockCallback).onDisconnected()
  }

  @Test
  fun onDisconnected_invokedWhenServiceBindingDied() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector =
      CompanionConnector(context, isForegroundProcess = false).apply { callback = mockCallback }

    connector.connect()
    context.serviceConnection.firstOrNull()!!.onBindingDied(ComponentName(PACKAGE_NAME, BG_NAME))

    verify(mockCallback).onDisconnected()
  }

  @Test
  fun onFailedToConnect_invokedWhenServiceIsNotFound() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector =
      CompanionConnector(context, isForegroundProcess = false).apply { callback = mockCallback }

    connector.connect()
    context.serviceConnection.firstOrNull()!!.onNullBinding(ComponentName(PACKAGE_NAME, BG_NAME))

    verify(mockCallback).onFailedToConnect()
  }

  @Test
  fun onFailedToConnect_invokedWithNullBindingFromFeatureCoordinatorAndNoCDMService() {
    setQueryIntentServicesAnswer(notFoundAnswer)
    val connector =
      CompanionConnector(context, isForegroundProcess = false).apply { callback = mockCallback }

    connector.connect()

    verify(mockCallback).onFailedToConnect()
  }

  @Test
  fun onFailedToConnect_invokedAfterBindingRetryLimitExceeded() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
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
  fun unbindService_invokedWhenServiceIsNotFound() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector =
      CompanionConnector(context, isForegroundProcess = false).apply { callback = mockCallback }

    connector.connect()
    val connection = context.serviceConnection.first()
    connection.onNullBinding(ComponentName(PACKAGE_NAME, FG_NAME))

    assertThat(context.unbindServiceConnection).contains(connection)
  }

  @Test
  fun unbindService_invokedWhenServiceBindingDied() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector =
      CompanionConnector(context, isForegroundProcess = false).apply { callback = mockCallback }

    connector.connect()
    val connection = context.serviceConnection.first()
    connection.onBindingDied(ComponentName(PACKAGE_NAME, FG_NAME))

    assertThat(context.unbindServiceConnection).contains(connection)
  }

  @Test
  fun featureCoordinator_isNotNullAfterServiceConnection() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = false)

    connector.connect()
    context.serviceConnection
      .firstOrNull()
      ?.onServiceConnected(ComponentName(PACKAGE_NAME, BG_NAME), mockFeatureCoordinator.asBinder())

    assertThat(connector.featureCoordinator).isNotNull()
  }

  @Test
  fun connect_registersCommonCallbacks() {
    defaultConnector.connect()

    verify(mockFeatureCoordinator).registerDeviceAssociationCallback(any())
    verify(mockFeatureCoordinator).registerOnLogRequestedListener(any(), any())
  }

  @Test
  fun connect_multipleCallsOnlyRegistersCallbacksOnce() {
    val featureId = ParcelUuid(UUID.randomUUID())
    val connector =
      CompanionConnector(context, userType = USER_TYPE_DRIVER).apply {
        featureCoordinator = mockFeatureCoordinator
        this.featureId = featureId
      }
    val device = createConnectedDevice()
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))

    connector.connect()
    connector.connect()

    verify(mockFeatureCoordinator).registerDeviceCallback(eq(device), eq(featureId), any())
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
    val device = createConnectedDevice()
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
    val device = createConnectedDevice(belongsToDriver = false)
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
    val device = createConnectedDevice()
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))

    connector.connect()

    verify(mockFeatureCoordinator).registerDeviceCallback(eq(device), eq(featureId), any())
  }

  @Test
  fun connect_deviceCallbacksNotRegisteredWhenMissingFeatureId() {
    defaultConnector.featureId = null
    val device = createConnectedDevice()
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))

    defaultConnector.connect()

    verify(mockFeatureCoordinator, never()).registerDeviceCallback(any(), any(), any())
  }

  @Test
  fun disconnect_unregistersFeatureCoordinatorCallbacks() {
    val device = createConnectedDevice()
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))

    defaultConnector.disconnect()

    verify(mockFeatureCoordinator).unregisterConnectionCallback(any())
    verify(mockFeatureCoordinator).unregisterDeviceAssociationCallback(any())
    verify(mockFeatureCoordinator).unregisterOnLogRequestedListener(any(), any())
    verify(mockFeatureCoordinator)
      .unregisterDeviceCallback(eq(device), eq(defaultConnector.featureId), any())
  }

  @Test
  fun disconnect_featureCoordinatorWithoutFeatureIdDoesNotThrow() {
    defaultConnector.featureId = null
    val device = createConnectedDevice()
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))

    defaultConnector.disconnect()
  }

  @Test
  fun disconnect_featureCoordinatorRemoteExceptionIsCaught() {
    val device = createConnectedDevice()
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.unregisterConnectionCallback(any()))
      .thenThrow(RemoteException())

    defaultConnector.disconnect()
  }

  @Test
  fun onDeviceConnected_registersDeviceCallbackWithFeatureId() {
    val device = createConnectedDevice()

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
    val device = createConnectedDevice()
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
    val device = createConnectedDevice()
    defaultConnector.connect()

    argumentCaptor<IConnectionCallback> {
      verify(mockFeatureCoordinator).registerDriverConnectionCallback(capture())
      firstValue.onDeviceDisconnected(device)
    }

    verify(mockCallback).onDeviceDisconnected(eq(device))
  }

  @Test
  fun onDeviceDisconnected_unregistersDeviceCallback() {
    val device = createConnectedDevice()
    defaultConnector.connect()

    argumentCaptor<IConnectionCallback> {
      verify(mockFeatureCoordinator).registerDriverConnectionCallback(capture())
      firstValue.onDeviceDisconnected(device)
    }

    verify(mockFeatureCoordinator)
      .unregisterDeviceCallback(eq(device), eq(defaultConnector.featureId), any())
  }

  @Test
  fun onSecureChannelEstablished_invokedWhenChannelEstablished() {
    val device = createConnectedDevice()
    val secureDevice = createConnectedDevice(hasSecureChannel = true)
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
    val device = createConnectedDevice(hasSecureChannel = true)
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    defaultConnector.connect()

    verify(mockCallback).onSecureChannelEstablished(device)
  }

  @Test
  fun onSecureChannelEstablished_invokedOnDeviceConnectedIfChannelAlreadyEstablished() {
    val device = createConnectedDevice(hasSecureChannel = true)
    defaultConnector.connect()

    argumentCaptor<IConnectionCallback> {
      verify(mockFeatureCoordinator).registerDriverConnectionCallback(capture())
      firstValue.onDeviceConnected(device)
    }
    verify(mockCallback).onSecureChannelEstablished(device)
  }

  @Test
  fun onDeviceError_invokedOnError() {
    val device = createConnectedDevice()
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
    val device = createConnectedDevice(hasSecureChannel = true)
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
    val device = createConnectedDevice(hasSecureChannel = true)
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

    // TODO: b/242360799 to fix the argument lint issue.
    verify(mockCallback).onMessageFailedToSend(deviceId, message, isTransient = false)
  }

  @Test
  fun onMessageFailedToSend_invokedWhenRemoteExceptionThrownId() {
    val device = createConnectedDevice(hasSecureChannel = true)
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.sendMessage(any(), any())).thenThrow(RemoteException())
    val message = ByteUtils.randomBytes(10)
    defaultConnector.connect()

    defaultConnector.sendMessageSecurely(device.deviceId, message)

    verify(mockCallback).onMessageFailedToSend(device.deviceId, message, isTransient = false)
  }

  @Test
  fun onMessageFailedToSend_invokedWhenRemoteExceptionThrown() {
    val device = createConnectedDevice(hasSecureChannel = true)
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.sendMessage(any(), any())).thenThrow(RemoteException())
    val message = ByteUtils.randomBytes(10)
    defaultConnector.connect()

    defaultConnector.sendMessageSecurely(device, message)

    verify(mockCallback).onMessageFailedToSend(device.deviceId, message, isTransient = false)
  }

  @Test
  fun onMessageFailedToSend_invokedWhenErrorRetrievingDevices() {
    val deviceId = UUID.randomUUID().toString()
    val message = ByteUtils.randomBytes(10)
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenThrow(RemoteException())
    defaultConnector.connect()

    defaultConnector.sendMessageSecurely(deviceId, message)

    verify(mockCallback).onMessageFailedToSend(deviceId, message, isTransient = false)
  }

  @Test
  fun onMessageFailedToSend_invokedWhenSendMessageIdCalledBeforeServiceConnection() {
    defaultConnector.featureCoordinator = null
    val deviceId = UUID.randomUUID().toString()
    val message = ByteUtils.randomBytes(10)

    defaultConnector.sendMessageSecurely(deviceId, message)

    verify(mockCallback).onMessageFailedToSend(deviceId, message, isTransient = true)
  }

  @Test
  fun onMessageFailedToSend_invokedWhenSendMessageCalledBeforeServiceConnection() {
    defaultConnector.featureCoordinator = null
    val device = createConnectedDevice(hasSecureChannel = true)
    val message = ByteUtils.randomBytes(10)

    defaultConnector.sendMessageSecurely(device, message)

    verify(mockCallback).onMessageFailedToSend(device.deviceId, message, isTransient = true)
  }

  @Test
  fun onMessageReceived_invokedWhenGenericMessageReceived() {
    val device = createConnectedDevice(hasSecureChannel = true)
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
    val device = createConnectedDevice()
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    defaultConnector.connect()

    assertThat(defaultConnector.getConnectedDeviceById(device.deviceId)).isEqualTo(device)
  }

  @Test
  fun getConnectedDeviceById_returnsNullWhenNotConnected() {
    val device = createConnectedDevice()
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

    assertThat(defaultConnector.getConnectedDeviceById(UUID.randomUUID().toString())).isNull()
  }

  @Test
  fun sendQuerySecurely_sendsQueryToOwnFeatureId() {
    val device = createConnectedDevice(hasSecureChannel = true)
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
    val device = createConnectedDevice(hasSecureChannel = true)
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
    val device = createConnectedDevice(hasSecureChannel = true)
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
    val device = createConnectedDevice(hasSecureChannel = true)
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
    val deviceId = UUID.randomUUID().toString()
    val request = ByteUtils.randomBytes(10)
    val parameters = ByteUtils.randomBytes(10)
    val callback = mock<Connector.QueryCallback>()

    defaultConnector.sendQuerySecurely(deviceId, request, parameters, callback)

    verify(callback).onQueryFailedToSend(isTransient = true)
  }

  @Test
  fun sendQuery_queryCallbackOnQueryNotSentInvokedBeforeServiceConnectionWithDevice() {
    defaultConnector.featureCoordinator = null
    val device = createConnectedDevice(hasSecureChannel = true)
    val request = ByteUtils.randomBytes(10)
    val parameters = ByteUtils.randomBytes(10)
    val callback = mock<Connector.QueryCallback>()

    defaultConnector.sendQuerySecurely(device, request, parameters, callback)

    verify(callback).onQueryFailedToSend(isTransient = true)
  }

  @Test
  fun sendQuery_queryCallbackOnQueryNotSentInvokedIfSendMessageThrowsRemoteException() {
    val device = createConnectedDevice(hasSecureChannel = true)
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.sendMessage(any(), any())).thenThrow(RemoteException())
    defaultConnector.connect()
    val request = ByteUtils.randomBytes(10)
    val parameters: ByteArray? = null
    val callback = mock<Connector.QueryCallback>()

    defaultConnector.sendQuerySecurely(device.deviceId, request, parameters, callback)

    verify(callback).onQueryFailedToSend(isTransient = false)
  }

  @Test
  fun sendQuery_queryCallbackOnQueryNotSentInvokedIfDeviceNotFound() {
    val device = createConnectedDevice(hasSecureChannel = true)
    defaultConnector.connect()
    val request = ByteUtils.randomBytes(10)
    val parameters = ByteUtils.randomBytes(10)
    val callback = mock<Connector.QueryCallback>()

    defaultConnector.sendQuerySecurely(device.deviceId, request, parameters, callback)

    verify(callback).onQueryFailedToSend(isTransient = false)
  }

  @Test
  fun onQueryReceived_invokedWithQueryFields() {
    val device = createConnectedDevice(hasSecureChannel = true)
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
    val device = createConnectedDevice(hasSecureChannel = true)
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    defaultConnector.connect()
    val nonExistentQueryId = 0
    val response = ByteUtils.randomBytes(10)

    defaultConnector.respondToQuerySecurely(device, nonExistentQueryId, success = true, response)

    verify(mockFeatureCoordinator, never()).sendMessage(any(), any())
  }

  @Test
  fun respondToQuery_sendsResponseToSenderIfSameFeatureId() {
    val device = createConnectedDevice(hasSecureChannel = true)
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
    defaultConnector.respondToQuerySecurely(device, queryId, success = true, response)
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
    val device = createConnectedDevice(hasSecureChannel = true)
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

    defaultConnector.respondToQuerySecurely(device, queryId, success = true, response)
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
    val device = createConnectedDevice(hasSecureChannel = true)
    val queryId = 0
    val response = ByteUtils.randomBytes(10)

    defaultConnector.respondToQuerySecurely(device, queryId, success = true, response)
  }

  @Test
  fun retrieveCompanionApplicationName_sendsAppNameQueryToSystemFeature() {
    val device = createConnectedDevice(hasSecureChannel = true)
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
    val device = createConnectedDevice(hasSecureChannel = true)
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
    val device = createConnectedDevice(hasSecureChannel = true)
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
    val device = createConnectedDevice(hasSecureChannel = true)
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
    val device = createConnectedDevice(hasSecureChannel = true)
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
    val device = createConnectedDevice(hasSecureChannel = true)
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))

    val connector =
      CompanionConnector.createLocalConnector(context, USER_TYPE_ALL, mockFeatureCoordinator)

    assertThat(connector.featureCoordinator).isEqualTo(mockFeatureCoordinator)
    assertThat(connector.connectedDevices).containsExactly(device)
    assertThat(connector.isConnected).isTrue()
  }

  @Test
  fun connectedDevices_returnsEmptyListWhenPlatformIsDisconnected() {
    defaultConnector.apply { featureCoordinator = null }

    assertThat(defaultConnector.connectedDevices).isEmpty()
  }

  @Test
  fun connectedDevices_userTypeDriverReturnsDriverDevices() {
    val connector =
      CompanionConnector(context, userType = USER_TYPE_DRIVER).apply {
        featureCoordinator = mockFeatureCoordinator
      }
    val device = createConnectedDevice(hasSecureChannel = true)
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))

    assertThat(connector.connectedDevices).containsExactly(device)
  }

  @Test
  fun connectedDevices_userTypePassengerReturnsPassengerDevices() {
    val connector =
      CompanionConnector(context, userType = USER_TYPE_PASSENGER).apply {
        featureCoordinator = mockFeatureCoordinator
      }
    val device = createConnectedDevice(hasSecureChannel = true)
    whenever(mockFeatureCoordinator.connectedDevicesForPassengers).thenReturn(listOf(device))

    assertThat(connector.connectedDevices).containsExactly(device)
  }

  @Test
  fun connectedDevices_userTypeAllReturnsAllDevices() {
    val connector =
      CompanionConnector(context, userType = USER_TYPE_ALL).apply {
        featureCoordinator = mockFeatureCoordinator
      }
    val device = createConnectedDevice(hasSecureChannel = true)
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))

    assertThat(connector.connectedDevices).containsExactly(device)
  }

  @Test
  fun binderForAction_returnsCorrectTypeForActions() {
    assertThat(defaultConnector.binderForAction(ACTION_BIND_FEATURE_COORDINATOR))
      .isEqualTo(defaultConnector.featureCoordinator?.asBinder())
    assertThat(defaultConnector.binderForAction(ACTION_BIND_FEATURE_COORDINATOR_FG))
      .isEqualTo(defaultConnector.foregroundUserBinder.asBinder())
    assertThat(defaultConnector.binderForAction("")).isNull()
  }

  @Test
  fun startAssociation_startsAssociationOnFeatureCoordinator() {
    val mockAssociationCallback = mock<IAssociationCallback>()

    defaultConnector.startAssociation(mockAssociationCallback)

    verify(mockFeatureCoordinator).startAssociation(mockAssociationCallback)
  }

  @Test
  fun startAssociationWithId_startsAssociationWithIdentifierOnFeatureCoordinator() {
    val mockAssociationCallback = mock<IAssociationCallback>()
    val id = ParcelUuid(UUID.randomUUID())

    defaultConnector.startAssociation(id, mockAssociationCallback)

    verify(mockFeatureCoordinator).startAssociationWithIdentifier(mockAssociationCallback, id)
  }

  @Test
  fun stopAssociation_stopsAssociationOnFeatureCoordinator() {
    defaultConnector.stopAssociation()

    verify(mockFeatureCoordinator).stopAssociation()
  }

  @Test
  fun acceptVerification_acceptsVerificationOnFeatureCoordinator() {
    defaultConnector.acceptVerification()

    verify(mockFeatureCoordinator).acceptVerification()
  }

  @Test
  fun removeAssociatedDevice_removesAssociatedDeviceOnFeatureCoordinator() {
    val deviceId = UUID.randomUUID().toString()

    defaultConnector.removeAssociatedDevice(deviceId)

    verify(mockFeatureCoordinator).removeAssociatedDevice(deviceId)
  }

  @Test
  fun enableAssociatedDeviceConnection_enablesAssociatedDeviceConnectionOnFeatureCoordinator() {
    val deviceId = UUID.randomUUID().toString()

    defaultConnector.enableAssociatedDeviceConnection(deviceId)

    verify(mockFeatureCoordinator).enableAssociatedDeviceConnection(deviceId)
  }

  @Test
  fun disableAssociatedDeviceConnection_disablesAssociatedDeviceConnectionOnFeatureCoordinator() {
    val deviceId = UUID.randomUUID().toString()

    defaultConnector.disableAssociatedDeviceConnection(deviceId)

    verify(mockFeatureCoordinator).disableAssociatedDeviceConnection(deviceId)
  }

  @Test
  fun retrieveAssociatedDevices_retrievesAssociatedDevicesFromFeatureCoordinator() {
    val mockListener = mock<IOnAssociatedDevicesRetrievedListener>()

    defaultConnector.retrieveAssociatedDevices(mockListener)

    verify(mockFeatureCoordinator).retrieveAssociatedDevices(mockListener)
  }

  @Test
  fun retrieveAssociatedDevicesForDriver_retrievesAssociatedDevicesFromFeatureCoordinator() {
    val mockListener = mock<IOnAssociatedDevicesRetrievedListener>()

    defaultConnector.retrieveAssociatedDevicesForDriver(mockListener)

    verify(mockFeatureCoordinator).retrieveAssociatedDevicesForDriver(mockListener)
  }

  @Test
  fun retrieveAssociatedDevicesForPassengers_retrievesAssociatedDevicesFromFeatureCoordinator() {
    val mockListener = mock<IOnAssociatedDevicesRetrievedListener>()

    defaultConnector.retrieveAssociatedDevicesForPassengers(mockListener)

    verify(mockFeatureCoordinator).retrieveAssociatedDevicesForPassengers(mockListener)
  }

  @Test
  fun claimAssociatedDevice_claimsDevice() {
    val deviceId = UUID.randomUUID().toString()

    defaultConnector.claimAssociatedDevice(deviceId)

    verify(mockFeatureCoordinator).claimAssociatedDevice(deviceId)
  }

  @Test
  fun removeAssociatedDeviceClaim_removesDeviceClaim() {
    val deviceId = UUID.randomUUID().toString()

    defaultConnector.removeAssociatedDeviceClaim(deviceId)

    verify(mockFeatureCoordinator).removeAssociatedDeviceClaim(deviceId)
  }

  @Test
  fun notifyListeners_onFeatureCoordinatorAlreadyInitialized() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connectorForeground = CompanionConnector(context, isForegroundProcess = true)
    val connectorBackground =
      CompanionConnector(context, isForegroundProcess = false).apply {
        featureCoordinator = mockFeatureCoordinator
      }
    val foregroundBinder = connectorBackground.foregroundUserBinder.asBinder()

    connectorForeground.connect()
    context.serviceConnection
      .firstOrNull()!!
      .onServiceConnected(ComponentName(PACKAGE_NAME, FG_NAME), foregroundBinder)

    assertThat(connectorForeground.featureCoordinatorStatusNotifier).isNotNull()
    assertThat(connectorBackground.registeredListeners.size).isEqualTo(1)
    assertThat(connectorForeground.featureCoordinator).isEqualTo(mockFeatureCoordinator)
  }

  @Test
  fun notifyListeners_onFeatureCoordinatorInitialized() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector =
      CompanionConnector(context, isForegroundProcess = false).apply {
        featureCoordinatorListener = mockFeatureCoordinatorListener
        registeredListeners = mutableListOf(mockFeatureCoordinatorListener)
      }

    connector.connect()
    context.serviceConnection
      .firstOrNull()!!
      .onServiceConnected(ComponentName(PACKAGE_NAME, BG_NAME), mockFeatureCoordinator.asBinder())

    verify(mockFeatureCoordinatorListener).onFeatureCoordinatorInitialized(any())
  }

  @Test
  fun doNotNotifyListeners_onFeatureCoordinatorNotInitialized() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val mockFeatureCoordinatorStatusNotifier = mockToBeAlive<IFeatureCoordinatorStatusNotifier>()
    val connector =
      CompanionConnector(context, isForegroundProcess = true).apply {
        featureCoordinatorListener = mockFeatureCoordinatorListener
      }

    connector.connect()
    context.serviceConnection
      .firstOrNull()!!
      .onServiceConnected(
        ComponentName(PACKAGE_NAME, FG_NAME),
        mockFeatureCoordinatorStatusNotifier.asBinder()
      )

    verify(mockFeatureCoordinatorListener, never()).onFeatureCoordinatorInitialized(any())
  }

  @Test
  fun unregisterListener_onForegroundUserServiceDisconnected() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connectorForeground = CompanionConnector(context, isForegroundProcess = true)
    val connectorBackground = CompanionConnector(context, isForegroundProcess = false)
    val foregroundBinder = connectorBackground.foregroundUserBinder.asBinder()

    connectorForeground.connect()
    context.serviceConnection
      .firstOrNull()!!
      .onServiceConnected(ComponentName(PACKAGE_NAME, FG_NAME), foregroundBinder)
    context.serviceConnection
      .firstOrNull()!!
      .onServiceDisconnected(ComponentName(PACKAGE_NAME, FG_NAME))

    assertThat(connectorBackground.registeredListeners.size).isEqualTo(0)
  }

  @Test
  fun scrubDeadListeners_onNotifyListeners() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val mockListenerDeadA = mockToBeDead<IFeatureCoordinatorListener.Stub>()
    val mockListenerDeadB = mockToBeDead<IFeatureCoordinatorListener.Stub>()
    val mockListenerAlive = mockToBeAlive<IFeatureCoordinatorListener.Stub>()
    val connector =
      CompanionConnector(context, isForegroundProcess = false).apply {
        registeredListeners = mutableListOf(mockListenerDeadA, mockListenerDeadB, mockListenerAlive)
      }

    connector.connect()
    context.serviceConnection
      .firstOrNull()!!
      .onServiceConnected(ComponentName(PACKAGE_NAME, BG_NAME), mockFeatureCoordinator.asBinder())

    assertThat(connector.registeredListeners.size).isEqualTo(1)
    assertThat(connector.registeredListeners.firstOrNull()).isEqualTo(mockListenerAlive)
  }

  @Test
  fun updateFeatureCoordinator_onPrevioulyNotConnected() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = true)

    connector.connect()
    connector.featureCoordinatorListener.onFeatureCoordinatorInitialized(mockFeatureCoordinator)

    assertThat(connector.featureCoordinator).isEqualTo(mockFeatureCoordinator)
  }

  @Test
  fun doNotUpdateFeatureCoordinator_onPreviouslyConnected() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val connector = CompanionConnector(context, isForegroundProcess = true)
    val mockFeatureCoordinatorConnected = mockToBeAlive<IFeatureCoordinator>()

    connector.connect()
    connector.featureCoordinator = mockFeatureCoordinatorConnected
    connector.featureCoordinatorListener.onFeatureCoordinatorInitialized(mockFeatureCoordinator)

    assertThat(connector.featureCoordinator).isNotEqualTo(mockFeatureCoordinator)
  }

  @Test
  fun invokeCallback_binderIsIncompatible() {
    setQueryIntentServicesAnswer(defaultServiceAnswer)
    val mockBinder: IFeatureCoordinator.Stub = mock()
    whenever(mockBinder.queryLocalInterface(any())).thenThrow(SecurityException())
    whenever(mockBinder.isBinderAlive).thenReturn(true)
    val connector =
      CompanionConnector(context, isForegroundProcess = true).apply { callback = mockCallback }
    connector.connect()
    val connection = context.serviceConnection.firstOrNull()
    val component = ComponentName(PACKAGE_NAME, FG_NAME)
    connection?.onServiceConnected(component, mockBinder)

    verify(mockCallback).onFailedToConnect()
  }

  @Test
  fun isFeatureSupported_noFeatureId_notSupported() = runBlocking {
    val connector = CompanionConnector(context)
    val device = createConnectedDevice()

    assertThat(connector.isFeatureSupported(device)).isFalse()
  }

  @Test
  fun isFeatureSupported_sendsSystemQuery() = runBlocking {
    val device = createConnectedDevice(hasSecureChannel = true)
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    defaultConnector.connect()

    // Immediate execution to send out the query.
    CoroutineScope(Dispatchers.Main.immediate).launch {
      val unused = defaultConnector.isFeatureSupported(device)
    }

    // Assertion: successfully parse the outbound message as a SystemQuery for the feature status.
    val messageCaptor =
      argumentCaptor<DeviceMessage> {
        verify(mockFeatureCoordinator).sendMessage(eq(device), capture())
      }
    val query =
      Query.parseFrom(messageCaptor.firstValue.message, ExtensionRegistryLite.getEmptyRegistry())
    val systemQuery = SystemQuery.parseFrom(query.request, ExtensionRegistryLite.getEmptyRegistry())
    assertThat(systemQuery.type).isEqualTo(SystemQueryType.IS_FEATURE_SUPPORTED)
    assertThat(systemQuery.payloadsList.size).isEqualTo(1)

    val queriedFeatureId =
      UUID.fromString(
        systemQuery.payloadsList.first().toByteArray().toString(StandardCharsets.UTF_8)
      )
    assertThat(queriedFeatureId).isEqualTo(featureId)
  }

  @Test
  fun isFeatureSupported_emptyResponse_notSupported() {
    // Arrange.
    val device = createConnectedDevice(hasSecureChannel = true)
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    defaultConnector.connect()

    // Action - immediate execution to send out the query.
    val deferred =
      CoroutineScope(Dispatchers.Main.immediate).async {
        defaultConnector.isFeatureSupported(device)
      }

    // Generate an empty response.
    val response = ByteArray(0)

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
    val callbackCaptor =
      argumentCaptor<IDeviceCallback> {
        verify(mockFeatureCoordinator)
          .registerDeviceCallback(eq(device), eq(defaultConnector.featureId), capture())
      }
    callbackCaptor.firstValue.onMessageReceived(device, deviceMessage)

    // Assert
    runBlocking { assertThat(deferred.await()).isFalse() }
  }

  @Test
  fun isFeatureSupported_nonProtoResponse_notSupported() {
    // Arrange.
    val device = createConnectedDevice(hasSecureChannel = true)
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    defaultConnector.connect()

    // Action - immediate execution to send out the query.
    val deferred =
      CoroutineScope(Dispatchers.Main.immediate).async {
        defaultConnector.isFeatureSupported(device)
      }

    // Generate a response that cannot be parsed (should at least contain UUID, which is 16 bytes).
    val response = ByteUtils.randomBytes(10)

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
    val callbackCaptor =
      argumentCaptor<IDeviceCallback> {
        verify(mockFeatureCoordinator)
          .registerDeviceCallback(eq(device), eq(defaultConnector.featureId), capture())
      }
    callbackCaptor.firstValue.onMessageReceived(device, deviceMessage)

    // Assert
    runBlocking { assertThat(deferred.await()).isFalse() }
  }

  @Test
  fun isFeatureSupported_notSupportedResponse_notSupported() {
    // Arrange.
    val device = createConnectedDevice(hasSecureChannel = true)
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    defaultConnector.connect()

    // Action - immediate execution to send out the query.
    val deferred =
      CoroutineScope(Dispatchers.Main.immediate).async {
        defaultConnector.isFeatureSupported(device)
      }

    val status =
      FeatureSupportStatus.newBuilder().run {
        featureId = defaultConnector.featureId!!.uuid.toString()
        isSupported = false
        build()
      }
    val response =
      FeatureSupportResponse.newBuilder().run {
        addStatuses(status)
        build()
      }

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
        .setResponse(ByteString.copyFrom(response.toByteArray()))
        .build()
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        defaultConnector.featureId?.uuid,
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.QUERY_RESPONSE,
        queryResponse.toByteArray()
      )
    val callbackCaptor =
      argumentCaptor<IDeviceCallback> {
        verify(mockFeatureCoordinator)
          .registerDeviceCallback(eq(device), eq(defaultConnector.featureId), capture())
      }
    callbackCaptor.firstValue.onMessageReceived(device, deviceMessage)

    // Assert
    runBlocking { assertThat(deferred.await()).isFalse() }
  }

  @Test
  fun isFeatureSupported_supportedResponse_featureSupported() {
    // Arrange.
    val device = createConnectedDevice(hasSecureChannel = true)
    whenever(mockFeatureCoordinator.connectedDevicesForDriver).thenReturn(listOf(device))
    whenever(mockFeatureCoordinator.allConnectedDevices).thenReturn(listOf(device))
    defaultConnector.connect()

    // Action - immediate execution to send out the query.
    val deferred =
      CoroutineScope(Dispatchers.Main.immediate).async {
        defaultConnector.isFeatureSupported(device)
      }

    val status =
      FeatureSupportStatus.newBuilder().run {
        featureId = defaultConnector.featureId!!.uuid.toString()
        isSupported = true
        build()
      }
    val response =
      FeatureSupportResponse.newBuilder().run {
        addStatuses(status)
        build()
      }

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
        .setResponse(ByteString.copyFrom(response.toByteArray()))
        .build()
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        defaultConnector.featureId?.uuid,
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.QUERY_RESPONSE,
        queryResponse.toByteArray()
      )
    val callbackCaptor =
      argumentCaptor<IDeviceCallback> {
        verify(mockFeatureCoordinator)
          .registerDeviceCallback(eq(device), eq(defaultConnector.featureId), capture())
      }
    callbackCaptor.firstValue.onMessageReceived(device, deviceMessage)

    // Assert
    runBlocking { assertThat(deferred.await()).isTrue() }
  }

  private fun setQueryIntentServicesAnswer(answer: Answer<List<ResolveInfo>>) {
    whenever(mockPackageManager.queryIntentServices(any(), any<Int>())).thenAnswer(answer)
  }

  private val defaultServiceAnswer = Answer {
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

  /** No companion actions are resolved. */
  private val notFoundAnswer = Answer<List<ResolveInfo>> { listOf() }

  private open class FakeContext(val mockPackageManager: PackageManager) :
    ContextWrapper(ApplicationProvider.getApplicationContext()) {

    val bindingActions = mutableListOf<String>()

    var serviceConnection = mutableListOf<ServiceConnection>()

    var unbindServiceConnection = mutableListOf<ServiceConnection>()

    override fun getPackageManager(): PackageManager = mockPackageManager

    override fun bindService(service: Intent?, conn: ServiceConnection, flags: Int): Boolean {
      service?.action?.let { bindingActions.add(it) }
      serviceConnection.add(conn)
      return super.bindService(service, conn, flags)
    }

    override fun unbindService(conn: ServiceConnection) {
      unbindServiceConnection.add(conn)
      super.unbindService(conn)
    }
  }

  private class FailingContext(mockPackageManager: PackageManager) :
    FakeContext(mockPackageManager) {

    override fun bindService(service: Intent?, conn: ServiceConnection, flags: Int): Boolean {
      serviceConnection.add(conn)
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

    private fun createConnectedDevice(
      belongsToDriver: Boolean = true,
      hasSecureChannel: Boolean = false
    ) =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ belongsToDriver,
        hasSecureChannel,
      )
  }
}
