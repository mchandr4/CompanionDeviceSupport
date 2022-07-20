/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.connecteddevice.util

/** Logging class for collecting metrics. */
object EventLog {
  private const val TAG = "ConnectedDeviceEvent"

  /** Mark in log that the service has started. */
  @JvmStatic
  fun onServiceStarted() {
    SafeLog.logi(TAG, "SERVICE_STARTED")
  }

  /** Mark in the log that BLE is on. */
  @JvmStatic
  fun onBleOn() {
    SafeLog.logi(TAG, "BLE_ON")
  }

  /** Mark in the log that a device connected. */
  @JvmStatic
  fun onDeviceConnected() {
    SafeLog.logi(TAG, "DEVICE_CONNECTED")
  }

  /** Mark in the log that a secure channel has been established with a device. */
  @JvmStatic
  fun onSecureChannelEstablished() {
    SafeLog.logi(TAG, "SECURE_CHANNEL_ESTABLISHED")
  }

  /** Mark in the log that the first bytes for a message were received. */
  @JvmStatic
  fun onMessageStarted(messageId: Int) {
    SafeLog.logi(TAG, "MESSAGE_RECEIVED_FIRST_BYTE $messageId")
  }

  /** Mark in the log that a message was fully received. */
  @JvmStatic
  fun onMessageFullyReceived(messageId: Int, messageSize: Int) {
    SafeLog.logi(TAG, "MESSAGE_RECEIVED_LAST_BYTE $messageId $messageSize")
  }
}
