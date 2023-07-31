package com.google.android.connecteddevice.api

import android.os.ParcelUuid
import android.os.RemoteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.companionprotos.DeviceMessageProto
import com.google.android.companionprotos.OperationProto.OperationType
import com.google.android.companionprotos.Query
import com.google.android.companionprotos.QueryResponse
import com.google.android.connecteddevice.api.SafeConnector.QueryCallback
import com.google.android.connecteddevice.api.external.ISafeFeatureCoordinator
import com.google.android.connecteddevice.api.external.ISafeOnAssociatedDevicesRetrievedListener
import com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener
import com.google.android.connecteddevice.core.util.mockToBeAlive
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
class SafeApiProxyTest {

  private val versionZeroMockCoordinator: ISafeFeatureCoordinator = mock()

  private val versionOneMockCoordinator: ISafeFeatureCoordinator = mock()

  private val versionTwoMockCoordinator: ISafeFeatureCoordinator = mock()

  private val recipientId = ParcelUuid(UUID.randomUUID())

  private val mockConnectorCallback: SafeConnector.Callback = mock()

  private val testLoggerId = 0

  private val mockListener: ISafeOnLogRequestedListener = mock()

  private lateinit var defaultProxyVersion0: SafeApiProxy

  private lateinit var defaultProxyVersion1: SafeApiProxy

  private lateinit var defaultProxyVersion2: SafeApiProxy

  @Before
  fun setUp() {
    val device = "driverDeviceName1"
    whenever(versionOneMockCoordinator.getConnectedDevices()).thenReturn(listOf(device))
    whenever(versionTwoMockCoordinator.getConnectedDevices()).thenReturn(listOf(device))
    defaultProxyVersion0 =
      SafeApiProxy(versionZeroMockCoordinator, recipientId, mockConnectorCallback, testLoggerId, 0)
    defaultProxyVersion1 =
      SafeApiProxy(versionOneMockCoordinator, recipientId, mockConnectorCallback, testLoggerId, 1)
    defaultProxyVersion2 =
      SafeApiProxy(versionTwoMockCoordinator, recipientId, mockConnectorCallback, testLoggerId, 2)
  }

  @Test
  fun onInit_doesNotInvokeCoordinator_onPlatformVersionZero() {
    verify(versionZeroMockCoordinator, never()).registerConnectionCallback(any())
    verify(versionZeroMockCoordinator, never()).registerDeviceCallback(any(), any(), any())
    verify(versionZeroMockCoordinator, never()).registerOnLogRequestedListener(any(), any())
  }

  @Test
  fun onInit_correctlyInvokesCoordinator_onPlatformVersionOne() {
    verify(versionOneMockCoordinator).registerConnectionCallback(any())
    verify(versionOneMockCoordinator).registerDeviceCallback(any(), any(), any())
    verify(versionOneMockCoordinator).registerOnLogRequestedListener(any(), any())
  }

  @Test
  fun onInit_correctlyInvokesCoordinator_onPlatformVersionTwo() {
    verify(versionTwoMockCoordinator).registerConnectionCallback(any())
    verify(versionTwoMockCoordinator).registerDeviceCallback(any(), any(), any())
    verify(versionTwoMockCoordinator).registerOnLogRequestedListener(any(), any())
  }

  @Test
  fun getConnectedDevices_returnsNullOnVersionZero() {
    whenever(versionZeroMockCoordinator.getConnectedDevices())
      .thenReturn(listOf("driverDeviceName1", "driverDeviceName2"))
    val devices = defaultProxyVersion0.getConnectedDevices()

    assertThat(devices).isNull()
  }

  @Test
  fun getConnectedDevices_returnsListOnVersionOne() {
    whenever(versionOneMockCoordinator.getConnectedDevices())
      .thenReturn(listOf("driverDeviceName1", "driverDeviceName2"))
    val devices = defaultProxyVersion1.getConnectedDevices()

    assertThat(devices).containsExactly("driverDeviceName1", "driverDeviceName2")
  }

  @Test
  fun getConnectedDevices_returnsListOnVersionTwo() {
    whenever(versionTwoMockCoordinator.getConnectedDevices())
      .thenReturn(listOf("driverDeviceName1", "driverDeviceName2"))
    val devices = defaultProxyVersion2.getConnectedDevices()

    assertThat(devices).containsExactly("driverDeviceName1", "driverDeviceName2")
  }

  @Test
  fun sendMessage_returnsFalseOnVersionZero() {
    val message =
      DeviceMessageProto.Message.newBuilder()
        .setRecipient(ByteString.copyFrom(ByteUtils.uuidToBytes(UUID.randomUUID())))
        .setIsPayloadEncrypted(true)
        .setOperation(
          OperationType.forNumber(/* CLIENT_MESSAGE */ 4) ?: OperationType.OPERATION_TYPE_UNKNOWN
        )
        .setPayload(ByteString.copyFrom(ByteUtils.randomBytes(10)))
        .build()
    val rawBytes = message.toByteArray()
    whenever(versionZeroMockCoordinator.sendMessage(any(), any())).thenReturn(true)
    val messageSent = defaultProxyVersion0.sendMessage(UUID.randomUUID().toString(), rawBytes)

    assertThat(messageSent).isFalse()
  }

