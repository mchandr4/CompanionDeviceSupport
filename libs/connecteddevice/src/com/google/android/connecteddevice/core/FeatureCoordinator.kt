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
package com.google.android.connecteddevice.core

import android.os.ParcelUuid
import androidx.annotation.VisibleForTesting
import com.google.android.connecteddevice.api.IAssociationCallback
import com.google.android.connecteddevice.api.IConnectionCallback
import com.google.android.connecteddevice.api.IDeviceAssociationCallback
import com.google.android.connecteddevice.api.IDeviceCallback
import com.google.android.connecteddevice.api.IFeatureCoordinator
import com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener
import com.google.android.connecteddevice.api.IOnLogRequestedListener
import com.google.android.connecteddevice.logging.LoggingManager
import com.google.android.connecteddevice.model.AssociatedDevice
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.model.Errors.DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.util.AidlThreadSafeCallbacks
import com.google.android.connecteddevice.util.ByteUtils
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Coordinator between features and connected devices. */
class FeatureCoordinator
@JvmOverloads
constructor(
  private val controller: DeviceController,
  private val storage: ConnectedDeviceStorage,
  private val loggingManager: LoggingManager,
  private val callbackExecutor: Executor = Executors.newCachedThreadPool()
) : IFeatureCoordinator.Stub() {

  private val deviceAssociationCallbacks = AidlThreadSafeCallbacks<IDeviceAssociationCallback>()

  private val driverConnectionCallbacks = AidlThreadSafeCallbacks<IConnectionCallback>()

  private val passengerConnectionCallbacks = AidlThreadSafeCallbacks<IConnectionCallback>()

  private val allConnectionCallbacks = AidlThreadSafeCallbacks<IConnectionCallback>()

  // deviceId -> (recipientId -> callbacks)
  private val deviceCallbacks:
    MutableMap<String, MutableMap<ParcelUuid, AidlThreadSafeCallbacks<IDeviceCallback>>> =
    ConcurrentHashMap()

  // recipientId -> (deviceId -> message bytes)
  private val recipientMissedMessages:
    MutableMap<ParcelUuid, MutableMap<String, MutableList<DeviceMessage>>> =
    ConcurrentHashMap()

  // Recipient ids that received multiple callback registrations indicate that the recipient id
  // has been compromised. Another party now has access the messages intended for that recipient.
  // As a safeguard, that recipient id will be added to this list and blocked from further
  // callback notifications.
  private val blockedRecipients: MutableSet<ParcelUuid> = CopyOnWriteArraySet()

  init {
    controller.registerCallback(createDeviceControllerCallback(), callbackExecutor)
    storage.setAssociatedDeviceCallback(createStorageAssociatedDeviceCallback())
  }

  /** Initiate connections with all enabled [AssociatedDevice]s. */
  fun start() {
    logd(TAG, "Initializing coordinator.")
    controller.start()
  }

  /** Disconnect all devices and reset state. */
  fun reset() {
    logd(TAG, "Resetting coordinator.")
    deviceCallbacks.clear()
    controller.reset()
    recipientMissedMessages.clear()
  }

  override fun getConnectedDevicesForDriver(): List<ConnectedDevice> =
    controller.connectedDevices.filter { it.isAssociatedWithDriver }

  override fun getConnectedDevicesForPassengers(): List<ConnectedDevice> =
    controller.connectedDevices.filter { !it.isAssociatedWithDriver }

  override fun getAllConnectedDevices(): List<ConnectedDevice> = controller.connectedDevices

  override fun registerDriverConnectionCallback(callback: IConnectionCallback) {
    driverConnectionCallbacks.add(callback, callbackExecutor)
  }

  override fun registerPassengerConnectionCallback(callback: IConnectionCallback) {
    passengerConnectionCallbacks.add(callback, callbackExecutor)
  }

  override fun registerAllConnectionCallback(callback: IConnectionCallback) {
    allConnectionCallbacks.add(callback, callbackExecutor)
  }

  override fun unregisterConnectionCallback(callback: IConnectionCallback) {
    driverConnectionCallbacks.remove(callback)
    passengerConnectionCallbacks.remove(callback)
    allConnectionCallbacks.remove(callback)
  }

  override fun registerDeviceCallback(
    connectedDevice: ConnectedDevice,
    recipientId: ParcelUuid,
    callback: IDeviceCallback
  ) {
    if (blockedRecipients.contains(recipientId)) {
      notifyRecipientBlocked(connectedDevice, recipientId, callback)
      return
    }

    val recipientCallbacks =
      deviceCallbacks.computeIfAbsent(connectedDevice.deviceId) { ConcurrentHashMap() }
    val newCallbacks =
      AidlThreadSafeCallbacks<IDeviceCallback>().apply { add(callback, callbackExecutor) }
    recipientCallbacks.computeIfPresent(recipientId) { _, callbacks ->
      if (callbacks.isEmpty) null else callbacks
    }
    val previousCallbacks = recipientCallbacks.putIfAbsent(recipientId, newCallbacks)

    // Device already has a callback registered with this recipient UUID. For the
    // protection of the user, this UUID is now deny listed from future subscriptions
    // and the original subscription is notified and removed.
    if (previousCallbacks != null) {
      blockedRecipients.add(recipientId)
      recipientCallbacks.remove(recipientId)
      previousCallbacks.invoke {
        it.onDeviceError(connectedDevice, DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED)
      }
      notifyRecipientBlocked(connectedDevice, recipientId, callback)
      return
    }

    logd(
      TAG,
      "New callback registered on device ${connectedDevice.deviceId} for recipient $recipientId."
    )
    notifyOfMissedMessages(connectedDevice, recipientId, callback)
  }

  private fun notifyRecipientBlocked(
    connectedDevice: ConnectedDevice,
    recipientId: ParcelUuid,
    callback: IDeviceCallback
  ) {
    loge(
      TAG,
      "Multiple callbacks registered for recipient $recipientId! Your recipient id is no " +
        "longer secure and has been blocked from future use."
    )
    callbackExecutor.execute {
      callback.onDeviceError(connectedDevice, DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED)
    }
  }

  private fun notifyOfMissedMessages(
    connectedDevice: ConnectedDevice,
    recipientId: ParcelUuid,
    callback: IDeviceCallback
  ) {
    val missedMessages = recipientMissedMessages[recipientId]?.remove(connectedDevice.deviceId)
    if (missedMessages?.isNotEmpty() != true) {
      return
    }
    logd(TAG, "Notifying $recipientId of missed messages.")
    callbackExecutor.execute {
      missedMessages.forEach { callback.onMessageReceived(connectedDevice, it) }
    }
  }

  override fun unregisterDeviceCallback(
    connectedDevice: ConnectedDevice,
    recipientId: ParcelUuid,
    callback: IDeviceCallback
  ) {
    logd(
      TAG,
      "Device callback unregistered on device ${connectedDevice.deviceId} for recipient " +
        "$recipientId."
    )
    val callbacks = deviceCallbacks[connectedDevice.deviceId]?.get(recipientId)
    callbacks?.remove(callback)
    if (callbacks?.isEmpty == true) {
      deviceCallbacks[connectedDevice.deviceId]?.remove(recipientId)
    }
  }

  override fun sendMessage(connectedDevice: ConnectedDevice, message: DeviceMessage): Boolean =
    controller.sendMessage(UUID.fromString(connectedDevice.deviceId), message)

  override fun registerDeviceAssociationCallback(callback: IDeviceAssociationCallback) {
    deviceAssociationCallbacks.add(callback, callbackExecutor)
  }

  override fun unregisterDeviceAssociationCallback(callback: IDeviceAssociationCallback) {
    deviceAssociationCallbacks.remove(callback)
  }

  override fun registerOnLogRequestedListener(loggerId: Int, listener: IOnLogRequestedListener) {
    loggingManager.registerLogRequestedListener(loggerId, listener, callbackExecutor)
  }

  override fun unregisterOnLogRequestedListener(loggerId: Int, listener: IOnLogRequestedListener) {
    loggingManager.unregisterLogRequestedListener(loggerId, listener)
  }

  override fun processLogRecords(loggerId: Int, logRecords: ByteArray) {
    loggingManager.prepareLocalLogRecords(loggerId, logRecords)
  }

  override fun startAssociation(callback: IAssociationCallback) {
    startAssociationInternal(callback)
  }

  override fun startAssociationWithIdentifier(
    callback: IAssociationCallback,
    identifier: ParcelUuid
  ) {
    startAssociationInternal(callback, identifier)
  }

  override fun stopAssociation() {
    logd(TAG, "Received request to stop association.")
    controller.stopAssociation()
  }

  override fun retrieveAssociatedDevices(listener: IOnAssociatedDevicesRetrievedListener) {
    callbackExecutor.execute { listener.onAssociatedDevicesRetrieved(storage.allAssociatedDevices) }
  }

  override fun retrieveAssociatedDevicesForDriver(listener: IOnAssociatedDevicesRetrievedListener) {
    callbackExecutor.execute {
      listener.onAssociatedDevicesRetrieved(storage.driverAssociatedDevices)
    }
  }

  override fun retrieveAssociatedDevicesForPassengers(
    listener: IOnAssociatedDevicesRetrievedListener
  ) {
    callbackExecutor.execute {
      listener.onAssociatedDevicesRetrieved(storage.passengerAssociatedDevices)
    }
  }

  override fun acceptVerification() {
    controller.notifyVerificationCodeAccepted()
  }

  override fun removeAssociatedDevice(deviceId: String) {
    controller.disconnectDevice(UUID.fromString(deviceId))
    storage.removeAssociatedDevice(deviceId)
  }

  override fun enableAssociatedDeviceConnection(deviceId: String) {
    storage.updateAssociatedDeviceConnectionEnabled(deviceId, /* isConnectionEnabled= */ true)
    controller.initiateConnectionToDevice(UUID.fromString(deviceId))
  }

  override fun disableAssociatedDeviceConnection(deviceId: String) {
    storage.updateAssociatedDeviceConnectionEnabled(deviceId, /* isConnectionEnabled= */ false)
    controller.disconnectDevice(UUID.fromString(deviceId))
  }

  override fun claimAssociatedDevice(deviceId: String) {
    storage.claimAssociatedDevice(deviceId)
  }

  override fun removeAssociatedDeviceClaim(deviceId: String) {
    storage.removeAssociatedDeviceClaim(deviceId)
  }

  private fun startAssociationInternal(
    callback: IAssociationCallback,
    identifier: ParcelUuid? = null
  ) {
    logd(TAG, "Received request to start association with identifier $identifier.")
    controller.startAssociation(
      ByteUtils.byteArrayToHexString(ByteUtils.randomBytes(DEVICE_NAME_LENGTH)),
      callback,
      identifier?.uuid
    )
  }

  private fun saveMissedMessage(connectedDevice: ConnectedDevice, message: DeviceMessage) =
    recipientMissedMessages
      .computeIfAbsent(ParcelUuid(message.recipient)) { ConcurrentHashMap() }
      .computeIfAbsent(connectedDevice.deviceId) { CopyOnWriteArrayList() }
      .add(message)

  @VisibleForTesting
  internal fun onDeviceConnectedInternal(connectedDevice: ConnectedDevice) {
    logd(TAG, "Connected device has a secure channel ${connectedDevice.hasSecureChannel()}")
    if (connectedDevice.isAssociatedWithDriver) {
      logd(TAG, "Notifying callbacks that a new device has connected for the driver.")
      driverConnectionCallbacks.invoke { it.onDeviceConnected(connectedDevice) }
    } else {
      logd(TAG, "Notifying callbacks that a new device has connected for a passenger.")
      passengerConnectionCallbacks.invoke { it.onDeviceConnected(connectedDevice) }
    }
    allConnectionCallbacks.invoke { it.onDeviceConnected(connectedDevice) }
  }

  @VisibleForTesting
  internal fun onDeviceDisconnectedInternal(connectedDevice: ConnectedDevice) {
    if (connectedDevice.isAssociatedWithDriver) {
      logd(TAG, "Notifying callbacks that a device has disconnected for the driver.")
      driverConnectionCallbacks.invoke { it.onDeviceDisconnected(connectedDevice) }
    } else {
      logd(TAG, "Notifying callbacks that a device has disconnected for a passenger.")
      passengerConnectionCallbacks.invoke { it.onDeviceDisconnected(connectedDevice) }
    }
    allConnectionCallbacks.invoke { it.onDeviceDisconnected(connectedDevice) }
  }

  @VisibleForTesting
  internal fun onSecureChannelEstablishedInternal(connectedDevice: ConnectedDevice) {
    logd(
      TAG,
      "Notifying callbacks that a secure channel has been established with " +
        "${connectedDevice.deviceId}."
    )
    deviceCallbacks[connectedDevice.deviceId]?.values?.forEach { callback ->
      callback.invoke { it.onSecureChannelEstablished(connectedDevice) }
    }
  }

  @VisibleForTesting
  internal fun onMessageReceivedInternal(connectedDevice: ConnectedDevice, message: DeviceMessage) {
    if (message.recipient == null) {
      loge(
        TAG,
        "Received callback for a new message containing no recipient. No callbacks were invoked!"
      )
      return
    }
    logd(TAG, "Received a new message for ${message.recipient} from ${connectedDevice.deviceId}.")
    val callback = deviceCallbacks[connectedDevice.deviceId]?.get(ParcelUuid(message.recipient))

    if (callback == null) {
      logd(TAG, "Recipient has not registered a callback yet. Saving missed message.")
      saveMissedMessage(connectedDevice, message)
      return
    }

    callback.invoke { it.onMessageReceived(connectedDevice, message) }
  }

  @VisibleForTesting
  internal fun onAssociatedDeviceAddedInternal(device: AssociatedDevice) {
    deviceAssociationCallbacks.invoke { it.onAssociatedDeviceAdded(device) }
  }

  @VisibleForTesting
  internal fun onAssociatedDeviceRemovedInternal(device: AssociatedDevice) {
    deviceAssociationCallbacks.invoke { it.onAssociatedDeviceRemoved(device) }
  }

  @VisibleForTesting
  internal fun onAssociatedDeviceUpdatedInternal(device: AssociatedDevice) {
    deviceAssociationCallbacks.invoke { it.onAssociatedDeviceUpdated(device) }
  }

  private fun createDeviceControllerCallback() =
    object : DeviceController.Callback {
      override fun onDeviceConnected(connectedDevice: ConnectedDevice) {
        onDeviceConnectedInternal(connectedDevice)
      }

      override fun onDeviceDisconnected(connectedDevice: ConnectedDevice) {
        onDeviceDisconnectedInternal(connectedDevice)
      }

      override fun onSecureChannelEstablished(connectedDevice: ConnectedDevice) {
        onSecureChannelEstablishedInternal(connectedDevice)
      }

      override fun onMessageReceived(connectedDevice: ConnectedDevice, message: DeviceMessage) {
        onMessageReceivedInternal(connectedDevice, message)
      }
    }

  private fun createStorageAssociatedDeviceCallback() =
    object : ConnectedDeviceStorage.AssociatedDeviceCallback {
      override fun onAssociatedDeviceAdded(device: AssociatedDevice) {
        onAssociatedDeviceAddedInternal(device)
      }

      override fun onAssociatedDeviceRemoved(device: AssociatedDevice) {
        onAssociatedDeviceRemovedInternal(device)
      }

      override fun onAssociatedDeviceUpdated(device: AssociatedDevice) {
        onAssociatedDeviceUpdatedInternal(device)
      }
    }

  companion object {
    private const val TAG = "FeatureCoordinator"

    @VisibleForTesting internal const val DEVICE_NAME_LENGTH = 2
  }
}
