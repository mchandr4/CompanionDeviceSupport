package com.google.android.connecteddevice.api

import android.os.ParcelUuid
import android.os.RemoteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.companionprotos.OperationProto.OperationType
import com.google.android.companionprotos.Query
import com.google.android.companionprotos.QueryResponse
import com.google.android.connecteddevice.api.SafeConnector.QueryCallback
import com.google.android.connecteddevice.api.external.ISafeOnAssociatedDevicesRetrievedListener
import com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener
import com.google.android.connecteddevice.core.util.mockToBeAlive
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.model.DeviceMessage.OperationType.CLIENT_MESSAGE
import com.google.android.connecteddevice.util.ByteUtils
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.ByteString
import java.util.UUID
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class LegacyApiProxyTest {

  private val negativeMockCoordinator: IFeatureCoordinator = mock()

  private val versionZeroMockCoordinator: IFeatureCoordinator = mock()

  private val versionOneMockCoordinator: IFeatureCoordinator = mock()

  private val recipientId = ParcelUuid(UUID.randomUUID())

  private val mockConnectorCallback: SafeConnector.Callback = mock()

  private val testLoggerId = 0

  private val mockListener: ISafeOnLogRequestedListener = mock()

  private lateinit var defaultProxyVersionNegative: LegacyApiProxy

  private lateinit var defaultProxyVersion0: LegacyApiProxy

  private lateinit var defaultProxyVersion1: LegacyApiProxy

  @Before
  fun setUp() {
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName1",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(versionZeroMockCoordinator.getConnectedDevicesForDriver())
      .thenReturn(listOf(connectedDevice))
    whenever(versionOneMockCoordinator.getConnectedDevicesForDriver())
      .thenReturn(listOf(connectedDevice))
    defaultProxyVersionNegative =
      LegacyApiProxy(negativeMockCoordinator, recipientId, mockConnectorCallback, testLoggerId, -1)
    defaultProxyVersion0 =
      LegacyApiProxy(
        versionZeroMockCoordinator,
        recipientId,
        mockConnectorCallback,
        testLoggerId,
        0
      )
    defaultProxyVersion1 =
      LegacyApiProxy(versionOneMockCoordinator, recipientId, mockConnectorCallback, testLoggerId, 1)
  }

  @Test
  fun onInit_doesNotInvokeCoordinator_onPlatformVersionLessThanZero() {
    verify(negativeMockCoordinator, never()).registerAllConnectionCallback(any())
    verify(negativeMockCoordinator, never()).registerDeviceCallback(any(), any(), any())
    verify(negativeMockCoordinator, never()).registerOnLogRequestedListener(any(), any())
  }

  @Test
  fun onInit_correctlyInvokesCoordinator_onPlatformVersionZero() {
    verify(versionZeroMockCoordinator).registerAllConnectionCallback(any())
    verify(versionZeroMockCoordinator).registerDeviceCallback(any(), any(), any())
    verify(versionZeroMockCoordinator).registerOnLogRequestedListener(any(), any())
  }

  @Test
  fun onInit_correctlyInvokesCoordinator_onPlatformVersionOne() {
    verify(versionOneMockCoordinator).registerAllConnectionCallback(any())
    verify(versionOneMockCoordinator).registerDeviceCallback(any(), any(), any())
    verify(versionOneMockCoordinator).registerOnLogRequestedListener(any(), any())
  }

  @Test
  fun getConnectedDevices_returnsNullOnVersionLessThanZero() {
    val device1 =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName1",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val device2 =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName2",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(negativeMockCoordinator.getConnectedDevicesForDriver())
      .thenReturn(listOf(device1, device2))
    val devices = defaultProxyVersionNegative.getConnectedDevices()

    assertThat(devices).isNull()
  }

  @Test
  fun getConnectedDevices_returnsListOnVersionZero() {
    val device1 =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName1",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val device2 =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName2",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(versionZeroMockCoordinator.getConnectedDevicesForDriver())
      .thenReturn(listOf(device1, device2))
    val devices = defaultProxyVersion0.getConnectedDevices()

    assertThat(devices).containsExactly(device1.deviceId, device2.deviceId)
  }

  @Test
  fun getConnectedDevices_returnsListOnVersionGreaterThanZero() {
    val device1 =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName1",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val device2 =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName2",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(versionOneMockCoordinator.getConnectedDevicesForDriver())
      .thenReturn(listOf(device1, device2))
    val devices = defaultProxyVersion1.getConnectedDevices()

    assertThat(devices).containsExactly(device1.deviceId, device2.deviceId)
  }

  @Test
  fun sendMessage_returnsFalseOnVersionLessThanZero() {
    val connectedDevice: ConnectedDevice = mock()
    val deviceId = UUID.randomUUID().toString()
    val message = ByteUtils.randomBytes(10)
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        UUID.fromString(deviceId),
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.CLIENT_MESSAGE,
        message
      )

    whenever(connectedDevice.deviceId).thenReturn(deviceId)
    whenever(negativeMockCoordinator.getConnectedDevicesForDriver())
      .thenReturn(listOf(connectedDevice))
    whenever(negativeMockCoordinator.sendMessage(connectedDevice, deviceMessage)).thenReturn(true)
    val messageSent = defaultProxyVersionNegative.sendMessage(deviceId, message)

    assertThat(messageSent).isFalse()
  }

  @Test
  fun sendMessage_sendsMessageToControllerOnVersionZero() {
    val connectedDevice: ConnectedDevice = mock()
    val deviceId = UUID.randomUUID().toString()
    val message = ByteUtils.randomBytes(10)
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        UUID.fromString(deviceId),
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.CLIENT_MESSAGE,
        message
      )

    whenever(connectedDevice.deviceId).thenReturn(deviceId)
    whenever(versionZeroMockCoordinator.getConnectedDevicesForDriver())
      .thenReturn(listOf(connectedDevice))
    whenever(versionZeroMockCoordinator.sendMessage(connectedDevice, deviceMessage))
      .thenReturn(true)
    val messageSent = defaultProxyVersion0.sendMessage(deviceId, message)

    assertThat(messageSent).isTrue()
  }

  @Test
  fun sendMessage_sendsMessageToControllerOnVersionGreaterThanZero() {
    val connectedDevice: ConnectedDevice = mock()
    val deviceId = UUID.randomUUID().toString()
    val message = ByteUtils.randomBytes(10)
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        UUID.fromString(deviceId),
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.CLIENT_MESSAGE,
        message
      )

    whenever(connectedDevice.deviceId).thenReturn(deviceId)
    whenever(versionOneMockCoordinator.getConnectedDevicesForDriver())
      .thenReturn(listOf(connectedDevice))
    whenever(versionOneMockCoordinator.sendMessage(connectedDevice, deviceMessage)).thenReturn(true)
    val messageSent = defaultProxyVersion1.sendMessage(deviceId, message)

    assertThat(messageSent).isTrue()
  }

  @Test
  fun sendMessage_failsToSendMessageUsingInvalidDeviceIdOnVersionZero() {
    val connectedDevice: ConnectedDevice = mock()
    val deviceId1 = "connectedDevice"
    val deviceId2 = "disconnectedDevice"
    val message = ByteUtils.randomBytes(10)
    whenever(connectedDevice.deviceId).thenReturn(deviceId1)
    whenever(versionZeroMockCoordinator.getConnectedDevicesForDriver())
      .thenReturn(listOf(connectedDevice))
    whenever(versionZeroMockCoordinator.sendMessage(any(), any())).thenReturn(true)
    val messageSent = defaultProxyVersion0.sendMessage(deviceId2, message)

    assertThat(messageSent).isFalse()
  }

  @Test
  fun sendMessage_failsToSendMessageUsingInvalidDeviceIdOnVersionGreaterThanZero() {
    val connectedDevice: ConnectedDevice = mock()
    val deviceId1 = "connectedDevice"
    val deviceId2 = "disconnectedDevice"
    val message = ByteUtils.randomBytes(10)
    whenever(connectedDevice.deviceId).thenReturn(deviceId1)
    whenever(versionOneMockCoordinator.getConnectedDevicesForDriver())
      .thenReturn(listOf(connectedDevice))
    whenever(versionOneMockCoordinator.sendMessage(any(), any())).thenReturn(true)
    val messageSent = defaultProxyVersion1.sendMessage(deviceId2, message)

    assertThat(messageSent).isFalse()
  }

  @Test
  fun sendMessage_failsIfCoordinatorThrowsException() {
    val connectedDevice: ConnectedDevice = mock()
    val deviceId = UUID.randomUUID().toString()
    val message = ByteUtils.randomBytes(10)
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        UUID.fromString(deviceId),
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.CLIENT_MESSAGE,
        message
      )
    whenever(connectedDevice.deviceId).thenReturn(deviceId)
    whenever(versionOneMockCoordinator.getConnectedDevicesForDriver())
      .thenReturn(listOf(connectedDevice))
    whenever(versionZeroMockCoordinator.sendMessage(connectedDevice, deviceMessage))
      .thenThrow(RemoteException())
    val messageSent = defaultProxyVersion0.sendMessage(UUID.randomUUID().toString(), message)

    assertThat(messageSent).isFalse()
  }

  @Test
  fun processLogRecords_returnsFalseOnVersionLessThanZero() {
    val testLogs = "test logs".toByteArray()
    val success = defaultProxyVersionNegative.processLogRecords(testLoggerId, testLogs)

    assertThat(success).isFalse()
  }

  @Test
  fun processLogRecords_returnsTrueOnVersionZero() {
    val testLogs = "test logs".toByteArray()
    val success = defaultProxyVersion0.processLogRecords(testLoggerId, testLogs)

    assertThat(success).isTrue()
  }

  @Test
  fun processLogRecords_returnsTrueOnVersionGreaterThanZero() {
    val testLogs = "test logs".toByteArray()
    val success = defaultProxyVersion1.processLogRecords(testLoggerId, testLogs)

    assertThat(success).isTrue()
  }

  @Test
  fun retrieveAssociatedDevices_returnsFalseOnVersionLessThanZero() {
    val mockListener: IOnAssociatedDevicesRetrievedListener = mockToBeAlive()
    val success = defaultProxyVersionNegative.retrieveAssociatedDevices(mockListener)

    assertThat(success).isFalse()
  }

  @Test
  fun retrieveAssociatedDevices_returnsTrueOnVersionZero() {
    val mockListener: IOnAssociatedDevicesRetrievedListener = mockToBeAlive()
    val success = defaultProxyVersion0.retrieveAssociatedDevices(mockListener)

    assertThat(success).isTrue()
  }

  @Test
  fun retrieveAssociatedDevices_returnsTrueOnVersionGreaterThanZero() {
    val mockListener: IOnAssociatedDevicesRetrievedListener = mockToBeAlive()
    val success = defaultProxyVersion1.retrieveAssociatedDevices(mockListener)

    assertThat(success).isTrue()
  }

  @Test
  fun retrieveAssociatedDevices_returnsFalseIfIncorrectParam() {
    val mockListener: ISafeOnAssociatedDevicesRetrievedListener = mockToBeAlive()
    val success = defaultProxyVersion0.retrieveAssociatedDevices(mockListener)

    assertThat(success).isFalse()
  }

  @Test
  fun processIncomingMessage_worksWithClientMessage() {
    val deviceId = UUID.randomUUID().toString()
    val mockConnectedDevice: ConnectedDevice = mock()
    whenever(mockConnectedDevice.deviceId).thenReturn(deviceId)
    val payload = ByteString.copyFrom(ByteUtils.randomBytes(10)).toByteArray()
    val message =
      DeviceMessage.createOutgoingMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.CLIENT_MESSAGE,
        payload
      )
    defaultProxyVersion0.deviceCallback.onMessageReceived(mockConnectedDevice, message)

    verify(mockConnectorCallback).onMessageReceived(deviceId, payload)
  }

  @Test
  fun processIncomingMessage_worksWithQuery() {
    val deviceId = UUID.randomUUID().toString()
    val mockConnectedDevice: ConnectedDevice = mock()
    whenever(mockConnectedDevice.deviceId).thenReturn(deviceId)
    val payload =
      Query.newBuilder()
        .setId(1)
        .setSender(ByteString.copyFrom(ByteUtils.uuidToBytes(recipientId.uuid)))
        .setRequest(ByteString.copyFrom(ByteUtils.randomBytes(10)))
        .setParameters(ByteString.copyFrom(ByteUtils.randomBytes(10)))
        .build()
        .toByteArray()
    val message =
      DeviceMessage.createOutgoingMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.QUERY,
        payload
      )
    defaultProxyVersion0.deviceCallback.onMessageReceived(mockConnectedDevice, message)

    verify(mockConnectorCallback).onQueryReceived(eq(deviceId), any(), any(), any())
  }

  @Test
  fun processIncomingMessage_worksWithQueryResponse() {
    val deviceId = UUID.randomUUID().toString()
    val mockConnectedDevice: ConnectedDevice = mock()
    whenever(mockConnectedDevice.deviceId).thenReturn(deviceId)
    val queryId = 1
    val mockQueryCallback: QueryCallback = mock()
    defaultProxyVersion0.queryCallbacks[queryId] = mockQueryCallback
    val payload =
      QueryResponse.newBuilder().setQueryId(queryId).setSuccess(true).build().toByteArray()
    val message =
      DeviceMessage.createOutgoingMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.QUERY_RESPONSE,
        payload
      )
    defaultProxyVersion0.deviceCallback.onMessageReceived(mockConnectedDevice, message)

    assertThat(defaultProxyVersion0.queryCallbacks.contains(queryId)).isFalse()
    verify(mockQueryCallback).onSuccess(any())
  }

  @Test
  fun onCleanUp_doesNotInvokeCoordinator_onPlatformVersionLessThanZero() {
    defaultProxyVersionNegative.cleanUp()

    verify(negativeMockCoordinator, never()).unregisterConnectionCallback(any())
    verify(negativeMockCoordinator, never()).unregisterDeviceCallback(any(), any(), any())
    verify(negativeMockCoordinator, never()).unregisterOnLogRequestedListener(any(), any())
  }

  @Test
  fun onCleanUp_correctlyInvokesCoordinator_onPlatformVersionZero() {
    defaultProxyVersion0.cleanUp()

    verify(versionZeroMockCoordinator).unregisterConnectionCallback(any())
    verify(versionZeroMockCoordinator).unregisterDeviceCallback(any(), any(), any())
    verify(versionZeroMockCoordinator).unregisterOnLogRequestedListener(any(), any())
  }

  @Test
  fun onCleanUp_correctlyInvokesCoordinator_onPlatformVersionGreaterThanZero() {
    defaultProxyVersion1.cleanUp()

    verify(versionOneMockCoordinator).unregisterConnectionCallback(any())
    verify(versionOneMockCoordinator).unregisterDeviceCallback(any(), any(), any())
    verify(versionOneMockCoordinator).unregisterOnLogRequestedListener(any(), any())
  }
}