  @Test
  fun sendMessage_sendsMessageToControllerOnVersionOne() {
    val message =
      DeviceMessageProto.Message.newBuilder()
        .setRecipient(ByteString.copyFrom(ByteUtils.uuidToBytes(UUID.randomUUID())))
        .setIsPayloadEncrypted(true)
        .setOperation(
          OperationType.forNumber(/* CLIENT_MESSAGE */ 4) ?: OperationType.OPERATION_TYPE_UNKNOWN
        )
        .setPayload(ByteString.copyFrom(ByteUtils.randomBytes(10)))
        .build()
    val rawBytes = message.toByteArray()
    whenever(versionOneMockCoordinator.sendMessage(any(), any())).thenReturn(true)
    val messageSent = defaultProxyVersion1.sendMessage(UUID.randomUUID().toString(), rawBytes)

    assertThat(messageSent).isTrue()
  }

  @Test
  fun sendMessage_sendsMessageToControllerOnVersionTwo() {
    val message =
      DeviceMessageProto.Message.newBuilder()
        .setRecipient(ByteString.copyFrom(ByteUtils.uuidToBytes(UUID.randomUUID())))
        .setIsPayloadEncrypted(true)
        .setOperation(
          OperationType.forNumber(/* CLIENT_MESSAGE */ 4) ?: OperationType.OPERATION_TYPE_UNKNOWN
        )
        .setPayload(ByteString.copyFrom(ByteUtils.randomBytes(10)))
        .build()
    val rawBytes = message.toByteArray()
    whenever(versionTwoMockCoordinator.sendMessage(any(), any())).thenReturn(true)
    val messageSent = defaultProxyVersion2.sendMessage(UUID.randomUUID().toString(), rawBytes)

    assertThat(messageSent).isTrue()
  }

  @Test
  fun sendMessage_failsIfCoordinatorThrowsException() {
    val message =
      DeviceMessageProto.Message.newBuilder()
        .setRecipient(ByteString.copyFrom(ByteUtils.uuidToBytes(UUID.randomUUID())))
        .setIsPayloadEncrypted(true)
        .setOperation(
          OperationType.forNumber(/* CLIENT_MESSAGE */ 4) ?: OperationType.OPERATION_TYPE_UNKNOWN
        )
        .setPayload(ByteString.copyFrom(ByteUtils.randomBytes(10)))
        .build()
    val rawBytes = message.toByteArray()
    whenever(versionOneMockCoordinator.sendMessage(any(), any())).thenThrow(RemoteException())
    val messageSent = defaultProxyVersion1.sendMessage(UUID.randomUUID().toString(), rawBytes)

    assertThat(messageSent).isFalse()
  }

  @Test
  fun processLogRecords_returnsFalseOnVersionZero() {
    val testLogs = "test logs".toByteArray()
    val success = defaultProxyVersion0.processLogRecords(testLoggerId, testLogs)

    assertThat(success).isFalse()
  }

  @Test
  fun processLogRecords_returnsTrueOnVersionOne() {
    val testLogs = "test logs".toByteArray()
    val success = defaultProxyVersion1.processLogRecords(testLoggerId, testLogs)

    assertThat(success).isTrue()
  }

  @Test
  fun processLogRecords_returnsTrueOnVersionTwo() {
    val testLogs = "test logs".toByteArray()
    val success = defaultProxyVersion2.processLogRecords(testLoggerId, testLogs)

    assertThat(success).isTrue()
  }

  @Test
  fun retrieveAssociatedDevices_returnsFalseOnVersionZero() {
    val mockListener: ISafeOnAssociatedDevicesRetrievedListener = mockToBeAlive()
    val success = defaultProxyVersion0.retrieveAssociatedDevices(mockListener)

    assertThat(success).isFalse()
  }

  @Test
  fun retrieveAssociatedDevices_returnsTrueOnVersionOne() {
    val mockListener: ISafeOnAssociatedDevicesRetrievedListener = mockToBeAlive()
    val success = defaultProxyVersion1.retrieveAssociatedDevices(mockListener)

    assertThat(success).isTrue()
  }

  @Test
  fun retrieveAssociatedDevices_returnsTrueOnVersionTwo() {
    val mockListener: ISafeOnAssociatedDevicesRetrievedListener = mockToBeAlive()
    val success = defaultProxyVersion2.retrieveAssociatedDevices(mockListener)

    assertThat(success).isTrue()
  }

