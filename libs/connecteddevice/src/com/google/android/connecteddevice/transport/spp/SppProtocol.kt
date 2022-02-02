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
package com.google.android.connecteddevice.transport.spp

import android.bluetooth.BluetoothDevice
import android.os.ParcelUuid
import android.os.RemoteException
import com.google.android.connecteddevice.transport.BluetoothDeviceProvider
import com.google.android.connecteddevice.transport.ConnectChallenge
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.IDataSendCallback
import com.google.android.connecteddevice.transport.IDiscoveryCallback
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder.OnErrorListener
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder.OnMessageReceivedListener
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logw
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Representation of a Serial Port Profile channel, which communicated with [SppService] via
 * [ConnectedDeviceSppDelegateBinder] to handle connection related actions.
 *
 * @property sppBinder [ConnectedDeviceSppDelegateBinder] for communication with [SppService].
 * @property maxWriteSize Maximum size in bytes to write in one packet.
 */
class SppProtocol
@JvmOverloads
constructor(
  private val sppBinder: ConnectedDeviceSppDelegateBinder,
  private val maxWriteSize: Int,
  callbackExecutor: Executor = Executors.newCachedThreadPool()
) : ConnectionProtocol(callbackExecutor), BluetoothDeviceProvider {
  private val pendingConnections = mutableMapOf<UUID, PendingConnection>()
  private val connections = mutableMapOf<UUID, Connection>()
  private val connectedDevices = mutableMapOf<UUID, BluetoothDevice>()
  private var associationIdentifier: UUID? = null

  override fun startAssociationDiscovery(
    name: String,
    identifier: ParcelUuid,
    callback: IDiscoveryCallback
  ) {
    val uuidIdentifier = identifier.uuid
    associationIdentifier = uuidIdentifier
    logd(TAG, "Start association discovery for association with UUID $uuidIdentifier.")
    startConnection(uuidIdentifier, callback)
  }

  override fun startConnectionDiscovery(
    id: ParcelUuid,
    challenge: ConnectChallenge,
    callback: IDiscoveryCallback
  ) {
    logd(TAG, "Starting connection discovery for device $id")
    startConnection(id.uuid, callback)
  }

  private fun startConnection(id: UUID, callback: IDiscoveryCallback) {
    try {
      val pendingConnection = sppBinder.connectAsServer(id, /* isSecure= */ true)
      if (pendingConnection == null) {
        callback.onDiscoveryFailedToStart()
        return
      }
      pendingConnections[id] = pendingConnection
      pendingConnection.setOnConnectedListener(generateOnConnectionListener(callback))
      callback.onDiscoveryStartedSuccessfully()
    } catch (e: RemoteException) {
      callback.onDiscoveryFailedToStart()
      loge(TAG, "Error when try to start discovery for remote device.", e)
    }
  }

  private fun generateOnConnectionListener(callback: IDiscoveryCallback) =
    PendingConnection.OnConnectedListener { uuid, remoteDevice, isSecure, deviceName ->
      val protocolId = UUID.randomUUID()
      val connection = Connection(ParcelUuid(uuid), remoteDevice, isSecure, deviceName)
      connectedDevices[protocolId] = remoteDevice

      pendingConnections.remove(uuid)
      connections[protocolId] = connection
      sppBinder.registerConnectionCallback(uuid, generateOnErrorListener())
      sppBinder.setOnMessageReceivedListener(
        connection,
        generateOnMessageReceivedListener(protocolId)
      )
      callback.onDeviceConnected(protocolId.toString())
      logd(
        TAG,
        "Remote device $remoteDevice connected successfully with UUID $uuid, assigned " +
          "connection id $protocolId."
      )
    }

  private fun generateOnMessageReceivedListener(protocolId: UUID): OnMessageReceivedListener {
    return OnMessageReceivedListener { message ->
      notifyDataReceived(protocolId.toString(), message)
      val listeners = dataReceivedListeners[protocolId.toString()]
      logd(
        TAG,
        "Informed message received with connection $protocolId to ${listeners?.size()} listeners."
      )
    }
  }

  private fun generateOnErrorListener(): OnErrorListener {
    return OnErrorListener { currentConnection ->
      val protocolId = connections.entries.first { it.value == currentConnection }.key
      val listeners = deviceDisconnectedListeners[protocolId.toString()]
      listeners?.invoke { it.onDeviceDisconnected(protocolId.toString()) }
      connectedDevices.remove(protocolId)
      connections.remove(protocolId)
      logd(
        TAG,
        "Inform device connection error with connection $protocolId to ${listeners?.size()} " +
          "listeners."
      )
      removeListeners(protocolId.toString())
    }
  }

  override fun stopAssociationDiscovery() {
    val id = associationIdentifier
    if (id == null) {
      logd(TAG, "No association discovery is happening, ignoring.")
      return
    }
    logd(TAG, "Stop association discovery with UUID $id.")
    stopDiscovery(id)
    associationIdentifier = null
  }

  override fun stopConnectionDiscovery(id: ParcelUuid) {
    logd(TAG, "Stop connection discovery with UUID ${id.uuid}.")
    stopDiscovery(id.uuid)
  }

  private fun stopDiscovery(id: UUID) {
    val pendingConnection = pendingConnections[id]
    if (pendingConnection != null) {
      cancelPendingConnection(pendingConnection)
    } else {
      logw(TAG, "Try to stop unidentified discovery process with id $id")
    }
  }

  override fun sendData(protocolId: String, data: ByteArray, callback: IDataSendCallback?) {
    val connection = connections[UUID.fromString(protocolId)]
    if (connection == null) {
      callback?.onDataFailedToSend()
      logw(TAG, "Unable to find correct connection channel with id $protocolId to send data")
      return
    }
    try {
      sppBinder.sendMessage(connection, data)
    } catch (e: RemoteException) {
      loge(TAG, "Error when try to send data to protocol channel with id $protocolId.", e)
      callback?.onDataFailedToSend()
    }
    callback?.onDataSentSuccessfully()
  }

  override fun disconnectDevice(protocolId: String) {
    val connection = connections[UUID.fromString(protocolId)]
    if (connection == null) {
      logw(TAG, "Try to disconnect unidentified device with id $protocolId")
      return
    }
    disconnect(connection)
  }

  /** Cancel all ongoing connection attempt and disconnect already connected devices. */
  override fun reset() {
    super.reset()
    logd(TAG, "Reset: cancel all connection attempts and disconnect all devices.")
    associationIdentifier = null
    pendingConnections.forEach { cancelPendingConnection(it.value) }
    pendingConnections.clear()

    connections.forEach { disconnect(it.value) }
    connections.clear()
  }

  override fun getBluetoothDeviceById(protocolId: String) =
    try {
      connectedDevices[UUID.fromString(protocolId)]
    } catch (e: IllegalArgumentException) {
      loge(TAG, "Invalid protocol Id passed.", e)
      null
    }

  private fun cancelPendingConnection(pendingConnection: PendingConnection) {
    try {
      sppBinder.cancelConnectionAttempt(pendingConnection)
    } catch (e: RemoteException) {
      loge(
        TAG,
        "Error when try to stop discovery for remote device with id " + "${pendingConnection.id}.",
        e
      )
    }
  }

  private fun disconnect(connection: Connection) {
    try {
      sppBinder.disconnect(connection)
    } catch (e: RemoteException) {
      loge(TAG, "Error when try to disconnect remote device with id ${connection.serviceUuid}.", e)
    }
  }

  override fun getMaxWriteSize(protocolId: String) = maxWriteSize

  override fun isDeviceVerificationRequired() = false

  companion object {
    private const val TAG = "SppProtocol"
  }
}
