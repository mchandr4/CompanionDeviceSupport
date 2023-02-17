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

package com.google.android.connecteddevice.oob

import android.os.ParcelUuid
import com.google.android.connecteddevice.transport.IConnectionProtocol
import com.google.android.connecteddevice.transport.IDataSendCallback
import com.google.android.connecteddevice.transport.IDiscoveryCallback
import com.google.android.connecteddevice.transport.ProtocolDelegate
import com.google.android.connecteddevice.transport.ProtocolDevice
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.logw
import java.util.concurrent.CopyOnWriteArrayList

/** Manages OOB data exchange through [ConnectionProtocol]s. */
class TransportOobChannel(
  private val delegate: ProtocolDelegate,
  private val protocolName: String
) : OobChannel {
  /** List of [ProtocolDevice]s which is doing OOB data exchange. */
  private val ongoingOobDataExchange = CopyOnWriteArrayList<ProtocolDevice>()

  override fun completeOobDataExchange(oobData: ByteArray): Boolean {
    val protocols = delegate.oobProtocols
    if (protocols.isEmpty()) {
      logw(TAG, "No available protocol to send OOB data, ignore.")
      return false
    }
    for (protocol in protocols) {
      logd(TAG, "Start OOB discovery with protocol: $protocol.")
      protocol.startAssociationDiscovery(
        protocolName,
        DEFAULT_UUID,
        generateDiscoveryCallback(protocol, oobData)
      )
    }
    return true
  }

  private fun generateDiscoveryCallback(
    protocol: IConnectionProtocol,
    oobData: ByteArray
  ): IDiscoveryCallback {
    return object : IDiscoveryCallback.Stub() {
      override fun onDeviceConnected(protocolId: String) {
        val protocolDevice = ProtocolDevice(protocol, protocolId)
        ongoingOobDataExchange.add(protocolDevice)
        logd(
          TAG,
          "Oob channel established with id: $protocolId, protocol: $protocol, send OOB data."
        )
        protocol.sendData(protocolId, oobData, generateDataSentCallback(protocolDevice))
      }

      override fun onDiscoveryStartedSuccessfully() {
        logd(TAG, "OOB discovery started successfully via protocol: $protocol")
      }

      override fun onDiscoveryFailedToStart() {
        logw(TAG, "Failed to start OOB discovery via protocol: $protocol")
      }
    }
  }

  private fun generateDataSentCallback(device: ProtocolDevice): IDataSendCallback {
    return object : IDataSendCallback.Stub() {
      override fun onDataSentSuccessfully() {
        logd(TAG, "OOB data sent successfully with protocol id: $device.protocolId.")
      }

      override fun onDataFailedToSend() {
        logw(TAG, "OOB data failed to send through protocol: $device.protocol, ignored.")
        device.protocol.disconnectDevice(device.protocolId)
        ongoingOobDataExchange.remove(device)
      }
    }
  }

  override fun interrupt() {
    for (protocolDevice in ongoingOobDataExchange) {
      protocolDevice.protocol.disconnectDevice(protocolDevice.protocolId)
    }
    ongoingOobDataExchange.clear()
  }

  companion object {
    private const val TAG = "TransportOobChannel"
    private val DEFAULT_UUID = ParcelUuid.fromString("00001101-0000-1000-8000-00805F9B34FB")
  }
}
