package com.google.android.connecteddevice.core

import android.os.ParcelUuid
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.companionprotos.DeviceMessageProto
import com.google.android.companionprotos.OperationProto.OperationType
import com.google.android.connecteddevice.api.IAssociationCallback
import com.google.android.connecteddevice.api.IConnectionCallback
import com.google.android.connecteddevice.api.IDeviceAssociationCallback
import com.google.android.connecteddevice.api.IDeviceCallback
import com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener
import com.google.android.connecteddevice.api.external.ISafeConnectionCallback
import com.google.android.connecteddevice.api.external.ISafeDeviceCallback
import com.google.android.connecteddevice.api.external.ISafeOnAssociatedDevicesRetrievedListener
import com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener
import com.google.android.connecteddevice.core.FeatureCoordinator.Companion.DEVICE_NAME_LENGTH
import com.google.android.connecteddevice.core.util.mockToBeAlive
import com.google.android.connecteddevice.core.util.mockToBeDead
import com.google.android.connecteddevice.logging.LoggingManager
import com.google.android.connecteddevice.model.AssociatedDevice
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.model.DeviceMessage.OperationType.CLIENT_MESSAGE
import com.google.android.connecteddevice.model.Errors.DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.util.ByteUtils
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.google.protobuf.ByteString
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeatureCoordinatorTest {
  private val mockController: DeviceController = mock()

  private val mockStorage: ConnectedDeviceStorage = mock()

  private val mockLoggingManager: LoggingManager = mock()

  private val coordinator =
    FeatureCoordinator(mockController, mockStorage, mockLoggingManager, directExecutor())

  private val safeCoordinator = coordinator.safeFeatureCoordinator

  @Test
  fun connectedDevicesForDriver_returnsOnlyDriverDevices() {
    val driverDevice1 =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName1",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val driverDevice2 =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName2",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val passengerDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "passengerDeviceName",
        /* belongsToDriver= */ false,
        /* hasSecureChannel= */ true
      )
    whenever(mockController.connectedDevices)
      .thenReturn(listOf(driverDevice1, passengerDevice, driverDevice2))

    val driverDevices = coordinator.connectedDevicesForDriver

    assertThat(driverDevices).containsExactly(driverDevice1, driverDevice2)
  }

  @Test
  fun connectedDevicesForPassengers_returnsOnlyPassengerDevices() {
    val passengerDevice1 =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "passengerDevice1",
        /* belongsToDriver= */ false,
        /* hasSecureChannel= */ true
      )
    val passengerDevice2 =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "passengerDevice2",
        /* belongsToDriver= */ false,
        /* hasSecureChannel= */ true
      )
    val driverDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDevice",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    whenever(mockController.connectedDevices)
      .thenReturn(listOf(passengerDevice1, driverDevice, passengerDevice2))

    val driverDevices = coordinator.connectedDevicesForPassengers

    assertThat(driverDevices).containsExactly(passengerDevice1, passengerDevice2)
  }

  @Test
  fun allConnectedDevices_returnsAllDevices() {
    val driverDevice1 =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName1",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val driverDevice2 =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName2",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val passengerDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "passengerDeviceName",
        /* belongsToDriver= */ false,
        /* hasSecureChannel= */ true
      )
    whenever(mockController.connectedDevices)
      .thenReturn(listOf(driverDevice1, passengerDevice, driverDevice2))

    val driverDevices = coordinator.allConnectedDevices

    assertThat(driverDevices).containsExactly(driverDevice1, passengerDevice, driverDevice2)
  }

  @Test
  fun onDeviceConnected_driverCallbacksInvokedWhenDriverDeviceConnects() {
    val mockConnectionCallback: IConnectionCallback = mockToBeAlive()
    coordinator.registerDriverConnectionCallback(mockConnectionCallback)
    val driverDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )

    coordinator.onDeviceConnectedInternal(driverDevice)

    verify(mockConnectionCallback).onDeviceConnected(driverDevice)
  }

  @Test
  fun onDeviceConnected_driverCallbacksNotInvokedWhenPassengerDeviceConnects() {
    val mockConnectionCallback: IConnectionCallback = mockToBeAlive()

    coordinator.registerDriverConnectionCallback(mockConnectionCallback)
    val passengerDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName",
        /* belongsToDriver= */ false,
        /* hasSecureChannel= */ true
      )

    coordinator.onDeviceConnectedInternal(passengerDevice)

    verify(mockConnectionCallback, never()).onDeviceConnected(passengerDevice)
  }

  @Test
  fun onDeviceConnected_passengerCallbacksNotInvokedWhenDriverDeviceConnects() {
    val mockConnectionCallback: IConnectionCallback = mockToBeAlive()
    coordinator.registerPassengerConnectionCallback(mockConnectionCallback)
    val driverDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )

    coordinator.onDeviceConnectedInternal(driverDevice)

    verify(mockConnectionCallback, never()).onDeviceConnected(driverDevice)
  }

  @Test
  fun onDeviceConnected_passengerCallbacksInvokedWhenPassengerDeviceConnects() {
    val mockConnectionCallback: IConnectionCallback = mockToBeAlive()
    coordinator.registerPassengerConnectionCallback(mockConnectionCallback)
    val passengerDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName",
        /* belongsToDriver= */ false,
        /* hasSecureChannel= */ true
      )

    coordinator.onDeviceConnectedInternal(passengerDevice)

    verify(mockConnectionCallback).onDeviceConnected(passengerDevice)
  }

  @Test
  fun onDeviceConnected_allCallbacksInvokedWhenDriverDeviceConnects() {
    val mockConnectionCallback: IConnectionCallback = mockToBeAlive()
    coordinator.registerAllConnectionCallback(mockConnectionCallback)
    val driverDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )

    coordinator.onDeviceConnectedInternal(driverDevice)

    verify(mockConnectionCallback).onDeviceConnected(driverDevice)
  }

  @Test
  fun onDeviceConnected_allCallbacksInvokedWhenPassengerDeviceConnects() {
    val mockConnectionCallback: IConnectionCallback = mockToBeAlive()
    coordinator.registerAllConnectionCallback(mockConnectionCallback)
    val passengerDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName",
        /* belongsToDriver= */ false,
        /* hasSecureChannel= */ true
      )

    coordinator.onDeviceConnectedInternal(passengerDevice)

    verify(mockConnectionCallback).onDeviceConnected(passengerDevice)
  }

  @Test
  fun onDeviceDisconnected_driverCallbacksInvokedWhenDriverDeviceDisconnects() {
    val mockConnectionCallback: IConnectionCallback = mockToBeAlive()
    coordinator.registerDriverConnectionCallback(mockConnectionCallback)
    val driverDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )

    coordinator.onDeviceDisconnectedInternal(driverDevice)

    verify(mockConnectionCallback).onDeviceDisconnected(driverDevice)
  }

  @Test
  fun onDeviceDisconnected_driverCallbacksNotInvokedWhenPassengerDeviceDisconnects() {
    val mockConnectionCallback: IConnectionCallback = mockToBeAlive()
    coordinator.registerDriverConnectionCallback(mockConnectionCallback)
    val passengerDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName",
        /* belongsToDriver= */ false,
        /* hasSecureChannel= */ true
      )

    coordinator.onDeviceDisconnectedInternal(passengerDevice)

    verify(mockConnectionCallback, never()).onDeviceDisconnected(passengerDevice)
  }

  @Test
  fun onDeviceDisconnected_passengerCallbacksNotInvokedWhenDriverDeviceDisconnects() {
    val mockConnectionCallback: IConnectionCallback = mockToBeAlive()
    coordinator.registerPassengerConnectionCallback(mockConnectionCallback)
    val driverDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )

    coordinator.onDeviceDisconnectedInternal(driverDevice)

    verify(mockConnectionCallback, never()).onDeviceDisconnected(driverDevice)
  }

  @Test
  fun onDeviceDisconnected_passengerCallbacksInvokedWhenPassengerDeviceDisconnects() {
    val mockConnectionCallback: IConnectionCallback = mockToBeAlive()
    coordinator.registerPassengerConnectionCallback(mockConnectionCallback)
    val passengerDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName",
        /* belongsToDriver= */ false,
        /* hasSecureChannel= */ true
      )

    coordinator.onDeviceDisconnectedInternal(passengerDevice)

    verify(mockConnectionCallback).onDeviceDisconnected(passengerDevice)
  }

  @Test
  fun onDeviceDisconnected_allCallbacksInvokedWhenDriverDeviceDisconnects() {
    val mockConnectionCallback: IConnectionCallback = mockToBeAlive()
    coordinator.registerAllConnectionCallback(mockConnectionCallback)
    val driverDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )

    coordinator.onDeviceDisconnectedInternal(driverDevice)

    verify(mockConnectionCallback).onDeviceDisconnected(driverDevice)
  }

  @Test
  fun onDeviceDisconnected_allCallbacksInvokedWhenPassengerDeviceDisconnects() {
    val mockConnectionCallback: IConnectionCallback = mockToBeAlive()
    coordinator.registerAllConnectionCallback(mockConnectionCallback)
    val passengerDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName",
        /* belongsToDriver= */ false,
        /* hasSecureChannel= */ true
      )

    coordinator.onDeviceDisconnectedInternal(passengerDevice)

    verify(mockConnectionCallback).onDeviceDisconnected(passengerDevice)
  }

  @Test
  fun unregisterConnectionCallback_driverCallbackNotInvokedWhenDeviceConnects() {
    val mockConnectionCallback: IConnectionCallback = mockToBeAlive()
    coordinator.registerDriverConnectionCallback(mockConnectionCallback)
    val driverDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )

    coordinator.unregisterConnectionCallback(mockConnectionCallback)
    coordinator.onDeviceConnectedInternal(driverDevice)

    verify(mockConnectionCallback, never()).onDeviceConnected(driverDevice)
  }

  @Test
  fun unregisterConnectionCallback_passengerCallbackNotInvokedWhenDeviceConnects() {
    val mockConnectionCallback: IConnectionCallback = mockToBeAlive()
    coordinator.registerPassengerConnectionCallback(mockConnectionCallback)
    val passengerDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName",
        /* belongsToDriver= */ false,
        /* hasSecureChannel= */ true
      )

    coordinator.unregisterConnectionCallback(mockConnectionCallback)
    coordinator.onDeviceConnectedInternal(passengerDevice)

    verify(mockConnectionCallback, never()).onDeviceConnected(passengerDevice)
  }

  @Test
  fun unregisterConnectionCallback_allCallbackNotInvokedWhenDeviceConnects() {
    val mockConnectionCallback: IConnectionCallback = mockToBeAlive()
    coordinator.registerAllConnectionCallback(mockConnectionCallback)
    val driverDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )

    coordinator.unregisterConnectionCallback(mockConnectionCallback)
    coordinator.onDeviceConnectedInternal(driverDevice)

    verify(mockConnectionCallback, never()).onDeviceConnected(driverDevice)
  }

  @Test
  fun registerDeviceCallback_sendsMissedMessages() {
    val deviceCallback: IDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val missedMessage =
      DeviceMessage.createOutgoingMessage(
        recipientId.uuid,
        /* isMessageEncrypted= */ true,
        CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )

    coordinator.onMessageReceivedInternal(connectedDevice, missedMessage)
    coordinator.registerDeviceCallback(connectedDevice, recipientId, deviceCallback)

    verify(deviceCallback).onMessageReceived(connectedDevice, missedMessage)
  }

  @Test
  fun registerDeviceCallback_blocksRecipientAndAlivePreviousRegistererIfIdAlreadyRegistered() {
    val deviceCallback: IDeviceCallback = mockToBeAlive()
    val duplicateDeviceCallback: IDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )

    coordinator.registerDeviceCallback(connectedDevice, recipientId, deviceCallback)
    coordinator.registerDeviceCallback(connectedDevice, recipientId, duplicateDeviceCallback)

    verify(deviceCallback)
      .onDeviceError(connectedDevice, DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED)
    verify(duplicateDeviceCallback)
      .onDeviceError(connectedDevice, DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED)
  }

  @Test
  fun registerDeviceCallback_ignoreDeadPreviousRegistererIfIdAlreadyRegistered() {
    val deadDeviceCallback: IDeviceCallback = mockToBeDead()
    val duplicateDeviceCallback: IDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )

    coordinator.registerDeviceCallback(connectedDevice, recipientId, deadDeviceCallback)
    coordinator.registerDeviceCallback(connectedDevice, recipientId, duplicateDeviceCallback)

    verify(duplicateDeviceCallback, never())
      .onDeviceError(connectedDevice, DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED)
  }

  @Test
  fun registerDeviceCallback_alreadyBlockedRecipientNotifiedOnRegistration() {
    val deviceCallback: IDeviceCallback = mockToBeAlive()
    val duplicateDeviceCallback: IDeviceCallback = mockToBeAlive()
    val secondDuplicateDeviceCallback: IDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    coordinator.registerDeviceCallback(connectedDevice, recipientId, deviceCallback)
    coordinator.registerDeviceCallback(connectedDevice, recipientId, duplicateDeviceCallback)

    coordinator.registerDeviceCallback(connectedDevice, recipientId, secondDuplicateDeviceCallback)

    verify(secondDuplicateDeviceCallback)
      .onDeviceError(connectedDevice, DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED)
  }

  @Test
  fun unregisterDeviceCallback_callbackNotInvokedAfterUnregistering() {
    val deviceCallback: IDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val message =
      DeviceMessage.createOutgoingMessage(
        recipientId.uuid,
        /* isMessageEncrypted= */ true,
        CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )

    coordinator.registerDeviceCallback(connectedDevice, recipientId, deviceCallback)
    coordinator.unregisterDeviceCallback(connectedDevice, recipientId, deviceCallback)
    coordinator.onMessageReceivedInternal(connectedDevice, message)

    verify(deviceCallback, never()).onMessageReceived(any(), any())
  }

  @Test
  fun onMessageReceived_callbackInvokedWhenMessageReceivedForRecipient() {
    val deviceCallback: IDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val message =
      DeviceMessage.createOutgoingMessage(
        recipientId.uuid,
        /* isMessageEncrypted= */ true,
        CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )

    coordinator.registerDeviceCallback(connectedDevice, recipientId, deviceCallback)
    coordinator.onMessageReceivedInternal(connectedDevice, message)

    verify(deviceCallback).onMessageReceived(connectedDevice, message)
  }

  @Test
  fun onMessageReceived_callbackNotInvokedWhenMessageReceivedForDifferentRecipient() {
    val deviceCallback: IDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val otherRecipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val message =
      DeviceMessage.createOutgoingMessage(
        otherRecipientId.uuid,
        /* isMessageEncrypted= */ true,
        CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )

    coordinator.registerDeviceCallback(connectedDevice, recipientId, deviceCallback)
    coordinator.onMessageReceivedInternal(connectedDevice, message)

    verify(deviceCallback, never()).onMessageReceived(any(), any())
  }

  @Test
  fun onMessageReceived_blockedRecipientCallbacksNotInvoked() {
    val deviceCallback: IDeviceCallback = mockToBeAlive()
    val duplicateDeviceCallback: IDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val message =
      DeviceMessage.createOutgoingMessage(
        recipientId.uuid,
        /* isMessageEncrypted= */ true,
        CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )
    coordinator.registerDeviceCallback(connectedDevice, recipientId, deviceCallback)
    coordinator.registerDeviceCallback(connectedDevice, recipientId, duplicateDeviceCallback)

    coordinator.onMessageReceivedInternal(connectedDevice, message)

    verify(deviceCallback, never()).onMessageReceived(any(), any())
    verify(duplicateDeviceCallback, never()).onMessageReceived(any(), any())
  }

  @Test
  fun onMessageReceived_blockedRecipientRecoverFromResetCallbacksInvoked() {
    val deviceCallback: IDeviceCallback = mockToBeAlive()
    val duplicateDeviceCallback: IDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    coordinator.registerDeviceCallback(connectedDevice, recipientId, deviceCallback)
    coordinator.registerDeviceCallback(connectedDevice, recipientId, duplicateDeviceCallback)

    /* Clear the blocked recipient list. */
    coordinator.reset()
    coordinator.registerDeviceCallback(connectedDevice, recipientId, deviceCallback)
    val message =
      DeviceMessage.createOutgoingMessage(
        recipientId.uuid,
        /* isMessageEncrypted= */ true,
        CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )
    coordinator.onMessageReceivedInternal(connectedDevice, message)

    verify(deviceCallback).onMessageReceived(any(), any())
    verify(duplicateDeviceCallback, never()).onMessageReceived(any(), any())
  }

  @Test
  fun onMessageReceived_nullRecipientDoesNotThrow() {
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val nullRecipientMessage =
      DeviceMessage.createOutgoingMessage(
        /* recipient= */ null,
        /* isMessageEncrypted= */ true,
        CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )
    coordinator.onMessageReceivedInternal(connectedDevice, nullRecipientMessage)
  }

  @Test
  fun sendMessage_sendsMessageToController() {
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val message =
      DeviceMessage.createOutgoingMessage(
        UUID.randomUUID(),
        /* isMessageEncrypted= */ true,
        CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )

    coordinator.sendMessage(connectedDevice, message)

    verify(mockController).sendMessage(UUID.fromString(connectedDevice.deviceId), message)
  }

  @Test
  fun startAssociation_startsAssociationWithCorrectlySizedName() {
    val associationCallback: IAssociationCallback = mockToBeAlive()

    coordinator.startAssociation(associationCallback)

    argumentCaptor<String>().apply {
      verify(mockController).startAssociation(capture(), eq(associationCallback), isNull())
      assertThat(firstValue).hasLength(DEVICE_NAME_LENGTH + 2) // Starts with 0x so add 2
    }
  }

  @Test
  fun startAssociationWithIdentifier_startsAssociationWithCorrectlySizedName() {
    val associationCallback: IAssociationCallback = mockToBeAlive()
    val identifier = ParcelUuid(UUID.randomUUID())

    coordinator.startAssociationWithIdentifier(associationCallback, identifier)

    argumentCaptor<String>().apply {
      verify(mockController)
        .startAssociation(capture(), eq(associationCallback), eq(identifier.uuid))
      assertThat(firstValue).hasLength(DEVICE_NAME_LENGTH + 2) // Starts with 0x so add 2
    }
  }

  @Test
  fun onSecureChannelEstablished_invokesAllRegisteredDeviceCallbacks() {
    val deviceCallback: IDeviceCallback = mockToBeAlive()
    val secondDeviceCallback: IDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val secondRecipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    coordinator.registerDeviceCallback(connectedDevice, recipientId, deviceCallback)
    coordinator.registerDeviceCallback(connectedDevice, secondRecipientId, secondDeviceCallback)

    coordinator.onSecureChannelEstablishedInternal(connectedDevice)

    verify(deviceCallback).onSecureChannelEstablished(connectedDevice)
    verify(secondDeviceCallback).onSecureChannelEstablished(connectedDevice)
  }

  @Test
  fun onSecureChannelEstablished_doesNotInvokeCallbacksRegisteredToDifferentDevice() {
    val deviceCallback: IDeviceCallback = mockToBeAlive()
    val otherDeviceCallback: IDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val otherRecipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val otherConnectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    coordinator.registerDeviceCallback(connectedDevice, recipientId, deviceCallback)
    coordinator.registerDeviceCallback(otherConnectedDevice, otherRecipientId, otherDeviceCallback)

    coordinator.onSecureChannelEstablishedInternal(connectedDevice)

    verify(deviceCallback).onSecureChannelEstablished(connectedDevice)
    verify(otherDeviceCallback, never()).onSecureChannelEstablished(connectedDevice)
  }

  @Test
  fun onAssociatedDeviceAdded_invokesCallbacks() {
    val callback: IDeviceAssociationCallback = mockToBeAlive()
    coordinator.registerDeviceAssociationCallback(callback)
    val associatedDevice =
      AssociatedDevice(
        UUID.randomUUID().toString(),
        "deviceAddress",
        "deviceName",
        /* isConnectionEnabled= */ true
      )

    coordinator.onAssociatedDeviceAddedInternal(associatedDevice)

    verify(callback).onAssociatedDeviceAdded(associatedDevice)
  }

  @Test
  fun onAssociatedDeviceRemoved_invokesCallbacks() {
    val callback: IDeviceAssociationCallback = mockToBeAlive()
    coordinator.registerDeviceAssociationCallback(callback)
    val associatedDevice =
      AssociatedDevice(
        UUID.randomUUID().toString(),
        "deviceAddress",
        "deviceName",
        /* isConnectionEnabled= */ true
      )

    coordinator.onAssociatedDeviceRemovedInternal(associatedDevice)

    verify(callback).onAssociatedDeviceRemoved(associatedDevice)
  }

  @Test
  fun onAssociatedDeviceUpdated_invokesCallbacks() {
    val callback: IDeviceAssociationCallback = mockToBeAlive()
    coordinator.registerDeviceAssociationCallback(callback)
    val associatedDevice =
      AssociatedDevice(
        UUID.randomUUID().toString(),
        "deviceAddress",
        "deviceName",
        /* isConnectionEnabled= */ true
      )

    coordinator.onAssociatedDeviceUpdatedInternal(associatedDevice)

    verify(callback).onAssociatedDeviceUpdated(associatedDevice)
  }

  @Test
  fun unregisterDeviceAssociationCallback_preventsFutureInvocation() {
    val callback: IDeviceAssociationCallback = mockToBeAlive()
    coordinator.registerDeviceAssociationCallback(callback)
    val associatedDevice =
      AssociatedDevice(
        UUID.randomUUID().toString(),
        "deviceAddress",
        "deviceName",
        /* isConnectionEnabled= */ true
      )

    coordinator.unregisterDeviceAssociationCallback(callback)
    coordinator.onAssociatedDeviceAddedInternal(associatedDevice)

    verify(callback, never()).onAssociatedDeviceAdded(associatedDevice)
  }

  @Test
  fun acceptVerification_notifiesControllerCodeAccepted() {
    coordinator.acceptVerification()

    verify(mockController).notifyVerificationCodeAccepted()
  }

  @Test
  fun removeAssociatedDevice_disconnectsAndRemovesAssociatedDeviceFromStorage() {
    val deviceId = UUID.randomUUID()

    coordinator.removeAssociatedDevice(deviceId.toString())

    verify(mockController).disconnectDevice(deviceId)
    verify(mockStorage).removeAssociatedDevice(deviceId.toString())
  }

  @Test
  fun enableAssociatedDeviceConnection_updatesStorageAndInitiatesConnection() {
    val deviceId = UUID.randomUUID()

    coordinator.enableAssociatedDeviceConnection(deviceId.toString())

    verify(mockStorage)
      .updateAssociatedDeviceConnectionEnabled(deviceId.toString(), /* isConnectionEnabled= */ true)
    verify(mockController).initiateConnectionToDevice(deviceId)
  }

  @Test
  fun disableAssociatedDeviceConnection_updatesStorageAndDisconnectsDevice() {
    val deviceId = UUID.randomUUID()

    coordinator.disableAssociatedDeviceConnection(deviceId.toString())

    verify(mockStorage)
      .updateAssociatedDeviceConnectionEnabled(
        deviceId.toString(),
        /* isConnectionEnabled= */ false
      )
    verify(mockController).disconnectDevice(deviceId)
  }

  @Test
  fun retrieveAssociatedDevices_returnsAllAssociatedDevicesInStorage() {
    val associatedDevices =
      listOf(
        AssociatedDevice(
          UUID.randomUUID().toString(),
          /* deviceAddress= */ "",
          /* deviceName= */ null,
          /* isConnectionEnabled= */ true
        ),
        AssociatedDevice(
          UUID.randomUUID().toString(),
          /* deviceAddress= */ "",
          /* deviceName= */ null,
          /* isConnectionEnabled= */ false
        )
      )
    val listener: IOnAssociatedDevicesRetrievedListener = mockToBeAlive()
    whenever(mockStorage.allAssociatedDevices).thenReturn(associatedDevices)

    coordinator.retrieveAssociatedDevices(listener)

    verify(listener).onAssociatedDevicesRetrieved(associatedDevices)
  }

  @Test
  fun retrieveAssociatedDevicesForDriver_returnsOnlyDriverDevicesInStorage() {
    val driverDevices =
      listOf(
        AssociatedDevice(
          UUID.randomUUID().toString(),
          /* deviceAddress= */ "",
          /* deviceName= */ null,
          /* isConnectionEnabled= */ true
        ),
        AssociatedDevice(
          UUID.randomUUID().toString(),
          /* deviceAddress= */ "",
          /* deviceName= */ null,
          /* isConnectionEnabled= */ false
        )
      )
    val listener: IOnAssociatedDevicesRetrievedListener = mockToBeAlive()
    whenever(mockStorage.driverAssociatedDevices).thenReturn(driverDevices)

    coordinator.retrieveAssociatedDevicesForDriver(listener)

    verify(listener).onAssociatedDevicesRetrieved(driverDevices)
  }

  @Test
  fun retrieveAssociatedDevicesForPassengers_returnsOnlyPassengerDevicesInStorage() {
    val passengerDevices =
      listOf(
        AssociatedDevice(
          UUID.randomUUID().toString(),
          /* deviceAddress= */ "",
          /* deviceName= */ null,
          /* isConnectionEnabled= */ true
        ),
        AssociatedDevice(
          UUID.randomUUID().toString(),
          /* deviceAddress= */ "",
          /* deviceName= */ null,
          /* isConnectionEnabled= */ false
        )
      )
    val listener: IOnAssociatedDevicesRetrievedListener = mockToBeAlive()
    whenever(mockStorage.passengerAssociatedDevices).thenReturn(passengerDevices)

    coordinator.retrieveAssociatedDevicesForPassengers(listener)

    verify(listener).onAssociatedDevicesRetrieved(passengerDevices)
  }

  @Test
  fun registerOnLogRequestedListener() {
    val testLoggerId = 0
    val listener: ISafeOnLogRequestedListener = mockToBeAlive()

    coordinator.registerOnLogRequestedListener(testLoggerId, listener)

    verify(mockLoggingManager).registerLogRequestedListener(eq(testLoggerId), eq(listener), any())
  }

  @Test
  fun unregisterOnLogRequestedListener() {
    val testLoggerId = 0
    val listener: ISafeOnLogRequestedListener = mockToBeAlive()

    coordinator.unregisterOnLogRequestedListener(testLoggerId, listener)

    verify(mockLoggingManager).unregisterLogRequestedListener(eq(testLoggerId), eq(listener))
  }

  @Test
  fun processLogRecords() {
    val testLoggerId = 0
    val testLogs = "test logs".toByteArray()

    coordinator.processLogRecords(testLoggerId, testLogs)

    verify(mockLoggingManager).prepareLocalLogRecords(eq(testLoggerId), eq(testLogs))
  }

  @Test
  fun stopAssociation_directCallIntoDeviceController() {
    coordinator.stopAssociation()

    verify(mockController).stopAssociation()
  }

  @Test
  fun start_startsController() {
    coordinator.start()

    verify(mockController).start()
  }

  @Test
  fun reset_resetsController() {
    coordinator.reset()

    verify(mockController).reset()
  }

  @Test
  fun claimAssociatedDevice_disconnectsAndClaimsDeviceAndInitiatesReconnection() {
    val deviceId = UUID.randomUUID()

    coordinator.claimAssociatedDevice(deviceId.toString())

    verify(mockController).disconnectDevice(deviceId)
    verify(mockStorage).claimAssociatedDevice(deviceId.toString())
    verify(mockController).initiateConnectionToDevice(deviceId)
  }

  @Test
  fun removeAssociatedDeviceClaim_disconnectsAndRemovesClaimAndInitiatesReconnection() {
    val deviceId = UUID.randomUUID()

    coordinator.removeAssociatedDeviceClaim(deviceId.toString())

    verify(mockController).disconnectDevice(deviceId)
    verify(mockStorage).removeAssociatedDeviceClaim(deviceId.toString())
    verify(mockController).initiateConnectionToDevice(deviceId)
  }

  // The following tests are for the SafeFeatureCoordinator contained in FeatureCoordinator.

  @Test
  fun safeFC_getConnectedDevices_returnsOnlyDriverDevices() {
    val driverDevice1 =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName1",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val driverDevice2 =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "driverDeviceName2",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val passengerDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "passengerDeviceName",
        /* belongsToDriver= */ false,
        /* hasSecureChannel= */ true
      )
    whenever(mockController.connectedDevices)
      .thenReturn(listOf(driverDevice1, passengerDevice, driverDevice2))

    val driverDevices = safeCoordinator.getConnectedDevices()

    assertThat(driverDevices).containsExactly(driverDevice1.deviceId, driverDevice2.deviceId)
  }

  @Test
  fun safeFC_onDeviceConnected_connectionCallbacksInvoked() {
    val mockConnectionCallback: ISafeConnectionCallback = mockToBeAlive()
    safeCoordinator.registerConnectionCallback(mockConnectionCallback)
    val deviceId = UUID.randomUUID().toString()

    coordinator.safeOnDeviceConnectedInternal(deviceId)

    verify(mockConnectionCallback).onDeviceConnected(deviceId)
  }

  @Test
  fun safeFC_onDeviceDisconnected_connectionCallbacksInvoked() {
    val mockConnectionCallback: ISafeConnectionCallback = mockToBeAlive()
    safeCoordinator.registerConnectionCallback(mockConnectionCallback)
    val deviceId = UUID.randomUUID().toString()

    coordinator.safeOnDeviceDisconnectedInternal(deviceId)

    verify(mockConnectionCallback).onDeviceDisconnected(deviceId)
  }

  @Test
  fun safeFC_unregisterConnectionCallback_connectionCallbackNotInvokedWhenDeviceConnects() {
    val mockConnectionCallback: ISafeConnectionCallback = mockToBeAlive()
    safeCoordinator.registerConnectionCallback(mockConnectionCallback)
    val deviceId = UUID.randomUUID().toString()

    safeCoordinator.unregisterConnectionCallback(mockConnectionCallback)
    coordinator.safeOnDeviceConnectedInternal(deviceId)

    verify(mockConnectionCallback, never()).onDeviceConnected(deviceId)
  }

  @Test
  fun safeFC_registerDeviceCallback_sendsMissedMessages() {
    val deviceCallback: ISafeDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val message =
      DeviceMessageProto.Message.newBuilder()
        .setRecipient(ByteString.copyFrom(ByteUtils.uuidToBytes(recipientId.uuid)))
        .setIsPayloadEncrypted(true)
        .setOperation(
          OperationType.forNumber(/* CLIENT_MESSAGE */ 4) ?: OperationType.OPERATION_TYPE_UNKNOWN
        )
        .setPayload(ByteString.copyFrom(ByteUtils.randomBytes(10)))
        .build()

    val rawBytes = message.toByteArray()

    val missedMessage =
      DeviceMessage.createOutgoingMessage(
        ByteUtils.bytesToUUID(message.recipient.toByteArray()),
        message.isPayloadEncrypted,
        DeviceMessage.OperationType.fromValue(message.operation.number),
        message.payload.toByteArray()
      )

    coordinator.onMessageReceivedInternal(connectedDevice, missedMessage)
    safeCoordinator.registerDeviceCallback(connectedDevice.deviceId, recipientId, deviceCallback)

    verify(deviceCallback).onMessageReceived(connectedDevice.deviceId, rawBytes)
  }

  @Test
  fun safeFC_registerDeviceCallback_blocksRecipientAndAlivePreviousRegistererIfAlreadyRegistered() {
    val deviceCallback: ISafeDeviceCallback = mockToBeAlive()
    val duplicateDeviceCallback: ISafeDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )

    safeCoordinator.registerDeviceCallback(connectedDevice.deviceId, recipientId, deviceCallback)
    safeCoordinator.registerDeviceCallback(
      connectedDevice.deviceId,
      recipientId,
      duplicateDeviceCallback
    )

    verify(deviceCallback)
      .onDeviceError(connectedDevice.deviceId, DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED)
    verify(duplicateDeviceCallback)
      .onDeviceError(connectedDevice.deviceId, DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED)
  }

  @Test
  fun safeFC_registerDeviceCallback_ignoreDeadPreviousRegistererIfAlreadyRegistered() {
    val deadDeviceCallback: ISafeDeviceCallback = mockToBeDead()
    val duplicateDeviceCallback: ISafeDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )

    safeCoordinator.registerDeviceCallback(
      connectedDevice.deviceId,
      recipientId,
      deadDeviceCallback
    )
    safeCoordinator.registerDeviceCallback(
      connectedDevice.deviceId,
      recipientId,
      duplicateDeviceCallback
    )

    verify(duplicateDeviceCallback, never())
      .onDeviceError(connectedDevice.deviceId, DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED)
  }

  @Test
  fun safeFC_registerDeviceCallback_alreadyBlockedRecipientNotifiedOnRegistration() {
    val deviceCallback: ISafeDeviceCallback = mockToBeAlive()
    val duplicateDeviceCallback: ISafeDeviceCallback = mockToBeAlive()
    val secondDuplicateDeviceCallback: ISafeDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    safeCoordinator.registerDeviceCallback(connectedDevice.deviceId, recipientId, deviceCallback)
    safeCoordinator.registerDeviceCallback(
      connectedDevice.deviceId,
      recipientId,
      duplicateDeviceCallback
    )

    safeCoordinator.registerDeviceCallback(
      connectedDevice.deviceId,
      recipientId,
      secondDuplicateDeviceCallback
    )

    verify(secondDuplicateDeviceCallback)
      .onDeviceError(connectedDevice.deviceId, DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED)
  }

  @Test
  fun safeFC_unregisterDeviceCallback_callbackNotInvokedAfterUnregistering() {
    val deviceCallback: ISafeDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val message =
      DeviceMessage.createOutgoingMessage(
        recipientId.uuid,
        /* isMessageEncrypted= */ true,
        CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )

    safeCoordinator.registerDeviceCallback(connectedDevice.deviceId, recipientId, deviceCallback)
    safeCoordinator.unregisterDeviceCallback(connectedDevice.deviceId, recipientId, deviceCallback)
    coordinator.onMessageReceivedInternal(connectedDevice, message)

    verify(deviceCallback, never()).onMessageReceived(any(), any())
  }

  @Test
  fun safeFC_onMessageReceived_callbackInvokedWhenMessageReceivedForRecipient() {
    val deviceCallback: ISafeDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val message =
      DeviceMessage.createOutgoingMessage(
        recipientId.uuid,
        /* isMessageEncrypted= */ true,
        CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )

    safeCoordinator.registerDeviceCallback(connectedDevice.deviceId, recipientId, deviceCallback)
    coordinator.onMessageReceivedInternal(connectedDevice, message)

    verify(deviceCallback).onMessageReceived(connectedDevice.deviceId, message.message)
  }

  @Test
  fun safeFC_onMessageReceived_callbackNotInvokedWhenMessageReceivedForDifferentRecipient() {
    val deviceCallback: ISafeDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val otherRecipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val message =
      DeviceMessage.createOutgoingMessage(
        otherRecipientId.uuid,
        /* isMessageEncrypted= */ true,
        CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )

    safeCoordinator.registerDeviceCallback(connectedDevice.deviceId, recipientId, deviceCallback)
    coordinator.onMessageReceivedInternal(connectedDevice, message)

    verify(deviceCallback, never()).onMessageReceived(any(), any())
  }

  @Test
  fun safeFC_onMessageReceived_blockedRecipientCallbacksNotInvoked() {
    val deviceCallback: ISafeDeviceCallback = mockToBeAlive()
    val duplicateDeviceCallback: ISafeDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val message =
      DeviceMessage.createOutgoingMessage(
        recipientId.uuid,
        /* isMessageEncrypted= */ true,
        CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )
    safeCoordinator.registerDeviceCallback(connectedDevice.deviceId, recipientId, deviceCallback)
    safeCoordinator.registerDeviceCallback(
      connectedDevice.deviceId,
      recipientId,
      duplicateDeviceCallback
    )

    coordinator.onMessageReceivedInternal(connectedDevice, message)

    verify(deviceCallback, never()).onMessageReceived(any(), any())
    verify(duplicateDeviceCallback, never()).onMessageReceived(any(), any())
  }

  @Test
  fun safeFC_onMessageReceived_blockedRecipientRecoverFromResetCallbacksInvoked() {
    val deviceCallback: ISafeDeviceCallback = mockToBeAlive()
    val duplicateDeviceCallback: ISafeDeviceCallback = mockToBeAlive()
    val recipientId = ParcelUuid(UUID.randomUUID())
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    safeCoordinator.registerDeviceCallback(connectedDevice.deviceId, recipientId, deviceCallback)
    safeCoordinator.registerDeviceCallback(
      connectedDevice.deviceId,
      recipientId,
      duplicateDeviceCallback
    )

    // Clear the blocked recipient list.
    coordinator.reset()
    safeCoordinator.registerDeviceCallback(connectedDevice.deviceId, recipientId, deviceCallback)
    val message =
      DeviceMessage.createOutgoingMessage(
        recipientId.uuid,
        /* isMessageEncrypted= */ true,
        CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )
    coordinator.onMessageReceivedInternal(connectedDevice, message)

    verify(deviceCallback).onMessageReceived(any(), any())
    verify(duplicateDeviceCallback, never()).onMessageReceived(any(), any())
  }

  @Test
  fun safeFC_onMessageReceived_nullRecipientDoesNotThrow() {
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
    val nullRecipientMessage =
      DeviceMessage.createOutgoingMessage(
        /* recipient= */ null,
        /* isMessageEncrypted= */ true,
        CLIENT_MESSAGE,
        ByteUtils.randomBytes(10)
      )
    coordinator.onMessageReceivedInternal(connectedDevice, nullRecipientMessage)
  }

  @Test
  fun safeFC_sendMessage_sendsMessageToController() {
    val connectedDevice =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        "testDeviceName",
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )
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

    val messageSent = safeCoordinator.sendMessage(connectedDevice.deviceId, rawBytes)

    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        ByteUtils.bytesToUUID(message.recipient.toByteArray()),
        message.isPayloadEncrypted,
        DeviceMessage.OperationType.fromValue(message.operation.number),
        message.payload.toByteArray()
      )

    assertThat(messageSent).isFalse() // Recipient not registered so this will return false
    verify(mockController).sendMessage(UUID.fromString(connectedDevice.deviceId), deviceMessage)
  }

  @Test
  fun safeFC_registerOnLogRequestedListener() {
    val testLoggerId = 0
    val listener: ISafeOnLogRequestedListener = mockToBeAlive()

    safeCoordinator.registerOnLogRequestedListener(testLoggerId, listener)

    verify(mockLoggingManager).registerLogRequestedListener(eq(testLoggerId), eq(listener), any())
  }

  @Test
  fun safeFC_unregisterOnLogRequestedListener() {
    val testLoggerId = 0
    val listener: ISafeOnLogRequestedListener = mockToBeAlive()

    safeCoordinator.unregisterOnLogRequestedListener(testLoggerId, listener)

    verify(mockLoggingManager).unregisterLogRequestedListener(eq(testLoggerId), eq(listener))
  }

  @Test
  fun safeFC_processLogRecords() {
    val testLoggerId = 0
    val testLogs = "test logs".toByteArray()

    safeCoordinator.processLogRecords(testLoggerId, testLogs)

    verify(mockLoggingManager).prepareLocalLogRecords(eq(testLoggerId), eq(testLogs))
  }

  @Test
  fun safeFC_retrieveAssociatedDevices_returnsOnlyDriverDevicesInStorage() {
    val driverDevices =
      listOf(
        AssociatedDevice(
          UUID.randomUUID().toString(),
          /* deviceAddress= */ "",
          /* deviceName= */ null,
          /* isConnectionEnabled= */ true
        ),
        AssociatedDevice(
          UUID.randomUUID().toString(),
          /* deviceAddress= */ "",
          /* deviceName= */ null,
          /* isConnectionEnabled= */ false
        )
      )
    val listener: ISafeOnAssociatedDevicesRetrievedListener = mockToBeAlive()
    whenever(mockStorage.driverAssociatedDevices).thenReturn(driverDevices)

    safeCoordinator.retrieveAssociatedDevices(listener)

    verify(listener).onAssociatedDevicesRetrieved(driverDevices.map { it.deviceId })
  }

  companion object {
    private const val TAG = "FeatureCoordinatorTest"
  }
}
