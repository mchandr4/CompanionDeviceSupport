/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.android.connecteddevice.ping

import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.google.android.companionprotos.PeriodicPingProto.PeriodicPingMessage
import com.google.android.companionprotos.PeriodicPingProto.PeriodicPingMessage.MessageType
import com.google.android.connecteddevice.api.Connector
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logi
import com.google.android.connecteddevice.util.SafeLog.logw
import com.google.protobuf.InvalidProtocolBufferException
import java.time.Duration

class PeriodicPingFeature(private val connector: Connector) : Connector.Callback {

  init {
    connector.callback = this@PeriodicPingFeature
    connector.featureId = FEATURE_ID
  }

  private val periodicPingTask =
    object : Runnable {
      override fun run() {
        sendPing()
        handler.postDelayed(this, PING_DELAY.toMillis())
      }
    }
  private val handler = Handler(Looper.getMainLooper())
  private var connectedDevice: ConnectedDevice? = null

  fun start() {
    connector.connect()
  }

  fun stop() {
    connector.disconnect()
  }

  override fun onSecureChannelEstablished(device: ConnectedDevice) {
    connectedDevice = device

    handler.post(periodicPingTask)
    logi(TAG, "Starting periodic pings for ${device.deviceId} every $PING_DELAY.")
  }

  override fun onDeviceDisconnected(device: ConnectedDevice) {
    if (connectedDevice != device) {
      logw(TAG, "A different device has disconnected. Ignore.")
      return
    }

    connectedDevice = null
    handler.removeCallbacks(periodicPingTask)
    logi(TAG, "${device.deviceId} disconnected. Stopping periodic ping.")
  }

  override fun onMessageReceived(device: ConnectedDevice, message: ByteArray) {
    val periodicPingMessage =
      try {
        PeriodicPingMessage.parseFrom(message)
      } catch (e: InvalidProtocolBufferException) {
        loge(TAG, "Received invalid message. Ignore.")
        return
      }

    when (periodicPingMessage.messageType) {
      MessageType.ACK -> {} // No-op
      else -> {
        loge(TAG, "Received invalid message type: ${periodicPingMessage.messageType}. Ignore.")
      }
    }
  }

  private fun sendPing() {
    if (connectedDevice == null) {
      loge(TAG, "No device connected. Cannot send ping.")
      return
    }
    connector.sendMessageSecurely(connectedDevice!!, PING_MESSAGE.toByteArray())
  }

  companion object {
    // https://developer.apple.com/documentation/uikit/uiapplicationdelegate/1622997-applicationdidenterbackground
    /** Delay between each ping, based on Apple developer guide. */
    val PING_DELAY = Duration.ofSeconds(5)

    private const val TAG = "PeriodicPingFeature"

    private val FEATURE_ID = ParcelUuid.fromString("9eb6528d-bb65-4239-b196-6789196cf2a9")
    private val PING_MESSAGE =
      PeriodicPingMessage.newBuilder().setMessageType(MessageType.PING).build()
  }
}
