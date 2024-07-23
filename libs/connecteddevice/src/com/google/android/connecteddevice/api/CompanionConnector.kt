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
package com.google.android.connecteddevice.api

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.os.RemoteException
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import com.google.android.companionprotos.FeatureSupportResponse
import com.google.android.companionprotos.Query
import com.google.android.companionprotos.QueryResponse
import com.google.android.companionprotos.SystemQuery
import com.google.android.companionprotos.SystemQueryType
import com.google.android.connecteddevice.api.Connector.AppNameCallback
import com.google.android.connecteddevice.api.Connector.Companion.ACTION_BIND_FEATURE_COORDINATOR
import com.google.android.connecteddevice.api.Connector.Companion.ACTION_BIND_FEATURE_COORDINATOR_FG
import com.google.android.connecteddevice.api.Connector.Companion.SYSTEM_FEATURE_ID
import com.google.android.connecteddevice.api.Connector.Companion.USER_TYPE_ALL
import com.google.android.connecteddevice.api.Connector.Companion.USER_TYPE_DRIVER
import com.google.android.connecteddevice.api.Connector.Companion.USER_TYPE_PASSENGER
import com.google.android.connecteddevice.api.Connector.Companion.UserType
import com.google.android.connecteddevice.api.Connector.QueryCallback
import com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener
import com.google.android.connecteddevice.model.AssociatedDevice
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.util.ByteUtils
import com.google.android.connecteddevice.util.Logger
import com.google.android.connecteddevice.util.SafeLog
import com.google.android.connecteddevice.util.aliveOrNull
import com.google.common.util.concurrent.ListenableFuture
import com.google.protobuf.ByteString
import com.google.protobuf.ExtensionRegistryLite
import com.google.protobuf.InvalidProtocolBufferException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Class for establishing and maintaining a connection to the companion device platform.
 *
 * This connector will return different binders based on binding actions. Only foreground process
 * should register listeners for feature coordinator initialization.
 *
 * @param context [Context] of the hosting process.
 * @param isForegroundProcess Set to `true` if running from outside of the companion application.
 * @param userType Filter devices to the matching [UserType].
 */
