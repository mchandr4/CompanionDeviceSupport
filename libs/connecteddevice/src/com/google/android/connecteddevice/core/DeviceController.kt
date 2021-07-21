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

import com.google.android.connecteddevice.api.IAssociationCallback
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.model.DeviceMessage
import java.util.UUID
import java.util.concurrent.Executor

/** Controller in charge of handling device connections. */
interface DeviceController {
  /** The devices that are currently connected. */
  val connectedDevices: List<ConnectedDevice>

  /** Start the controller and initiate connections to known devices. */
  fun start()

  /** Disconnect all devices and reset state. */
  fun reset()

  /** Start to connect to associated devices. */
  fun initiateConnectionToDevice(deviceId: UUID)

  /** Start the association with a new device. */
  fun startAssociation(nameForAssociation: String, callback: IAssociationCallback)

  /** Notify that the user has accepted a pairing code. */
  fun notifyVerificationCodeAccepted()

  /**
   * Send the [message] to a connected device with id [deviceId] and returns `true` if the send was
   * successful.
   *
   * If `false` is returned, then it could mean that the given device was not currently connected or
   * is waiting for its secure channel to be set up. [isReadyToSendMessage] should return `true` for
   * the given `deviceId` before calling this method with the same `deviceId`.
   */
  fun sendMessage(deviceId: UUID, message: DeviceMessage): Boolean

  /**
   * Returns `true` if a secure channel has been established for device with id [deviceId]. A value
   * of `true` means that [sendMessage] can be called for the device.
   */
  fun isReadyToSendMessage(deviceId: UUID): Boolean

  /** Disconnect the provided device from this controller. */
  fun disconnectDevice(deviceId: UUID)

  /** Register a [Callback] to be notified on the [Executor]. */
  fun registerCallback(callback: Callback, executor: Executor)

  /** Stop the association process with any device. */
  fun stopAssociation()

  /**
   * Unregister a callback.
   *
   * @param callback The [Callback] to unregister.
   */
  fun unregisterCallback(callback: Callback)

  /** Callback for triggered events. */
  interface Callback {
    /** Invoked when the [connectedDevice] has connected. */
    fun onDeviceConnected(connectedDevice: ConnectedDevice) {}

    /** Invoked when the [connectedDevice] has disconnected. */
    fun onDeviceDisconnected(connectedDevice: ConnectedDevice) {}

    /** Triggered when the [connectedDevice] has established encryption for secure communication. */
    fun onSecureChannelEstablished(connectedDevice: ConnectedDevice) {}

    /** Triggered when a new [message] is received from the [connectedDevice]. */
    fun onMessageReceived(connectedDevice: ConnectedDevice, message: DeviceMessage) {}
  }
}
