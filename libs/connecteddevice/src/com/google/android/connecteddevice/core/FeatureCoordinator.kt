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

import android.os.IInterface
import android.os.ParcelUuid
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import com.google.android.companionprotos.DeviceMessageProto
import com.google.android.companionprotos.OperationProto.OperationType
import com.google.android.connecteddevice.api.IAssociationCallback
import com.google.android.connecteddevice.api.IConnectionCallback
import com.google.android.connecteddevice.api.IDeviceAssociationCallback
import com.google.android.connecteddevice.api.IDeviceCallback
import com.google.android.connecteddevice.api.IFeatureCoordinator
import com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener
import com.google.android.connecteddevice.api.external.ISafeConnectionCallback
import com.google.android.connecteddevice.api.external.ISafeDeviceCallback
import com.google.android.connecteddevice.api.external.ISafeFeatureCoordinator
import com.google.android.connecteddevice.api.external.ISafeOnAssociatedDevicesRetrievedListener
import com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener
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
import com.google.android.connecteddevice.util.SafeLog.logw
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Coordinator between features and connected devices. */
class FeatureCoordinator
@JvmOverloads
constructor(
  private val controller: DeviceController,
  private val storage: ConnectedDeviceStorage,
  private val systemQueryCache: SystemQueryCache = SystemQueryCache.create(),
  private val loggingManager: LoggingManager,
  private val callbackExecutor: Executor = Executors.newCachedThreadPool(),
) : IFeatureCoordinator.Stub() {

  private val deviceAssociationCallbacks = AidlThreadSafeCallbacks<IDeviceAssociationCallback>()

  private val driverConnectionCallbacks = AidlThreadSafeCallbacks<IConnectionCallback>()

  private val passengerConnectionCallbacks = AidlThreadSafeCallbacks<IConnectionCallback>()

  private val allConnectionCallbacks = AidlThreadSafeCallbacks<IConnectionCallback>()

  @VisibleForTesting
  internal val safeConnectionCallbacks = AidlThreadSafeCallbacks<ISafeConnectionCallback>()

  private val lock = ReentrantLock()

  // deviceId -> (recipientId -> callback)s
  @GuardedBy("lock")
  private val deviceCallbacks: MutableMap<String, MutableMap<ParcelUuid, IDeviceCallback>> =
    ConcurrentHashMap()

  // deviceId -> (recipientId -> callback)s
  @GuardedBy("lock")
  private val safeDeviceCallbacks: MutableMap<String, MutableMap<ParcelUuid, ISafeDeviceCallback>> =
    ConcurrentHashMap()

  // Recipient ids that received multiple callback registrations indicate that the recipient id
  // has been compromised. Another party now has access the messages intended for that recipient.
  // As a safeguard, that recipient id will be added to this list and blocked from further
  // callback notifications.
  @GuardedBy("lock") private val blockedRecipients = mutableSetOf<ParcelUuid>()

  // recipientId -> (deviceId -> message bytes)
  private val recipientMissedMessages:
    MutableMap<ParcelUuid, MutableMap<String, MutableList<DeviceMessage>>> =
    ConcurrentHashMap()

  /**
   * Coordinator between external features and connected devices.
   *
   * FeatureCoordinator exposes some APIs meant for Companion Platform features and some APIs meant
   * for external features (SUW, Account Transfer, etc.). SafeFeatureCoordinator only implements the
   * subset of APIs meant for external features, providing a better Feature Coordinator for
   * Companion to give to these external features. "Safe" simply means "Safe for External Features
   * to use."
   */
  public val safeFeatureCoordinator =
    object : ISafeFeatureCoordinator.Stub() {

      // Retrieves Connected Devices for Driver
      override fun getConnectedDevices(): List<String> =
        this@FeatureCoordinator.getConnectedDevicesForDriver().map { it.deviceId }

      override fun registerConnectionCallback(callback: ISafeConnectionCallback) {
        safeConnectionCallbacks.add(callback, callbackExecutor)
      }

      override fun unregisterConnectionCallback(callback: ISafeConnectionCallback) {
        safeConnectionCallbacks.remove(callback)
      }

      override fun registerDeviceCallback(
        deviceId: String,
        recipientId: ParcelUuid,
        callback: ISafeDeviceCallback
      ) {
        val connectedDevice =
          ConnectedDevice(
            deviceId,
            /* deviceName= */ null,
            /* belongsToDriver= */ false,
            /* hasSecureChannel= */ false
          )

        val registrationSuccessful =
          lock.withLock { registerDeviceCallbackLocked(connectedDevice, recipientId, callback) }

        if (registrationSuccessful) {
          notifyOfMissedMessages(connectedDevice, recipientId, callback)
        } else {
          loge(
            TAG,
            "Multiple callbacks registered for recipient $recipientId! " +
              "Your recipient id is no longer secure and has been blocked from future use."
          )
          callbackExecutor.execute {
            callback.onDeviceError(deviceId, DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED)
          }
        }
      }

      override fun unregisterDeviceCallback(
        deviceId: String,
        recipientId: ParcelUuid,
        callback: ISafeDeviceCallback
      ) {
        lock.withLock { unregisterDeviceCallbackLocked(deviceId, recipientId, callback) }
      }

      override fun sendMessage(deviceId: String, message: ByteArray): Boolean {
        val connectedDevice = controller.connectedDevices.firstOrNull { it.deviceId == deviceId }
        if (connectedDevice == null) {
          loge(TAG, "Device $deviceId not found. Unable to send message.")
          return false
        }
        // TODO(b/265862484): Deprecate DeviceMessage in favor of byte arrays.
        val parsedMessage =
          try {
            DeviceMessageProto.Message.parseFrom(message)
          } catch (e: InvalidProtocolBufferException) {
            loge(TAG, "Cannot parse device message to send.", e)
            return false
          }
        val deviceMessage =
          DeviceMessage.createOutgoingMessage(
            ByteUtils.bytesToUUID(parsedMessage.recipient.toByteArray()),
            parsedMessage.isPayloadEncrypted,
            DeviceMessage.OperationType.fromValue(parsedMessage.operation.number),
            parsedMessage.payload.toByteArray()
          )
        val cachedResponse = systemQueryCache.getCachedResponse(connectedDevice, deviceMessage)
        if (cachedResponse != null) {
          // If a system query has a cached answer, short-circuit the query/response flow by faking
          // a response. Using the cached response allows us to speed up queries by features when
          // the response time is limited (e.g. time for SecondDeviceSignInUrlFeature to be
          // "ready").
          //
          // Schedule the response callback on a different executor to avoid the callback is
          // delivered before this sendMessage method completes/returns.
          callbackExecutor.execute {
            onMessageReceivedInternal(connectedDevice, cachedResponse, shouldCacheMessage = false)
          }
          return true
        }
        return controller.sendMessage(UUID.fromString(deviceId), deviceMessage)
      }

      override fun registerOnLogRequestedListener(
        loggerId: Int,
        listener: ISafeOnLogRequestedListener
      ) {
        this@FeatureCoordinator.registerOnLogRequestedListener(loggerId, listener)
      }

      override fun unregisterOnLogRequestedListener(
        loggerId: Int,
        listener: ISafeOnLogRequestedListener
      ) {
        this@FeatureCoordinator.unregisterOnLogRequestedListener(loggerId, listener)
      }

      override fun processLogRecords(loggerId: Int, logRecords: ByteArray) {
        this@FeatureCoordinator.processLogRecords(loggerId, logRecords)
      }

      // Retrieves Associated Devices for Driver
      override fun retrieveAssociatedDevices(listener: ISafeOnAssociatedDevicesRetrievedListener) {
        callbackExecutor.execute {
          listener.onAssociatedDevicesRetrieved(
            storage.getDriverAssociatedDevices().map { it.deviceId }
          )
        }
      }
    }

  init {
    controller.registerCallback(createDeviceControllerCallback(), callbackExecutor)
    storage.registerAssociatedDeviceCallback(createStorageAssociatedDeviceCallback())
  }

  /** Initiate connections with all enabled [AssociatedDevice]s. */
  fun start() {
    logd(TAG, "Initializing coordinator.")
    controller.start()
  }

  /** Disconnect all devices and reset state. */
  fun reset() {
    logd(TAG, "Resetting coordinator.")
    lock.withLock {
      deviceCallbacks.clear()
      safeDeviceCallbacks.clear()
      blockedRecipients.clear()
    }
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
    val registrationSuccessful =
      lock.withLock { registerDeviceCallbackLocked(connectedDevice, recipientId, callback) }

    if (registrationSuccessful) {
      notifyOfMissedMessages(connectedDevice, recipientId, callback)
    } else {
      loge(
        TAG,
        "Multiple callbacks registered for recipient $recipientId! " +
          "Your recipient id is no longer secure and has been blocked from future use."
      )
      callbackExecutor.execute {
        callback.onDeviceError(connectedDevice, DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED)
      }
    }
  }

  @GuardedBy("lock")
  private fun registerDeviceCallbackLocked(
    connectedDevice: ConnectedDevice,
    recipientId: ParcelUuid,
    callback: IInterface
  ): Boolean {
    if (recipientId in blockedRecipients) {
      logw(TAG, "Recipient $recipientId is already blocked. Request to register callback ignored.")
      return false
    }

    // TODO(b/266652724): Replace this with AidlCallback; isBinderAlive might not always be
    // accurate.
    if (!callback.asBinder().isBinderAlive) {
      logd(TAG, "Attempted to register dead callback. Request to register callback ignored.")
      return false
    }

    val recipientCallbacks =
      when (callback) {
        is IDeviceCallback ->
          deviceCallbacks.computeIfAbsent(connectedDevice.deviceId) { ConcurrentHashMap() }
        is ISafeDeviceCallback ->
          safeDeviceCallbacks.computeIfAbsent(connectedDevice.deviceId) { ConcurrentHashMap() }
        else -> {
          logd(
            TAG,
            "Attempted to use unsupported callback type. Request to register callback ignored."
          )
          return false
        }
      }

    val previousCallback =
      deviceCallbacks[connectedDevice.deviceId]?.get(recipientId)
        ?: safeDeviceCallbacks[connectedDevice.deviceId]?.get(recipientId)

    // Device already has a callback registered with this recipient UUID. For the
    // protection of the user, this UUID is now deny listed from future subscriptions
    // and the original subscription is notified and removed.
    if (previousCallback != null) {
      logd(TAG, "A callback already existed for recipient $recipientId. Block the recipient.")
      blockedRecipients.add(recipientId)
      recipientCallbacks.remove(recipientId)
      when (previousCallback) {
        is IDeviceCallback ->
          callbackExecutor.execute {
            previousCallback.onDeviceError(
              connectedDevice,
              DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED
            )
          }
        is ISafeDeviceCallback ->
          callbackExecutor.execute {
            previousCallback.onDeviceError(
              connectedDevice.deviceId,
              DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED
            )
          }
      }
      return false
    }

    logd(
      TAG,
      "New callback registered on device ${connectedDevice.deviceId} for recipient $recipientId."
    )
    @Suppress("UNCHECKED_CAST") // Cast will always succeed because of the type check above.
    (recipientCallbacks as? MutableMap<ParcelUuid, IInterface>)?.put(recipientId, callback)
    return true
  }

  private fun notifyOfMissedMessages(
    connectedDevice: ConnectedDevice,
    recipientId: ParcelUuid,
    callback: IInterface
  ) {
    val missedMessages = recipientMissedMessages[recipientId]?.remove(connectedDevice.deviceId)
    if (missedMessages?.isNotEmpty() != true) {
      return
    }
    logd(TAG, "Notifying $recipientId of missed messages.")
    when (callback) {
      is IDeviceCallback ->
        callbackExecutor.execute {
          for (deviceMessage in missedMessages) {
            callback.onMessageReceived(connectedDevice, deviceMessage)
          }
        }
      is ISafeDeviceCallback -> {
        for (deviceMessage in missedMessages) {
          val builder =
            DeviceMessageProto.Message.newBuilder()
              .setOperation(
                OperationType.forNumber(deviceMessage.operationType.value)
                  ?: OperationType.OPERATION_TYPE_UNKNOWN
              )
              .setIsPayloadEncrypted(deviceMessage.isMessageEncrypted)
              .setPayload(ByteString.copyFrom(deviceMessage.message))
              .setOriginalSize(deviceMessage.originalMessageSize)
          deviceMessage.recipient?.let {
            builder.recipient = ByteString.copyFrom(ByteUtils.uuidToBytes(it))
          }
          val message = builder.build()
          val rawBytes = message.toByteArray()
          callbackExecutor.execute {
            callback.onMessageReceived(connectedDevice.deviceId, rawBytes)
          }
        }
      }
      else ->
        logd(
          TAG,
          "Attempted to use unsupported callback type. Request to notify of missed messsages ignored."
        )
    }
  }

  override fun unregisterDeviceCallback(
    connectedDevice: ConnectedDevice,
    recipientId: ParcelUuid,
    callback: IDeviceCallback
  ) {
    lock.withLock {
      unregisterDeviceCallbackLocked(connectedDevice.deviceId, recipientId, callback)
    }
  }

  /**
   * Unregisters the given [callback] from being notified of device events for the specified
   * [recipientId] on the connected device with ID [deviceId].
   *
   * The caller should ensure that they have acquired [lock].
   */
  @GuardedBy("lock")
  private fun unregisterDeviceCallbackLocked(
    deviceId: String,
    recipientId: ParcelUuid,
    callback: IInterface
  ) {
    val deviceCallback =
      when (callback) {
        is IDeviceCallback -> deviceCallbacks[deviceId]?.get(recipientId)
        is ISafeDeviceCallback -> safeDeviceCallbacks[deviceId]?.get(recipientId)
        else -> {
          logd(
            TAG,
            "Attempted to use unsupported callback type. Request to unregister callback ignored."
          )
          return
        }
      }

    if (deviceCallback == null || callback.asBinder() != deviceCallback.asBinder()) {
      logw(
        TAG,
        "Request to unregister callback on device ${deviceId} for recipient $recipientId, but " +
          "this callback is not registered. Request to unregister callback ignored."
      )
      return
    }

    when (callback) {
      is IDeviceCallback -> deviceCallbacks[deviceId]?.remove(recipientId)
      is ISafeDeviceCallback -> safeDeviceCallbacks[deviceId]?.remove(recipientId)
      else -> {
        logd(
          TAG,
          "Attempted to use unsupported callback type. Request to unregister callback ignored."
        )
        return
      }
    }

    logd(TAG, "Device callback unregistered on device ${deviceId} for recipient " + "$recipientId.")
  }

  override fun sendMessage(connectedDevice: ConnectedDevice, message: DeviceMessage): Boolean {
    val cachedResponse = systemQueryCache.getCachedResponse(connectedDevice, message)
    if (cachedResponse != null) {
      // If a system query has a cached answer, short-circuit the query/response flow by faking a
      // response. Using the cached response allows us to speed up queries by features when the
      // response time is limited (e.g. time for SecondDeviceSignInUrlFeature to be "ready").
      //
      // Schedule the response callback on a different executor to avoid the callback is delivered
      // before this sendMessage method completes/returns.
      callbackExecutor.execute {
        onMessageReceivedInternal(connectedDevice, cachedResponse, shouldCacheMessage = false)
      }
      return true
    }
    return controller.sendMessage(UUID.fromString(connectedDevice.deviceId), message)
  }

  override fun registerDeviceAssociationCallback(callback: IDeviceAssociationCallback) {
    deviceAssociationCallbacks.add(callback, callbackExecutor)
  }

  override fun unregisterDeviceAssociationCallback(callback: IDeviceAssociationCallback) {
    deviceAssociationCallbacks.remove(callback)
  }

  override fun registerOnLogRequestedListener(
    loggerId: Int,
    listener: ISafeOnLogRequestedListener
  ) {
    loggingManager.registerLogRequestedListener(loggerId, listener, callbackExecutor)
  }

  override fun unregisterOnLogRequestedListener(
    loggerId: Int,
    listener: ISafeOnLogRequestedListener
  ) {
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
    logd(TAG, "Claiming device $deviceId. Updating storage and disconnecting.")
    controller.disconnectDevice(UUID.fromString(deviceId))
    storage.claimAssociatedDevice(deviceId)
    controller.initiateConnectionToDevice(UUID.fromString(deviceId))
  }

  override fun removeAssociatedDeviceClaim(deviceId: String) {
    logd(TAG, "Removing claim on device $deviceId. Updating storage and disconnecting.")
    controller.disconnectDevice(UUID.fromString(deviceId))
    storage.removeAssociatedDeviceClaim(deviceId)
    controller.initiateConnectionToDevice(UUID.fromString(deviceId))
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
    // Passenger mode is currently not supported for external features, therefore some callbacks
    // may be missed in the above invocations. Will need the following invocations until passenger
    // mode becomes officially supported.
    allConnectionCallbacks.invoke { it.onDeviceConnected(connectedDevice) }
    safeConnectionCallbacks.invoke { it.onDeviceConnected(connectedDevice.deviceId) }
  }

  @VisibleForTesting
  internal fun onDeviceDisconnectedInternal(connectedDevice: ConnectedDevice) {
    systemQueryCache.clearCache(connectedDevice)
    if (connectedDevice.isAssociatedWithDriver) {
      logd(TAG, "Notifying callbacks that a device has disconnected for the driver.")
      driverConnectionCallbacks.invoke { it.onDeviceDisconnected(connectedDevice) }
    } else {
      logd(TAG, "Notifying callbacks that a device has disconnected for a passenger.")
      passengerConnectionCallbacks.invoke { it.onDeviceDisconnected(connectedDevice) }
    }
    // Passenger mode is currently not supported for external features, therefore some callbacks
    // may be missed in the above invocations. Will need the following invocations until passenger
    // mode becomes officially supported.
    allConnectionCallbacks.invoke { it.onDeviceDisconnected(connectedDevice) }
    safeConnectionCallbacks.invoke { it.onDeviceDisconnected(connectedDevice.deviceId) }
    // Clear blocked recipients for the next connection so the state is easier to recover.
    lock.withLock { blockedRecipients.clear() }
  }

  @VisibleForTesting
  internal fun onSecureChannelEstablishedInternal(connectedDevice: ConnectedDevice) {
    val callbacks = lock.withLock { deviceCallbacks[connectedDevice.deviceId]?.values }
    val safeCallbacks = lock.withLock { safeDeviceCallbacks[connectedDevice.deviceId]?.values }
    if (callbacks == null && safeCallbacks == null) {
      logd(
        TAG,
        "A secure channel has been established with ${connectedDevice.deviceId}, but no " +
          "callbacks registered to be notified."
      )
      return
    }
    logd(
      TAG,
      "Notifying callbacks that a secure channel has been established with " +
        "${connectedDevice.deviceId}."
    )
    if (callbacks != null) {
      for (callback in callbacks) {
        callbackExecutor.execute { callback.onSecureChannelEstablished(connectedDevice) }
      }
    }
    if (safeCallbacks != null) {
      for (safeCallback in safeCallbacks) {
        callbackExecutor.execute {
          safeCallback.onSecureChannelEstablished(connectedDevice.deviceId)
        }
      }
    }
  }

  @VisibleForTesting
  internal fun onMessageReceivedInternal(
    connectedDevice: ConnectedDevice,
    message: DeviceMessage,
    shouldCacheMessage: Boolean = true,
  ) {
    if (shouldCacheMessage) {
      // Cache the received message for a faster response if queried again by another feature.
      systemQueryCache.maybeCacheResponse(connectedDevice, message)
    }

    if (message.recipient == null) {
      loge(
        TAG,
        "Received callback for a new message containing no recipient. No callbacks were invoked!"
      )
      return
    }

    logd(TAG, "Received a new message for ${message.recipient} from ${connectedDevice.deviceId}.")

    val callback =
      lock.withLock {
        deviceCallbacks[connectedDevice.deviceId]?.get(ParcelUuid(message.recipient))
      }
    val safeCallback =
      lock.withLock {
        safeDeviceCallbacks[connectedDevice.deviceId]?.get(ParcelUuid(message.recipient))
      }

    if (callback == null && safeCallback == null) {
      logd(TAG, "Recipient has not registered a callback yet. Saving missed message.")
      saveMissedMessage(connectedDevice, message)
      return
    }

    logd(TAG, "Notifying callback for recipient ${message.recipient}")

    callbackExecutor.execute {
      callback?.onMessageReceived(connectedDevice, message)
      safeCallback?.onMessageReceived(connectedDevice.deviceId, message.message)
    }
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