  @Test
  fun retrieveAssociatedDevices_returnsFalseIfIncorrectParam() {
    val mockListener: IOnAssociatedDevicesRetrievedListener = mockToBeAlive()
    val success = defaultProxyVersion1.retrieveAssociatedDevices(mockListener)

    assertThat(success).isFalse()
  }

  @Test
  fun processIncomingMessage_worksWithClientMessage() {
    val deviceId = UUID.randomUUID().toString()
    val payload = ByteString.copyFrom(ByteUtils.randomBytes(10))
    val message =
      DeviceMessageProto.Message.newBuilder()
        .setRecipient(ByteString.copyFrom(ByteUtils.uuidToBytes(UUID.randomUUID())))
        .setIsPayloadEncrypted(true)
        .setOperation(
          OperationType.forNumber(/* CLIENT_MESSAGE */ 4) ?: OperationType.OPERATION_TYPE_UNKNOWN
        )
        .setPayload(payload)
        .build()
    val rawBytes = message.toByteArray()
    defaultProxyVersion0.deviceCallback.onMessageReceived(deviceId, rawBytes)

    verify(mockConnectorCallback).onMessageReceived(deviceId, payload.toByteArray())
  }

  @Test
  fun processIncomingMessage_worksWithQuery() {
    val deviceId = UUID.randomUUID().toString()
    val payload =
      Query.newBuilder()
        .setId(1)
        .setSender(ByteString.copyFrom(ByteUtils.uuidToBytes(recipientId.uuid)))
        .setRequest(ByteString.copyFrom(ByteUtils.randomBytes(10)))
        .setParameters(ByteString.copyFrom(ByteUtils.randomBytes(10)))
        .build()
        .toByteString()
    val message =
      DeviceMessageProto.Message.newBuilder()
        .setRecipient(ByteString.copyFrom(ByteUtils.uuidToBytes(UUID.randomUUID())))
        .setIsPayloadEncrypted(true)
        .setOperation(
          OperationType.forNumber(/* QUERY */ 5) ?: OperationType.OPERATION_TYPE_UNKNOWN
        )
        .setPayload(payload)
        .build()
    val rawBytes = message.toByteArray()
    defaultProxyVersion0.deviceCallback.onMessageReceived(deviceId, rawBytes)

    verify(mockConnectorCallback).onQueryReceived(eq(deviceId), any(), any(), any())
  }

  @Test
  fun processIncomingMessage_worksWithQueryResponse() {
    val deviceId = UUID.randomUUID().toString()
    val queryId = 1
    val mockQueryCallback: QueryCallback = mock()
    defaultProxyVersion0.queryCallbacks[queryId] = mockQueryCallback
    val payload =
      QueryResponse.newBuilder().setQueryId(queryId).setSuccess(true).build().toByteString()
    val message =
      DeviceMessageProto.Message.newBuilder()
        .setRecipient(ByteString.copyFrom(ByteUtils.uuidToBytes(UUID.randomUUID())))
        .setIsPayloadEncrypted(true)
        .setOperation(
          OperationType.forNumber(/* QUERY_RESPONSE */ 6) ?: OperationType.OPERATION_TYPE_UNKNOWN
        )
        .setPayload(payload)
        .build()
    val rawBytes = message.toByteArray()
    defaultProxyVersion0.deviceCallback.onMessageReceived(deviceId, rawBytes)

    assertThat(defaultProxyVersion0.queryCallbacks.contains(queryId)).isFalse()
    verify(mockQueryCallback).onSuccess(any())
  }

  @Test
  fun onCleanUp_doesNotInvokeCoordinator_onPlatformVersionZero() {
    defaultProxyVersion0.cleanUp()

    verify(versionZeroMockCoordinator, never()).unregisterConnectionCallback(any())
    verify(versionZeroMockCoordinator, never()).unregisterDeviceCallback(any(), any(), any())
    verify(versionZeroMockCoordinator, never()).unregisterOnLogRequestedListener(any(), any())
  }

  @Test
  fun onCleanUp_correctlyInvokesCoordinator_onPlatformVersionOne() {
    defaultProxyVersion1.cleanUp()

    verify(versionOneMockCoordinator).unregisterConnectionCallback(any())
    verify(versionOneMockCoordinator).unregisterDeviceCallback(any(), any(), any())
    verify(versionOneMockCoordinator).unregisterOnLogRequestedListener(any(), any())
  }

  @Test
  fun onCleanUp_correctlyInvokesCoordinator_onPlatformVersionTwo() {
    defaultProxyVersion2.cleanUp()

    verify(versionTwoMockCoordinator).unregisterConnectionCallback(any())
    verify(versionTwoMockCoordinator).unregisterDeviceCallback(any(), any(), any())
    verify(versionTwoMockCoordinator).unregisterOnLogRequestedListener(any(), any())
  }
}
