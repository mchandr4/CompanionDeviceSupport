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
import androidx.annotation.VisibleForTesting
import com.google.android.companionprotos.Query
import com.google.android.companionprotos.QueryResponse
import com.google.android.companionprotos.SystemQuery
import com.google.android.companionprotos.SystemQueryType
import com.google.android.connecteddevice.api.Connector.AppNameCallback
import com.google.android.connecteddevice.api.Connector.Companion.ACTION_BIND_FEATURE_COORDINATOR
import com.google.android.connecteddevice.api.Connector.Companion.ACTION_BIND_FEATURE_COORDINATOR_FG
import com.google.android.connecteddevice.api.Connector.Companion.ACTION_BIND_REMOTE_FEATURE
import com.google.android.connecteddevice.api.Connector.Companion.ACTION_BIND_REMOTE_FEATURE_FG
import com.google.android.connecteddevice.api.Connector.Companion.SYSTEM_FEATURE_ID
import com.google.android.connecteddevice.api.Connector.Companion.USER_TYPE_ALL
import com.google.android.connecteddevice.api.Connector.Companion.USER_TYPE_DRIVER
import com.google.android.connecteddevice.api.Connector.Companion.USER_TYPE_PASSENGER
import com.google.android.connecteddevice.api.Connector.Companion.UserType
import com.google.android.connecteddevice.api.Connector.QueryCallback
import com.google.android.connecteddevice.model.AssociatedDevice
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.util.ByteUtils
import com.google.android.connecteddevice.util.Logger
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logw
import com.google.android.connecteddevice.util.aliveOrNull
import com.google.protobuf.ByteString
import com.google.protobuf.ExtensionRegistryLite
import com.google.protobuf.InvalidProtocolBufferException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Class for establishing and maintaining a connection to the companion device platform.
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
  private val userType: @UserType Int = USER_TYPE_DRIVER
) : Connector {
  private val isLegacyOnly = AtomicBoolean(false)

  private val retryHandler = Handler(Looper.getMainLooper())

  private val loggerId = Logger.getLogger().loggerId

  private val featureCoordinatorAction =
    if (isForegroundProcess) {
      ACTION_BIND_FEATURE_COORDINATOR_FG
    } else {
      ACTION_BIND_FEATURE_COORDINATOR
    }

  private val connectedDeviceManagerAction =
    if (isForegroundProcess) {
      ACTION_BIND_REMOTE_FEATURE_FG
    } else {
      ACTION_BIND_REMOTE_FEATURE
    }

  private val serviceConnection =
    object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName, service: IBinder) {
        this@CompanionConnector.onServiceConnected(service)
      }

      override fun onServiceDisconnected(name: ComponentName) {
        this@CompanionConnector.onServiceDisconnected()
      }

      override fun onNullBinding(name: ComponentName) {
        this@CompanionConnector.onNullBinding()
      }
    }

  private val connectionCallback =
    object : IConnectionCallback.Stub() {
      override fun onDeviceConnected(device: ConnectedDevice) {
        logd(TAG, "Device ${device.deviceId} has connected. Notifying callback.")
        callback?.onDeviceConnected(device)
        if (device.hasSecureChannel()) {
          callback?.onSecureChannelEstablished(device)
        }
        val featureId = featureId ?: return
        logd(TAG, "Registering device callback for $featureId on device ${device.deviceId}.")

        // Only call method once. If featureCoordinator is not null, connectedDeviceManager is a
        // wrapper and will result in a double call on featureCoordinator.
        featureCoordinator?.registerDeviceCallback(device, featureId, deviceCallback)
          ?: connectedDeviceManager?.registerDeviceCallback(device, featureId, deviceCallback)
      }

      override fun onDeviceDisconnected(device: ConnectedDevice) {
        logd(TAG, "Device ${device.deviceId} has disconnected. Notifying callback.")
        callback?.onDeviceDisconnected(device)
        val featureId = featureId ?: return
        logd(TAG, "Unregistering device callback for $featureId on device ${device.deviceId}.")

        // Only call method once. If featureCoordinator is not null, connectedDeviceManager is a
        // wrapper and will result in a double call on featureCoordinator.
        featureCoordinator?.unregisterDeviceCallback(device, featureId, deviceCallback)
          ?: connectedDeviceManager?.unregisterDeviceCallback(device, featureId, deviceCallback)
      }
    }

  private val deviceCallback =
    object : IDeviceCallback.Stub() {
      override fun onSecureChannelEstablished(device: ConnectedDevice) {
        logd(TAG, "Secure channel has been established on ${device.deviceId}. Notifying callback.")
        callback?.onSecureChannelEstablished(device)
      }

      override fun onMessageReceived(device: ConnectedDevice, message: DeviceMessage) {
        processIncomingMessage(device, message)
      }

      override fun onDeviceError(device: ConnectedDevice, error: Int) {
        logw(TAG, "Received a device error of $error from ${device.deviceId}.")
        callback?.onDeviceError(device, error)
      }
    }

  private val deviceAssociationCallback =
    object : IDeviceAssociationCallback.Stub() {
      override fun onAssociatedDeviceAdded(device: AssociatedDevice) {
        logd(TAG, "New device ${device.deviceId} associated. Notifying callback.")
        callback?.onAssociatedDeviceAdded(device)
      }

      override fun onAssociatedDeviceRemoved(device: AssociatedDevice) {
        logd(TAG, "Associated device ${device.deviceId} removed. Notifying callback.")
        callback?.onAssociatedDeviceRemoved(device)
      }

      override fun onAssociatedDeviceUpdated(device: AssociatedDevice) {
        logd(TAG, "Associated device ${device.deviceId} updated. Notifying callback.")
        callback?.onAssociatedDeviceUpdated(device)
      }
    }

  private val logRequestedListener =
    object : IOnLogRequestedListener.Stub() {
      override fun onLogRecordsRequested() {
        val loggerBytes = Logger.getLogger().toByteArray()
        try {
          featureCoordinator?.processLogRecords(loggerId, loggerBytes)
            ?: connectedDeviceManager?.processLogRecords(loggerId, loggerBytes)
        } catch (e: RemoteException) {
          loge(TAG, "Failed to send log records for logger $loggerId.", e)
        }
      }
    }

  private val queryIdGenerator = QueryIdGenerator()

  // queryId -> callback
  private val queryCallbacks: MutableMap<Int, QueryCallback> = ConcurrentHashMap()

  // queryId -> original sender for response
  private val queryResponseRecipients: MutableMap<Int, ParcelUuid> = ConcurrentHashMap()

  private var bindAttempts = 0

  @VisibleForTesting
  internal var featureCoordinator: IFeatureCoordinator? = null
    set(value) {
      field = value
      connectedDeviceManager = value?.let { createConnectedDeviceManagerWrapper(it) }
    }

  @VisibleForTesting internal var connectedDeviceManager: IConnectedDeviceManager? = null

  override var featureId: ParcelUuid? = null

  override var callback: Connector.Callback? = null

  override val connectedDevices: List<ConnectedDevice>
    get() {
      if (!isConnected) {
        logw(
          TAG,
          "Attempted to get connected devices before the platform was connected. Returning empty " +
            "list."
        )
        return emptyList()
      }
      return when (userType) {
        USER_TYPE_DRIVER -> featureCoordinator?.connectedDevicesForDriver
            ?: connectedDeviceManager?.activeUserConnectedDevices
        USER_TYPE_PASSENGER -> featureCoordinator?.connectedDevicesForPassengers
        USER_TYPE_ALL -> featureCoordinator?.allConnectedDevices
            ?: connectedDeviceManager?.activeUserConnectedDevices
        else -> null
      }
        ?: emptyList()
    }

  override val isConnected: Boolean
    get() = featureCoordinator?.aliveOrNull() ?: connectedDeviceManager?.aliveOrNull() != null

  override fun connect() {
    logd(TAG, "Initiating connection to companion platform.")
    if (isConnected) {
      logd(TAG, "Platform is already connected. Skipping binding.")
      initializePlatform()
      callback?.onConnected()
      return
    }
    val intent =
      if (isLegacyOnly.get()) {
        resolveIntent(connectedDeviceManagerAction)
      } else {
        resolveIntent(featureCoordinatorAction)
          ?: resolveIntent(connectedDeviceManagerAction).also { isLegacyOnly.set(true) }
      }

    if (intent == null) {
      loge(TAG, "No services found supporting companion device. Aborting.")
      callback?.onFailedToConnect()
      return
    }

    val success = context.bindService(intent, serviceConnection, /* flag= */ 0)
    if (success) {
      logd(TAG, "Successfully started binding with ${intent.action}.")
      return
    }

    bindAttempts++
    if (bindAttempts > MAX_BIND_ATTEMPTS) {
      loge(TAG, "Failed to bind to service after $bindAttempts attempts. Aborting.")
      callback?.onFailedToConnect()
      return
    }
    logw(TAG, "Unable to bind to service with action ${intent.action}. Trying again.")
    retryHandler.postDelayed(::connect, BIND_RETRY_DURATION.toMillis())
  }

  override fun disconnect() {
    logd(TAG, "Disconnecting from the companion platform.")
    val wasConnected = isConnected
    cleanUpFeatureCoordinator()
    cleanUpConnectedDeviceManager()
    retryHandler.removeCallbacksAndMessages(/* token= */ null)
    try {
      context.unbindService(serviceConnection)
    } catch (e: IllegalArgumentException) {
      logw(TAG, "Attempted to unbind an already unbound service.")
    }
    bindAttempts = 0
    isLegacyOnly.set(false)
    if (wasConnected) {
      callback?.onDisconnected()
    }
  }

  private fun cleanUpFeatureCoordinator() {
    logd(TAG, "Cleaning up FeatureCoordinator.")
    featureCoordinator?.aliveOrNull()?.let {
      try {
        it.unregisterConnectionCallback(connectionCallback)
        for (device in it.allConnectedDevices) {
          it.unregisterDeviceCallback(device, featureId, deviceCallback)
        }
        it.unregisterDeviceAssociationCallback(deviceAssociationCallback)
        it.unregisterOnLogRequestedListener(loggerId, logRequestedListener)
      } catch (e: RemoteException) {
        loge(TAG, "Error while cleaning up FeatureCoordinator.", e)
      }
    }
    if (featureCoordinator != null) {
      this.featureCoordinator = null
      connectedDeviceManager = null
    }
  }

  private fun cleanUpConnectedDeviceManager() {
    logd(TAG, "Cleaning up ConnectedDeviceManager.")
    connectedDeviceManager?.aliveOrNull()?.let {
      try {
        it.unregisterConnectionCallback(connectionCallback)
        for (device in it.activeUserConnectedDevices) {
          it.unregisterDeviceCallback(device, featureId, deviceCallback)
        }
        it.unregisterDeviceAssociationCallback(deviceAssociationCallback)
        it.unregisterOnLogRequestedListener(loggerId, logRequestedListener)
      } catch (e: RemoteException) {
        loge(TAG, "Error while cleaning up ConnectedDeviceManager.", e)
      }
    }
    connectedDeviceManager = null
  }

  override fun binderForAction(action: String): IBinder? {
    return when (action) {
      ACTION_BIND_FEATURE_COORDINATOR, ACTION_BIND_FEATURE_COORDINATOR_FG ->
        featureCoordinator?.asBinder()
      ACTION_BIND_REMOTE_FEATURE, ACTION_BIND_REMOTE_FEATURE_FG ->
        connectedDeviceManager?.asBinder()
      else -> null
    }
  }

  override fun sendMessageSecurely(deviceId: String, message: ByteArray) {
    if (!isConnected) {
      loge(TAG, "Unable to send message, the platform is not actively connected.")
      callback?.onMessageFailedToSend(deviceId, message, /* isTransient= */ true)
      return
    }
    val device = getConnectedDeviceById(deviceId)
    if (device == null) {
      loge(TAG, "No matching device found with id $deviceId when trying to send secure message.")
      callback?.onMessageFailedToSend(deviceId, message, /* isTransient= */ false)
      return
    }
    sendMessageSecurely(device, message)
  }

  override fun sendMessageSecurely(device: ConnectedDevice, message: ByteArray) {
    if (!isConnected) {
      loge(TAG, "Unable to send message, the platform is not actively connected.")
      callback?.onMessageFailedToSend(device.deviceId, message, /* isTransient= */ true)
      return
    }
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        featureId?.uuid,
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.CLIENT_MESSAGE,
        message
      )
    try {
      sendMessageInternal(device, deviceMessage)
    } catch (e: RemoteException) {
      loge(TAG, "Error while sending secure message.", e)
      callback?.onMessageFailedToSend(device.deviceId, message, /* isTransient= */ false)
    }
  }

  override fun sendQuerySecurely(
    deviceId: String,
    request: ByteArray,
    parameters: ByteArray?,
    callback: QueryCallback
  ) {
    if (!isConnected) {
      loge(TAG, "Unable to send query, the platform is not actively connected.")
      callback.onQueryFailedToSend(/* isTransient= */ true)
      return
    }
    val device = getConnectedDeviceById(deviceId)
    if (device == null) {
      loge(TAG, "No matching device found with id $deviceId when trying to send a secure query.")
      callback.onQueryFailedToSend(/* isTransient= */ false)
      return
    }
    sendQuerySecurely(device, request, parameters, callback)
  }

  override fun sendQuerySecurely(
    device: ConnectedDevice,
    request: ByteArray,
    parameters: ByteArray?,
    callback: QueryCallback
  ) {
    val featureId = featureId
    if (featureId == null) {
      loge(TAG, "Attempted to send a query with no feature id.")
      callback.onQueryFailedToSend(/* isTransient= */ false)
      return
    }
    sendQuerySecurelyInternal(device, featureId, request, parameters, callback)
  }

  private fun sendQuerySecurelyInternal(
    device: ConnectedDevice,
    recipient: ParcelUuid,
    request: ByteArray,
    parameters: ByteArray?,
    callback: QueryCallback
  ) {
    if (!isConnected) {
      loge(TAG, "Unable to send message, the platform is not actively connected.")
      callback.onQueryFailedToSend(/* isTransient= */ true)
      return
    }
    val featureId = featureId
    if (featureId == null) {
      loge(TAG, "Attempted to send a query with no feature id.")
      callback.onQueryFailedToSend(/* isTransient= */ false)
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
    logd(TAG, "Sending secure query with id $id.")
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        recipient.uuid,
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.QUERY,
        builder.build().toByteArray()
      )
    try {
      sendMessageInternal(device, deviceMessage)
    } catch (e: RemoteException) {
      loge(TAG, "Error while sending secure query.", e)
      callback.onQueryFailedToSend(/* isTransient= */ false)
      return
    }
    queryCallbacks[id] = callback
  }

  override fun respondToQuerySecurely(
    device: ConnectedDevice,
    queryId: Int,
    success: Boolean,
    response: ByteArray?
  ) {
    if (!isConnected) {
      loge(TAG, "Unable to send query response, the platform is not actively connected.")
      return
    }
    val recipientId: ParcelUuid? = queryResponseRecipients.remove(queryId)
    if (recipientId == null) {
      loge(TAG, "Unable to send response to unrecognized query $queryId.")
      return
    }
    val builder = QueryResponse.newBuilder().setQueryId(queryId).setSuccess(success)
    if (response != null) {
      builder.response = ByteString.copyFrom(response)
    }
    val queryResponse = builder.build()
    logd(TAG, "Sending response to query $queryId to $recipientId.")
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        recipientId.uuid,
        /* isMessageEncrypted= */ true,
        DeviceMessage.OperationType.QUERY_RESPONSE,
        queryResponse.toByteArray()
      )
    try {
      sendMessageInternal(device, deviceMessage)
    } catch (e: RemoteException) {
      loge(TAG, "Error while sending query response.", e)
    }
  }

  override fun getConnectedDeviceById(deviceId: String): ConnectedDevice? {
    if (!isConnected) {
      loge(TAG, "Unable to get connected device by id. The platform is not actively connected.")
      return null
    }
    val connectedDevices =
      try {
        featureCoordinator?.allConnectedDevices
          ?: connectedDeviceManager?.activeUserConnectedDevices
      } catch (e: RemoteException) {
        loge(TAG, "Exception while retrieving connected devices.", e)
        null
      }

    return connectedDevices?.find { it.deviceId == deviceId }
  }

  override fun retrieveCompanionApplicationName(
    device: ConnectedDevice,
    callback: AppNameCallback
  ) {
    val systemQuery = SystemQuery.newBuilder().setType(SystemQueryType.APP_NAME).build()
    sendQuerySecurelyInternal(
      device,
      SYSTEM_FEATURE_ID,
      systemQuery.toByteArray(),
      /* parameters= */ null,
      object : QueryCallback {
        override fun onSuccess(response: ByteArray?) {
          if (response == null || response.isEmpty()) {
            loge(TAG, "Received a null or empty response for the application name.")
            callback.onError()
            return
          }
          val appName = String(response, StandardCharsets.UTF_8)
          logd(TAG, "Received successful app name query response of $appName.")
          callback.onNameReceived(appName)
        }

        override fun onError(response: ByteArray?) {
          loge(TAG, "Received an error response when querying for application name.")
          callback.onError()
        }

        override fun onQueryFailedToSend(isTransient: Boolean) {
          loge(TAG, "Failed to send the query for the application name.")
          callback.onError()
        }
      }
    )
  }

  private fun sendMessageInternal(device: ConnectedDevice, message: DeviceMessage) {
    featureCoordinator?.aliveOrNull()?.sendMessage(device, message)
      ?: connectedDeviceManager?.aliveOrNull()?.sendMessage(device, message)
  }

  private fun onServiceConnected(service: IBinder?) {
    logd(TAG, "Service connected.")
    if (isLegacyOnly.get()) {
      logd(TAG, "Setting up legacy manager.")
      connectedDeviceManager = IConnectedDeviceManager.Stub.asInterface(service)
    } else {
      logd(TAG, "Setting up coordinator and legacy wrapper.")
      featureCoordinator = IFeatureCoordinator.Stub.asInterface(service)
      val coordinator =
        featureCoordinator
          ?: throw IllegalStateException("Cannot create wrapper of a null feature coordinator.")
      connectedDeviceManager = createConnectedDeviceManagerWrapper(coordinator)
    }
    initializePlatform()
    callback?.onConnected()
  }

  private fun initializePlatform() {
    initializeFeatureCoordinator()
    initializeConnectedDeviceManager()
  }

  private fun initializeFeatureCoordinator() {
    logd(TAG, "Initializing FeatureCoordinator.")
    val featureCoordinator = featureCoordinator
    if (featureCoordinator == null) {
      logd(TAG, "Unable to initialize null FeatureCoordinator. Ignoring.")
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
          loge(TAG, "Unknown user type $userType detected.")
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
          logd(TAG, "There is currently no feature id set. Skipping device registration.")
          continue
        }
        featureCoordinator.registerDeviceCallback(device, featureId, deviceCallback)
      }
    } catch (e: RemoteException) {
      loge(TAG, "Error while initializing FeatureCoordinator.", e)
    }
  }

  private fun initializeConnectedDeviceManager() {
    logd(TAG, "Initializing ConnectedDeviceManager.")
    if (featureCoordinator != null) {
      logd(TAG, "FeatureCoordinator has already registered callbacks.")
      return
    }
    val connectedDeviceManager =
      connectedDeviceManager
        ?: throw IllegalStateException(
          "Attempted to initialize the platform with a null ConnectedDeviceManager! This should " +
            "not happen and is indicative of a serious logic flaw."
        )
    try {
      connectedDeviceManager.registerActiveUserConnectionCallback(connectionCallback)
      connectedDeviceManager.registerDeviceAssociationCallback(deviceAssociationCallback)
      connectedDeviceManager.registerOnLogRequestedListener(loggerId, logRequestedListener)
      val activeUserConnectedDevices = connectedDeviceManager.activeUserConnectedDevices
      for (device in activeUserConnectedDevices) {
        callback?.onDeviceConnected(device)
        if (device.hasSecureChannel()) {
          callback?.onSecureChannelEstablished(device)
        }
        connectedDeviceManager.registerDeviceCallback(device, featureId, deviceCallback)
      }
    } catch (e: RemoteException) {
      loge(TAG, "Error while initializing ConnectedDeviceManager.", e)
    }
  }

  private fun onServiceDisconnected() {
    logd(TAG, "Service has disconnected. Cleaning up.")
    disconnect()
  }

  private fun onNullBinding() {
    if (!isLegacyOnly.compareAndSet(false, true)) {
      loge(TAG, "Received a null binding. Alerting callbacks of failed connection.")
      callback?.onFailedToConnect()
      return
    }
    logd(
      TAG,
      "Service does not support feature coordinator despite defining the action. " +
        "Attempting to bind again in legacy mode."
    )
    connect()
  }

  private fun resolveIntent(action: String): Intent? {
    val packageManager = context.packageManager
    val intent = Intent(action)
    val services = packageManager.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY)
    if (services.isEmpty()) {
      logw(TAG, "There are no services supporting the $action action installed on this device.")
      return null
    }
    logd(TAG, "Found ${services.size} service(s) supporting $action. Choosing the first one.")
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
        logd(TAG, "Received client message. Passing on to feature.")
        callback?.onMessageReceived(device, message)
        return
      }
      DeviceMessage.OperationType.QUERY -> {
        try {
          val query = Query.parseFrom(message, ExtensionRegistryLite.getEmptyRegistry())
          processQuery(device, query)
        } catch (e: InvalidProtocolBufferException) {
          loge(TAG, "Unable to parse query.", e)
        }
        return
      }
      DeviceMessage.OperationType.QUERY_RESPONSE -> {
        try {
          val response = QueryResponse.parseFrom(message, ExtensionRegistryLite.getEmptyRegistry())
          processQueryResponse(response)
        } catch (e: InvalidProtocolBufferException) {
          loge(TAG, "Unable to parse query response.", e)
        }
        return
      }
      else -> loge(TAG, "Received unknown type of message: $operationType. Ignoring.")
    }
  }

  private fun processQuery(device: ConnectedDevice, query: Query) {
    logd(TAG, "Received a new query with id ${query.id}. Passing on to feature.")
    val sender = ParcelUuid(ByteUtils.bytesToUUID(query.sender.toByteArray()))
    queryResponseRecipients[query.id] = sender
    callback?.onQueryReceived(
      device,
      query.id,
      query.request.toByteArray(),
      query.parameters.toByteArray()
    )
  }

  private fun processQueryResponse(response: QueryResponse) {
    logd(TAG, "Received a query response. Issuing registered callback.")
    val callback = queryCallbacks.remove(response.queryId)
    if (callback == null) {
      loge(TAG, "Unable to locate callback for query ${response.queryId}.")
      return
    }
    if (response.success) {
      callback.onSuccess(response.response.toByteArray())
    } else {
      callback.onError(response.response.toByteArray())
    }
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
      featureCoordinator: IFeatureCoordinator
    ): CompanionConnector =
      CompanionConnector(context, userType = userType).apply {
        this.featureCoordinator = featureCoordinator
        connectedDeviceManager = createConnectedDeviceManagerWrapper(featureCoordinator)
      }

    @JvmStatic
    /** Create a [CompanionConnector] instance with a [connectedDeviceManager] already populated. */
    fun createLocalConnector(
      context: Context,
      userType: @UserType Int,
      connectedDeviceManager: IConnectedDeviceManager
    ): CompanionConnector =
      CompanionConnector(context, userType = userType).apply {
        this.connectedDeviceManager = connectedDeviceManager
      }

    /**
     * Generate an [IConnectedDeviceManager] wrapper around the [fromFeatureCoordinator] to support
     * legacy functionality.
     */
    @VisibleForTesting
    internal fun createConnectedDeviceManagerWrapper(
      fromFeatureCoordinator: IFeatureCoordinator
    ): IConnectedDeviceManager {

      return object : IConnectedDeviceManager.Stub() {
        @Throws(RemoteException::class)
        override fun getActiveUserConnectedDevices(): List<ConnectedDevice> {
          return fromFeatureCoordinator.connectedDevicesForDriver
        }

        @Throws(RemoteException::class)
        override fun registerActiveUserConnectionCallback(callback: IConnectionCallback) {
          fromFeatureCoordinator.registerDriverConnectionCallback(callback)
        }

        @Throws(RemoteException::class)
        override fun unregisterConnectionCallback(callback: IConnectionCallback) {
          fromFeatureCoordinator.unregisterConnectionCallback(callback)
        }

        @Throws(RemoteException::class)
        override fun registerDeviceCallback(
          connectedDevice: ConnectedDevice,
          recipientId: ParcelUuid,
          callback: IDeviceCallback
        ) {
          fromFeatureCoordinator.registerDeviceCallback(connectedDevice, recipientId, callback)
        }

        @Throws(RemoteException::class)
        override fun unregisterDeviceCallback(
          connectedDevice: ConnectedDevice,
          recipientId: ParcelUuid,
          callback: IDeviceCallback
        ) {
          fromFeatureCoordinator.unregisterDeviceCallback(connectedDevice, recipientId, callback)
        }

        @Throws(RemoteException::class)
        override fun sendMessage(
          connectedDevice: ConnectedDevice,
          message: DeviceMessage
        ): Boolean {
          return fromFeatureCoordinator.sendMessage(connectedDevice, message)
        }

        @Throws(RemoteException::class)
        override fun registerDeviceAssociationCallback(callback: IDeviceAssociationCallback) {
          fromFeatureCoordinator.registerDeviceAssociationCallback(callback)
        }

        @Throws(RemoteException::class)
        override fun unregisterDeviceAssociationCallback(callback: IDeviceAssociationCallback) {
          fromFeatureCoordinator.unregisterDeviceAssociationCallback(callback)
        }

        @Throws(RemoteException::class)
        override fun registerOnLogRequestedListener(
          loggerId: Int,
          listener: IOnLogRequestedListener
        ) {
          fromFeatureCoordinator.registerOnLogRequestedListener(loggerId, listener)
        }

        @Throws(RemoteException::class)
        override fun unregisterOnLogRequestedListener(
          loggerId: Int,
          listener: IOnLogRequestedListener
        ) {
          fromFeatureCoordinator.unregisterOnLogRequestedListener(loggerId, listener)
        }

        @Throws(RemoteException::class)
        override fun processLogRecords(loggerId: Int, logRecords: ByteArray) {
          fromFeatureCoordinator.processLogRecords(loggerId, logRecords)
        }
      }
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
