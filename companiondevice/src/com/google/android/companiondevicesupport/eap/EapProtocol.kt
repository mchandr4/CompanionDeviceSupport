/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.companiondevicesupport.eap

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.ParcelUuid
import android.os.RemoteException
import androidx.annotation.VisibleForTesting
import com.google.android.connecteddevice.transport.ConnectChallenge
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.IDataSendCallback
import com.google.android.connecteddevice.transport.IDiscoveryCallback
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logw
import com.panasonic.iapx.IDeviceConnection
import com.panasonic.iapx.IDeviceConnectionDelegate
import com.panasonic.iapx.IServiceConnector
import com.panasonic.iapx.IServiceConnectorDelegate
import java.util.concurrent.ConcurrentHashMap

/** Defines how connection is established and data is transferred via EAP channel. */
class EapProtocol(
  private val eapClientName: String,
  private val eapServiceName: String,
  private val maxPacketSize: Int,
) : ConnectionProtocol() {
  // Stores the connected EAP session id to the real connection pair.
  private val sessions = ConcurrentHashMap<Long, IDeviceConnection>()
  private val ongoingDiscoveries = ConcurrentHashMap<String, IDiscoveryCallback>()

  private val serviceDelegate =
    object : IServiceConnectorDelegate.Stub() {
      override fun OnServiceConnectionChange(status: Int) {
        logd(TAG, "Service connection status has changed to $status.")
        if (status == IServiceConnector.kServiceConnectionReady) {
          logd(TAG, "Connection to EAP service is ready to use.")
        }
      }
    }

  init {
    bindToEapService()
  }

  @SuppressLint("PrivateApi")
  private fun bindToEapService() {
    logd(TAG, "Attempting to connect to EAP service $eapServiceName.")
    val serviceBinder =
      Class.forName("android.os.ServiceManager")
        .getMethod("getService", java.lang.String::class.java)
        .invoke(null, eapServiceName) as? IBinder
    if (serviceBinder == null) {
      loge(TAG, "Unable to bind to EAP service. Aborting.")
      return
    }
    val connector = IServiceConnector.Stub.asInterface(serviceBinder)
    logd(TAG, "Attempting to connect to the EAP client.")
    connectClient(connector)
  }

  @VisibleForTesting
  internal fun connectClient(connector: IServiceConnector) {
    try {
      connector.ConnectClient(
        eapClientName,
        serviceDelegate.asBinder(),
        generateDeviceDelegate().asBinder()
      )
    } catch (e: RemoteException) {
      loge(TAG, "Failed to connect the EAP client.", e)
    }
  }

  override fun startAssociationDiscovery(
    name: String,
    identifier: ParcelUuid,
    callback: IDiscoveryCallback,
  ) {
    logd(TAG, "Request to start EAP discovery, register discovery callback.")
    ongoingDiscoveries[name] = callback
  }

  private fun generateDeviceDelegate(): IDeviceConnectionDelegate {
    return object : IDeviceConnectionDelegate.Stub() {
      override fun OnConnectionReady(connection: IDeviceConnection?, transportType: Int) {
        logd(TAG, "Connection is ready on transport $transportType.")
      }

      override fun OnConnectionClosed(connection: IDeviceConnection?) {
        logd(TAG, "Connection has been closed.")
      }

      override fun OnEAPSessionStart(
        connection: IDeviceConnection,
        eapSessionId: Long,
        eapProtocolName: String?
      ) {
        logd(
          TAG,
          "Starting new session $eapSessionId with protocol $eapProtocolName. Currently " +
            "${ongoingDiscoveries.size} callbacks registered."
        )
        if (ongoingDiscoveries.containsKey(eapProtocolName)) {
          sessions[eapSessionId] = connection
          ongoingDiscoveries[eapProtocolName]?.onDeviceConnected(eapSessionId.toString())
        } else {
          logw(TAG, "Did not find matching callback; ignoring the connection.")
        }
      }

      override fun OnEAPSessionStop(connection: IDeviceConnection?, eapSessionId: Long) {
        logd(TAG, "Device disconnected. id: $eapSessionId.")
        if (sessions.containsKey(eapSessionId)) {
          sessions.remove(eapSessionId)
          deviceDisconnectedListeners[eapSessionId.toString()]?.invoke {
            it.onDeviceDisconnected(eapSessionId.toString())
          }
        }
      }

      override fun OnEAPData(connection: IDeviceConnection?, eapSessionId: Long, data: ByteArray?) {
        logd(TAG, "Received new data from session $eapSessionId.")
        if (data != null) {
          notifyDataReceived(eapSessionId.toString(), data)
        }
      }

      override fun OnDeviceNameUpdate(connection: IDeviceConnection?, name: String?) {}

      override fun OnDeviceTransientUUIDUpdate(connection: IDeviceConnection?, uuid: String?) {}
    }
  }

  override fun stopAssociationDiscovery() {
    ongoingDiscoveries.clear()
  }

  override fun reset() {
    super.reset()
    logd(TAG, "Reset the protocol.")
    sessions.clear()
    ongoingDiscoveries.clear()
  }

  override fun sendData(protocolId: String, data: ByteArray, callback: IDataSendCallback?) {
    sessions[protocolId.toLong()]?.SendEAPData(protocolId.toLong(), data)
      ?: loge(TAG, "Unable to find device with session $protocolId.")
  }

  override fun getMaxWriteSize(protocolId: String): Int {
    return maxPacketSize
  }

  override fun isDeviceVerificationRequired() = false

  override fun startConnectionDiscovery(
    id: ParcelUuid,
    challenge: ConnectChallenge,
    callback: IDiscoveryCallback,
  ) {}

  override fun stopConnectionDiscovery(id: ParcelUuid) {}

  override fun disconnectDevice(protocolId: String) {}

  companion object {
    private const val TAG = "EapProtocol"
  }
}
