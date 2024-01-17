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
import android.content.Context
import android.os.ParcelUuid
import androidx.annotation.VisibleForTesting
import com.google.android.connecteddevice.transport.ConnectChallenge
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.IDataSendCallback
import com.google.android.connecteddevice.transport.IDiscoveryCallback
import com.google.android.connecteddevice.transport.spp.SppManager.OnMessageReceivedListener
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logw
import java.util.UUID

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
  private val context: Context,
  private val maxWriteSize: Int,
) : ConnectionProtocol() {
  /** Map protocolId to the [SppManager] which manages the certain connection. */
  private val connections = mutableMapOf<String, SppManager>()
  /**
   * Map service UUID used to perform discovery to the [SppManager] which performs the discovery.
   */
  private val pendingDiscoveries = mutableMapOf<UUID, SppManager>()
  /** Invoke [SppManager]'s callback in current thread. */
  private val managerCallbackExecutor = Runnable::run
  @VisibleForTesting internal var associationIdentifier: UUID? = null

  override fun startAssociationDiscovery(
    name: String,
    identifier: ParcelUuid,
    callback: IDiscoveryCallback,
  ) {
    val uuidIdentifier = identifier.uuid
    associationIdentifier = uuidIdentifier
    logd(TAG, "Start association discovery for association with UUID $uuidIdentifier.")
    startConnection(uuidIdentifier, callback)
  }

  override fun startConnectionDiscovery(
    id: ParcelUuid,
    challenge: ConnectChallenge,
    callback: IDiscoveryCallback,
  ) {
    logd(TAG, "Starting connection discovery for device $id")
    startConnection(id.uuid, callback)
  }

  @VisibleForTesting
  internal fun startConnection(
    id: UUID,
    callback: IDiscoveryCallback,
    manager: SppManager = SppManager(context, /* isSecure= */ true)
  ) {
    manager.registerCallback(
      generateConnectionCallback(manager, id, callback),
      managerCallbackExecutor
    )
    if (manager.startListening(id)) {
      callback.onDiscoveryStartedSuccessfully()
      pendingDiscoveries[id] = manager
    } else {
      loge(
        TAG,
        "Error when try to start discovery for remote device. Issue discovery failed callback."
      )
      callback.onDiscoveryFailedToStart()
    }
  }

  private fun generateConnectionCallback(
    manager: SppManager,
    id: UUID,
    discoveryCallback: IDiscoveryCallback,
  ): SppManager.ConnectionCallback {
    val protocolId = createProtocolId()
    return object : SppManager.ConnectionCallback {
      override fun onRemoteDeviceConnected(device: BluetoothDevice) {
        pendingDiscoveries.remove(id)
        connections[protocolId] = manager
        manager.addOnMessageReceivedListener(
          generateOnMessageReceivedListener(protocolId),
          managerCallbackExecutor
        )
        discoveryCallback.onDeviceConnected(protocolId)
        logd(
          TAG,
          "Remote device $device connected successfully, assigned connection id $protocolId."
        )
      }

      override fun onRemoteDeviceDisconnected(device: BluetoothDevice) {
        logd(TAG, "Remote device disconnected: $device")
        deviceDisconnectedListeners[protocolId]?.invoke { it.onDeviceDisconnected(protocolId) }
        connections.remove(protocolId)
        removeListeners(protocolId)
      }
    }
  }

  private fun createProtocolId() = UUID.randomUUID().toString()

  private fun generateOnMessageReceivedListener(protocolId: String): OnMessageReceivedListener {
    return OnMessageReceivedListener { _, message ->
      notifyDataReceived(protocolId, message)
      val listeners = dataReceivedListeners[protocolId]
      logd(
        TAG,
        "Informed message received with connection $protocolId to ${listeners?.size()} listeners."
      )
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
    val pendingDiscoveryManager = pendingDiscoveries[id]
    if (pendingDiscoveryManager == null) {
      logw(TAG, "Attetmpted to stop discovery for $id, but no pending discovery. Ignoring.")
      return
    }
    pendingDiscoveryManager.cleanup()
  }

  override fun sendData(protocolId: String, data: ByteArray, callback: IDataSendCallback?) {
    val manager = connections[protocolId]
    if (manager == null) {
      callback?.onDataFailedToSend()
      logw(TAG, "Unable to find correct connection channel with id $protocolId to send data")
      return
    }
    val pendingMessage =
      PendingSentMessage().apply { setOnSuccessListener { callback?.onDataSentSuccessfully() } }
    val success = manager.write(data, pendingMessage)
    if (!success) {
      loge(TAG, "Error when try to send data to protocol channel with id $protocolId.")
      callback?.onDataFailedToSend()
    }
  }

  override fun disconnectDevice(protocolId: String) {
    connections.remove(protocolId)?.cleanup()
  }

  /** Cancel all ongoing connection attempt and disconnect already connected devices. */
  override fun reset() {
    super.reset()
    logd(TAG, "Reset: cancel all connection attempts and disconnect all devices.")
    for (manager in pendingDiscoveries.values) {
      manager.cleanup()
    }
    for (manager in connections.values) {
      manager.cleanup()
    }
    associationIdentifier = null
    pendingDiscoveries.clear()
    connections.clear()
  }

  override fun getMaxWriteSize(protocolId: String) = maxWriteSize

  override fun isDeviceVerificationRequired() = false

  companion object {
    private const val TAG = "SppProtocol"
  }
}