class CompanionConnector
@JvmOverloads
constructor(
  private val context: Context,
  private val isForegroundProcess: Boolean = false,
  private val userType: @UserType Int = USER_TYPE_DRIVER,
) : Connector {
  private val lock = ReentrantLock()

  private val retryHandler = Handler(Looper.getMainLooper())

  private val loggerId = Logger.getLogger().loggerId

  private val isPlatformInitialized = AtomicBoolean(false)

  private val waitingForConnection = AtomicBoolean(false)

  private val featureCoordinatorAction =
    if (isForegroundProcess) {
      ACTION_BIND_FEATURE_COORDINATOR_FG
    } else {
      ACTION_BIND_FEATURE_COORDINATOR
    }

  private val featureCoordinatorConnection =
    object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName, service: IBinder) {
        if (isForegroundProcess) {
          handleForegroundUserConnection(service)
        } else {
          handleFeatureCoordinatorConnection(service)
        }
      }

      override fun onServiceDisconnected(name: ComponentName) {
        this@CompanionConnector.onServiceDisconnected()
      }

      override fun onNullBinding(name: ComponentName) {
        this@CompanionConnector.onNullBinding()
      }

      override fun onBindingDied(name: ComponentName?) {
        this@CompanionConnector.onBindingDied()
      }
    }

  private val connectionCallback =
    object : IConnectionCallback.Stub() {
      override fun onDeviceConnected(device: ConnectedDevice) {
        logd("Device ${device.deviceId} has connected. Notifying callback.")
        callback?.onDeviceConnected(device)
        if (device.hasSecureChannel()) {
          callback?.onSecureChannelEstablished(device)
        }
        val featureId = featureId ?: return
        logd("Registering device callback for $featureId on device ${device.deviceId}.")

        // Only call method once. If featureCoordinator is not null, connectedDeviceManager is a
        // wrapper and will result in a double call on featureCoordinator.
        aliveFeatureCoordinator?.registerDeviceCallback(device, featureId, deviceCallback)
      }

      override fun onDeviceDisconnected(device: ConnectedDevice) {
        logd("Device ${device.deviceId} has disconnected. Notifying callback.")
        callback?.onDeviceDisconnected(device)
        val featureId = featureId ?: return
        logd("Unregistering device callback for $featureId on device ${device.deviceId}.")

        // Only call method once. If featureCoordinator is not null, connectedDeviceManager is a
        // wrapper and will result in a double call on featureCoordinator.
        aliveFeatureCoordinator?.unregisterDeviceCallback(device, featureId, deviceCallback)
      }
    }

  private val deviceCallback =
    object : IDeviceCallback.Stub() {
      override fun onSecureChannelEstablished(device: ConnectedDevice) {
        logd("Secure channel has been established on ${device.deviceId}. Notifying callback.")
        callback?.onSecureChannelEstablished(device)
      }

      override fun onMessageReceived(device: ConnectedDevice, message: DeviceMessage) {
        processIncomingMessage(device, message)
      }

      override fun onDeviceError(device: ConnectedDevice, error: Int) {
        logw("Received a device error of $error from ${device.deviceId}.")
        callback?.onDeviceError(device, error)
      }
    }

  private val deviceAssociationCallback =
    object : IDeviceAssociationCallback.Stub() {
      override fun onAssociatedDeviceAdded(device: AssociatedDevice) {
        logd("New device ${device.deviceId} associated. Notifying callback.")
        callback?.onAssociatedDeviceAdded(device)
      }

      override fun onAssociatedDeviceRemoved(device: AssociatedDevice) {
        logd("Associated device ${device.deviceId} removed. Notifying callback.")
        callback?.onAssociatedDeviceRemoved(device)
      }

      override fun onAssociatedDeviceUpdated(device: AssociatedDevice) {
        logd("Associated device ${device.deviceId} updated. Notifying callback.")
        callback?.onAssociatedDeviceUpdated(device)
      }
    }

  private val logRequestedListener =
    object : ISafeOnLogRequestedListener.Stub() {
      override fun onLogRecordsRequested() {
        val loggerBytes = Logger.getLogger().toByteArray()
        try {
          featureCoordinator?.processLogRecords(loggerId, loggerBytes)
        } catch (e: RemoteException) {
          loge("Failed to send log records for logger $loggerId.", e)
        }
      }
    }

  private val queryIdGenerator = QueryIdGenerator()

  // queryId -> callback
  private val queryCallbacks: MutableMap<Int, QueryCallback> = ConcurrentHashMap()

  // queryId -> original sender for response
  private val queryResponseRecipients: MutableMap<Int, ParcelUuid> = ConcurrentHashMap()

  private var bindAttempts = 0

  // Binder returned for foreground users. Allow binding services/activities to register listeners
  // and get notified when feature coordinator is initialized.
  @VisibleForTesting
  internal val foregroundUserBinder =
    object : IFeatureCoordinatorStatusNotifier.Stub() {
      override fun registerFeatureCoordinatorListener(listener: IFeatureCoordinatorListener) {
        logd("Register feature coordinator listener.")
        lock.withLock { registeredListeners.add(listener) }
        if (isConnected) {
          logd("Feature coordinator has already been initialized, notifying listeners.")
          notifyFeatureCoordinatorListeners()
        }
      }

      override fun unregisterFeatureCoordinatorListener(listener: IFeatureCoordinatorListener) {
        logd("Unregister feature coordinator listener.")
        lock.withLock { registeredListeners.removeIf { it.asBinder() == listener.asBinder() } }
      }
    }

  @VisibleForTesting
  internal var featureCoordinatorListener =
    object : IFeatureCoordinatorListener.Stub() {
      override fun onFeatureCoordinatorInitialized(featureCoordinator: IFeatureCoordinator) {
        if (!isConnected) {
          this@CompanionConnector.featureCoordinator = featureCoordinator
          onServiceConnected()
        }
      }
    }

  @VisibleForTesting
  @GuardedBy("lock")
  internal var registeredListeners = mutableListOf<IFeatureCoordinatorListener>()

  // Binder returned for system users. Allow binding services to access ConnectedDeviceService's
  // feature coordinator.
  @VisibleForTesting internal var featureCoordinator: IFeatureCoordinator? = null

  @VisibleForTesting
  internal var featureCoordinatorStatusNotifier: IFeatureCoordinatorStatusNotifier? = null

  override var featureId: ParcelUuid? = null

  override var callback: Connector.Callback? = null

  override val connectedDevices: List<ConnectedDevice>
    get() {
      if (!isConnected) {
        logw(
          "Attempted to get connected devices before the platform was connected. Returning empty " +
            "list."
        )
        return emptyList()
      }
      return when (userType) {
        USER_TYPE_DRIVER -> aliveFeatureCoordinator?.connectedDevicesForDriver
        USER_TYPE_PASSENGER -> aliveFeatureCoordinator?.connectedDevicesForPassengers
        USER_TYPE_ALL -> aliveFeatureCoordinator?.allConnectedDevices
        else -> null
      } ?: emptyList()
    }

  override val isConnected: Boolean
    get() = aliveFeatureCoordinator != null

  private val isBound: Boolean
    get() = featureCoordinator != null

  private val aliveFeatureCoordinator
    get() = featureCoordinator?.aliveOrNull()

  private val aliveFeatureCoordinatorStatusNotifier
    get() = featureCoordinatorStatusNotifier?.aliveOrNull()

  override fun connect() {
    logd("Initiating connection to companion platform.")
    if (isConnected) {
      logd("Platform is already connected. Skipping binding.")
      initializePlatform()
      return
    }
    if (waitingForConnection.compareAndSet(false, true)) {
      bindAttempts = 0
      bindToService()
    }
  }

  private fun bindToService() {
    logd("Platform is not currently connected. Initiating binding.")
    val intent = resolveIntent(featureCoordinatorAction)

    if (intent == null) {
      loge("No services found supporting companion device. Aborting.")
      callback?.onFailedToConnect()
      return
    }

    val success = context.bindService(intent, featureCoordinatorConnection, /* flag= */ 0)
    if (success) {
      logd("Successfully started binding with ${intent.action}.")
      return
    }

    bindAttempts++
    if (bindAttempts > MAX_BIND_ATTEMPTS) {
      loge("Failed to bind to service after $bindAttempts attempts. Aborting.")
      waitingForConnection.set(false)
      callback?.onFailedToConnect()
      return
    }
    logw("Unable to bind to service with action ${intent.action}. Trying again.")
    retryHandler.postDelayed(::bindToService, BIND_RETRY_DURATION.toMillis())
  }

  override fun disconnect() {
    logd("Disconnecting from the companion platform.")
    val wasConnected = isBound
    logd("FeatureCoordinator is null: ${featureCoordinator == null} isBound: $isBound")
    unbindFromService()
    if (wasConnected) {
      callback?.onDisconnected()
    }
  }

  private fun unbindFromService() {
    cleanUpFeatureCoordinator()
    cleanUpFeatureCoordinatorStatusNotifier()
    retryHandler.removeCallbacksAndMessages(/* token= */ null)
    try {
      context.unbindService(featureCoordinatorConnection)
    } catch (e: IllegalArgumentException) {
      logw("Attempted to unbind an already unbound service.")
    }
    waitingForConnection.set(false)
  }

  private fun cleanUpFeatureCoordinator() {
    logd("Cleaning up FeatureCoordinator.")
    aliveFeatureCoordinator?.let {
      try {
        it.unregisterConnectionCallback(connectionCallback)
        val featureId = featureId
        if (featureId != null) {
          for (device in it.allConnectedDevices) {
            it.unregisterDeviceCallback(device, featureId, deviceCallback)
          }
        }
        it.unregisterDeviceAssociationCallback(deviceAssociationCallback)
        it.unregisterOnLogRequestedListener(loggerId, logRequestedListener)
      } catch (e: RemoteException) {
        loge("Error while cleaning up FeatureCoordinator.", e)
      }
    }
    // Only set to null if already non-null. Otherwise, this will also inadvertently null out the
    // live connectedDeviceManager as well leaving us in a state where callbacks cannot be properly
    // unregistered.
    if (featureCoordinator != null) {
      featureCoordinator = null
    }
    isPlatformInitialized.set(false)
  }

  private fun cleanUpFeatureCoordinatorStatusNotifier() {
    logd("Clean up feature coordinator status notifier")
    aliveFeatureCoordinatorStatusNotifier?.unregisterFeatureCoordinatorListener(
      featureCoordinatorListener
    )
  }

  override fun binderForAction(action: String): IBinder? {
    logd("Binder for action: $action.")
    return when (action) {
      ACTION_BIND_FEATURE_COORDINATOR -> aliveFeatureCoordinator?.asBinder()
      ACTION_BIND_FEATURE_COORDINATOR_FG -> foregroundUserBinder.asBinder()
      else -> {
        loge("Binder for unexpected action, returning null binder.")
        null
      }
    }
  }

  override fun sendMessageSecurely(deviceId: String, message: ByteArray) {
    if (!isConnected) {
      loge("Unable to send message, the platform is not actively connected.")
      callback?.onMessageFailedToSend(deviceId, message, isTransient = true)
      return
    }
    val device = getConnectedDeviceById(deviceId)
    if (device == null) {
      loge("No matching device found with id $deviceId when trying to send secure message.")
      callback?.onMessageFailedToSend(deviceId, message, isTransient = false)
      return
    }
    sendMessageSecurely(device, message)
  }

  override fun sendMessageSecurely(device: ConnectedDevice, message: ByteArray) {
    if (!isConnected) {
      loge("Unable to send message, the platform is not actively connected.")
      callback?.onMessageFailedToSend(device.deviceId, message, isTransient = true)
      return
    }
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        featureId?.uuid,
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.CLIENT_MESSAGE,
        message,
      )
    try {
      sendMessageInternal(device, deviceMessage)
    } catch (e: RemoteException) {
      loge("Error while sending secure message.", e)
      callback?.onMessageFailedToSend(device.deviceId, message, isTransient = false)
    }
  }

  override fun sendQuerySecurely(
    deviceId: String,
    request: ByteArray,
    parameters: ByteArray?,
    callback: QueryCallback,
  ) {
    if (!isConnected) {
      loge("Unable to send query, the platform is not actively connected.")
      callback.onQueryFailedToSend(isTransient = true)
      return
    }
    val device = getConnectedDeviceById(deviceId)
    if (device == null) {
      loge("No matching device found with id $deviceId when trying to send a secure query.")
      callback.onQueryFailedToSend(isTransient = false)
      return
    }
    sendQuerySecurely(device, request, parameters, callback)
  }

  override fun isFeatureSupportedCached(device: ConnectedDevice): Boolean? {
    val featureId = featureId?.uuid?.toString() ?: return null
    val status =
      try {
        aliveFeatureCoordinator?.isFeatureSupportedCached(device.deviceId, featureId) ?: return null
      } catch (e: Exception) {
        // If the companion AIDL doesn't have an updated version, calling a non-existent method
        // could throw exception. This check is always executed so we catch a generic Exception to
        // be safe.
        // Return 0 to indicate unknown status.
        0
      }
    return when {
      status > 0 -> true
      status < 0 -> false
      // Status unknown - returns 0; using `else` so `when` is exhausive.
      else -> null
    }
  }

  override suspend fun isFeatureSupported(device: ConnectedDevice): Boolean {
    val queriedFeatureId = featureId?.uuid ?: return false
    val status =
      queryFeatureSupportStatuses(device, listOf(queriedFeatureId)).firstOrNull {
        it.first == queriedFeatureId
      }
    return status?.second ?: false
  }

  override suspend fun queryFeatureSupportStatuses(
    device: ConnectedDevice,
    queriedFeatures: List<UUID>,
  ): List<Pair<UUID, Boolean>> {
    val payloads =
      queriedFeatures.map {
        logd("Batch querying support status for feature $it.")
        ByteString.copyFrom(it.toString().toByteArray(StandardCharsets.UTF_8))
      }

    val systemQuery =
      SystemQuery.newBuilder().run {
        setType(SystemQueryType.IS_FEATURE_SUPPORTED)
        addAllPayloads(payloads)
        build()
      }

    return suspendCancellableCoroutine<List<Pair<UUID, Boolean>>> { continuation ->
      sendQuerySecurelyInternal(
        device,
        SYSTEM_FEATURE_ID,
        systemQuery.toByteArray(),
        parameters = null,
        object : QueryCallback {
          override fun onSuccess(response: ByteArray) {
            if (response.isEmpty()) {
              loge("Received an empty response for feature support query.")
              continuation.resume(emptyList())
              return
            }

            val supportResponse =
              try {
                FeatureSupportResponse.parseFrom(response)
              } catch (e: InvalidProtocolBufferException) {
                loge("Could not parse query response as proto.", e)
                continuation.resume(emptyList())
                return
              }
            val statuses =
              supportResponse.statusesList.map { status ->
                Pair(UUID.fromString(status.featureId), status.isSupported)
              }
            continuation.resume(statuses)
          }

          override fun onError(response: ByteArray) {
            loge("Received an error response when querying for feature support.")
            continuation.resume(emptyList())
          }

          override fun onQueryFailedToSend(isTransient: Boolean) {
            loge("Failed to send the query for the feature support status.")
            continuation.resume(emptyList())
          }
        },
      )
    }
  }

  override fun isFeatureSupportedFuture(device: ConnectedDevice): ListenableFuture<Boolean> {
    return CoroutineScope(Dispatchers.Main).future { isFeatureSupported(device) }
  }

  override fun sendQuerySecurely(
    device: ConnectedDevice,
    request: ByteArray,
    parameters: ByteArray?,
    callback: QueryCallback,
  ) {
    val featureId = featureId
    if (featureId == null) {
      loge("Attempted to send a query with no feature id.")
      callback.onQueryFailedToSend(isTransient = false)
      return
    }
    sendQuerySecurelyInternal(device, featureId, request, parameters, callback)
  }

  private fun sendQuerySecurelyInternal(
    device: ConnectedDevice,
    recipient: ParcelUuid,
    request: ByteArray,
    parameters: ByteArray?,
    callback: QueryCallback,
  ) {
    if (!isConnected) {
      loge("Unable to send message, the platform is not actively connected.")
      callback.onQueryFailedToSend(isTransient = true)
      return
    }
    val featureId = featureId
    if (featureId == null) {
      loge("Attempted to send a query with no feature id.")
      callback.onQueryFailedToSend(isTransient = false)
      return
    }
    val id = queryIdGenerator.next()
    val builder =
      Query.newBuilder()
        .setId(id)
        .setSender(ByteString.copyFrom(ByteUtils.uuidToBytes(featureId.uuid)))
        .setRequest(ByteString.copyFrom(request))
    if (parameters != null) {
      builder.parameters = ByteString.copyFrom(parameters)
    }
    logd("Sending secure query with id $id.")
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        recipient.uuid,
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.QUERY,
        builder.build().toByteArray(),
      )
    try {
      sendMessageInternal(device, deviceMessage)
    } catch (e: RemoteException) {
      loge("Error while sending secure query.", e)
      callback.onQueryFailedToSend(isTransient = false)
      return
    }
    queryCallbacks[id] = callback
  }

  override fun respondToQuerySecurely(
    device: ConnectedDevice,
    queryId: Int,
    success: Boolean,
    response: ByteArray?,
  ) {
    if (!isConnected) {
      loge("Unable to send query response, the platform is not actively connected.")
      return
    }
    val recipientId: ParcelUuid? = queryResponseRecipients.remove(queryId)
    if (recipientId == null) {
      loge("Unable to send response to unrecognized query $queryId.")
      return
    }
    val builder = QueryResponse.newBuilder().setQueryId(queryId).setSuccess(success)
    if (response != null) {
      builder.response = ByteString.copyFrom(response)
    }
    val queryResponse = builder.build()
    logd("Sending response to query $queryId to $recipientId.")
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        recipientId.uuid,
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.QUERY_RESPONSE,
        queryResponse.toByteArray(),
      )
    try {
      sendMessageInternal(device, deviceMessage)
    } catch (e: RemoteException) {
      loge("Error while sending query response.", e)
    }
  }

  override fun getConnectedDeviceById(deviceId: String): ConnectedDevice? {
    if (!isConnected) {
      loge("Unable to get connected device by id. The platform is not actively connected.")
      return null
    }
    val connectedDevices =
      try {
        aliveFeatureCoordinator?.allConnectedDevices
      } catch (e: RemoteException) {
        loge("Exception while retrieving connected devices.", e)
        null
      }

    return connectedDevices?.find { it.deviceId == deviceId }
  }

  override fun retrieveCompanionApplicationName(
    device: ConnectedDevice,
    callback: AppNameCallback,
  ) {
    val systemQuery = SystemQuery.newBuilder().setType(SystemQueryType.APP_NAME).build()
    sendQuerySecurelyInternal(
      device,
      SYSTEM_FEATURE_ID,
      systemQuery.toByteArray(),
      parameters = null,
      object : QueryCallback {
        override fun onSuccess(response: ByteArray) {
          if (response == null || response.isEmpty()) {
            loge("Received a null or empty response for the application name.")
            callback.onError()
            return
          }
          val appName = String(response, StandardCharsets.UTF_8)
          logd("Received successful app name query response of $appName.")
          callback.onNameReceived(appName)
        }

        override fun onError(response: ByteArray) {
          loge("Received an error response when querying for application name.")
          callback.onError()
        }

        override fun onQueryFailedToSend(isTransient: Boolean) {
          loge("Failed to send the query for the application name.")
          callback.onError()
        }
      },
    )
  }

  override fun startAssociation(callback: IAssociationCallback) {
    aliveFeatureCoordinator?.startAssociation(callback)
  }

  override fun startAssociation(identifier: ParcelUuid, callback: IAssociationCallback) {
    aliveFeatureCoordinator?.startAssociationWithIdentifier(callback, identifier)
  }

  override fun stopAssociation() {
    aliveFeatureCoordinator?.stopAssociation()
  }

  override fun acceptVerification() {
    aliveFeatureCoordinator?.acceptVerification()
  }

  override fun removeAssociatedDevice(deviceId: String) {
    aliveFeatureCoordinator?.removeAssociatedDevice(deviceId)
  }

  override fun enableAssociatedDeviceConnection(deviceId: String) {
    aliveFeatureCoordinator?.enableAssociatedDeviceConnection(deviceId)
  }

  override fun disableAssociatedDeviceConnection(deviceId: String) {
    aliveFeatureCoordinator?.disableAssociatedDeviceConnection(deviceId)
  }

  override fun retrieveAssociatedDevices(listener: IOnAssociatedDevicesRetrievedListener) {
    aliveFeatureCoordinator?.retrieveAssociatedDevices(listener)
  }

  override fun retrieveAssociatedDevicesForDriver(listener: IOnAssociatedDevicesRetrievedListener) {
    aliveFeatureCoordinator?.retrieveAssociatedDevicesForDriver(listener)
  }

  override fun retrieveAssociatedDevicesForPassengers(
    listener: IOnAssociatedDevicesRetrievedListener
  ) {
    aliveFeatureCoordinator?.retrieveAssociatedDevicesForPassengers(listener)
      ?: listener.onAssociatedDevicesRetrieved(emptyList())
  }

  override fun claimAssociatedDevice(deviceId: String) {
    aliveFeatureCoordinator?.claimAssociatedDevice(deviceId)
  }

  override fun removeAssociatedDeviceClaim(deviceId: String) {
    aliveFeatureCoordinator?.removeAssociatedDeviceClaim(deviceId)
  }

  private fun sendMessageInternal(device: ConnectedDevice, message: DeviceMessage) {
    aliveFeatureCoordinator?.sendMessage(device, message)
  }

  private fun onServiceConnected() {
    if (!isConnected) {
      logd(
        "Initialization criteria have not been met yet. Waiting for further service connections " +
          "before starting."
      )
      return
    }
    initializePlatform()
  }

  private fun handleFeatureCoordinatorConnection(service: IBinder) {
    logd("Feature coordinator binder connected.")
    featureCoordinator =
      checkNotNull(IFeatureCoordinator.Stub.asInterface(service)) {
        "Cannot create wrapper of a null feature coordinator."
      }
    logd("Feature coordinator is alive: $isConnected")
    waitingForConnection.set(false)
    onServiceConnected()
    notifyFeatureCoordinatorListeners()
  }

  private fun handleForegroundUserConnection(service: IBinder) {
    logd("Foreground user service connection connected.")
    try {
      val featureCoordinatorStatusNotifier =
        IFeatureCoordinatorStatusNotifier.Stub.asInterface(service)
      featureCoordinatorStatusNotifier.registerFeatureCoordinatorListener(
        featureCoordinatorListener
      )
      this@CompanionConnector.featureCoordinatorStatusNotifier = featureCoordinatorStatusNotifier
    } catch (e: SecurityException) {
      loge("Incompatible binder, invoking failure callback.", e)
      callback?.onFailedToConnect()
      unbindFromService()
      return
    }
    waitingForConnection.set(false)
    onServiceConnected()
  }

  private fun initializePlatform() {
    if (!isPlatformInitialized.compareAndSet(false, true)) {
      logw("Platform is already initialized. Ignoring.")
      return
    }
    logd("Initializing FeatureCoordinator.")
    val featureCoordinator = featureCoordinator
    if (featureCoordinator == null) {
      logd("Unable to initialize null FeatureCoordinator. Ignoring.")
      return
    }
    try {
      val connectedDevices: List<ConnectedDevice>
      when (userType) {
        USER_TYPE_DRIVER -> {
          featureCoordinator.registerDriverConnectionCallback(connectionCallback)
          connectedDevices = featureCoordinator.connectedDevicesForDriver
        }
        USER_TYPE_PASSENGER -> {
          featureCoordinator.registerPassengerConnectionCallback(connectionCallback)
          connectedDevices = featureCoordinator.connectedDevicesForPassengers
        }
        USER_TYPE_ALL -> {
          featureCoordinator.registerAllConnectionCallback(connectionCallback)
          connectedDevices = featureCoordinator.allConnectedDevices
        }
        else -> {
          loge("Unknown user type $userType detected.")
          connectedDevices = emptyList()
        }
      }
      featureCoordinator.registerDeviceAssociationCallback(deviceAssociationCallback)
      featureCoordinator.registerOnLogRequestedListener(loggerId, logRequestedListener)
      val featureId = featureId
      for (device in connectedDevices) {
        callback?.onDeviceConnected(device)
        if (device.hasSecureChannel()) {
          callback?.onSecureChannelEstablished(device)
        }
        if (featureId == null) {
          logd("There is currently no feature id set. Skipping device registration.")
          continue
        }
        featureCoordinator.registerDeviceCallback(device, featureId, deviceCallback)
      }
    } catch (e: RemoteException) {
      loge("Error while initializing FeatureCoordinator.", e)
    }
    callback?.onConnected()
  }

  private fun onServiceDisconnected() {
    logd("Service has disconnected. Cleaning up.")
    disconnect()
  }

  private fun onNullBinding() {
    loge("Received a null binding for FeatureCoordinator. Unbinding service.")
    unbindFromService()
    callback?.onFailedToConnect()
  }

  private fun onBindingDied() {
    logw("FeatureCoordinator binding died. Unbinding service.")
    unbindFromService()
    callback?.onDisconnected()
  }

  private fun resolveIntent(action: String): Intent? {
    val packageManager = context.packageManager
    val intent = Intent(action)
    val services = packageManager.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY)
    if (services.isEmpty()) {
      logw("There are no services supporting the $action action installed on this device.")
      return null
    }
    logd("Found ${services.size} service(s) supporting $action. Choosing the first one.")
    val service = services[0]
    return intent.apply {
      component = ComponentName(service.serviceInfo.packageName, service.serviceInfo.name)
    }
  }

  private fun processIncomingMessage(device: ConnectedDevice, deviceMessage: DeviceMessage) {
    val operationType = deviceMessage.operationType
    val message = deviceMessage.message
    when (operationType) {
      DeviceMessage.OperationType.CLIENT_MESSAGE -> {
        logd("Received client message. Passing on to feature.")
        callback?.onMessageReceived(device, message)
        return
      }
      DeviceMessage.OperationType.QUERY -> {
        try {
          val query = Query.parseFrom(message, ExtensionRegistryLite.getEmptyRegistry())
          processQuery(device, query)
        } catch (e: InvalidProtocolBufferException) {
          loge("Unable to parse query.", e)
        }
        return
      }
      DeviceMessage.OperationType.QUERY_RESPONSE -> {
        try {
          val response = QueryResponse.parseFrom(message, ExtensionRegistryLite.getEmptyRegistry())
          processQueryResponse(response)
        } catch (e: InvalidProtocolBufferException) {
          loge("Unable to parse query response.", e)
        }
        return
      }
      else -> loge("Received unknown type of message: $operationType. Ignoring.")
    }
  }

  private fun processQuery(device: ConnectedDevice, query: Query) {
    logd("Received a new query with id ${query.id}. Passing on to feature.")
    val sender = ParcelUuid(ByteUtils.bytesToUUID(query.sender.toByteArray()))
    queryResponseRecipients[query.id] = sender
    callback?.onQueryReceived(
      device,
      query.id,
      query.request.toByteArray(),
      query.parameters.toByteArray(),
    )
  }

  private fun processQueryResponse(response: QueryResponse) {
    logd("Received a query response. Issuing registered callback.")
    val callback = queryCallbacks.remove(response.queryId)
    if (callback == null) {
      loge("Unable to locate callback for query ${response.queryId}.")
      return
    }
    if (response.success) {
      callback.onSuccess(response.response.toByteArray())
    } else {
      callback.onError(response.response.toByteArray())
    }
  }

  private fun notifyFeatureCoordinatorListeners() {
    logd("Feature coordinator has been initiated, notifying listeners.")
    scrubDeadListeners()
    lock.withLock {
      for (listener in registeredListeners) {
        listener.onFeatureCoordinatorInitialized(aliveFeatureCoordinator)
      }
    }
  }

  private fun scrubDeadListeners() {
    logd("Remove disconnected registered feature coordinator listeners")
    lock.withLock { registeredListeners.removeIf { !it.asBinder().isBinderAlive } }
  }

  private fun logd(message: String) {
    SafeLog.logd(TAG, "$message [Feature ID: $featureId]")
  }

  private fun logw(message: String) {
    SafeLog.logw(TAG, "$message [Feature ID: $featureId]")
  }

  private fun loge(message: String, e: Exception? = null) {
    SafeLog.loge(TAG, "$message [Feature ID: $featureId]", e)
  }

  companion object {
    private const val TAG = "CompanionConnector"

    private val BIND_RETRY_DURATION = Duration.ofSeconds(1)

    @VisibleForTesting internal const val MAX_BIND_ATTEMPTS = 3

    @JvmStatic
    /** Create a [CompanionConnector] instance with a [featureCoordinator] already populated. */
    fun createLocalConnector(
      context: Context,
      userType: @UserType Int,
      featureCoordinator: IFeatureCoordinator,
    ): CompanionConnector =
      CompanionConnector(context, userType = userType).apply {
        this.featureCoordinator = featureCoordinator
      }

    /** A generator of unique IDs for queries. */
    private class QueryIdGenerator {
      private val messageId = AtomicInteger(0)

      fun next(): Int {
        val current = messageId.getAndIncrement()
        messageId.compareAndSet(Int.MAX_VALUE, 0)
        return current
      }
    }
  }
}
