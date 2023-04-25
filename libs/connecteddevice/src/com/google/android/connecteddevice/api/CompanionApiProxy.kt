/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.os.ParcelUuid
import com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener
import com.google.android.connecteddevice.api.SafeConnector.QueryCallback
import android.os.IInterface

/** Wrapper class for ensuring any Feature Coordinator APIs called are version-compliant. */
interface CompanionApiProxy {

  val queryCallbacks: MutableMap<Int, QueryCallback>

  val queryResponseRecipients: MutableMap<Int, ParcelUuid>

  val listener: ISafeOnLogRequestedListener

  /**
   * Retrieves connected devices associated with the current user.
   *
   * @return list of identifiers of connected devices associated with the current user, null if this
   *   action is not supported by the on-device companion platform.
   */
  fun getConnectedDevices(): List<String>?

  /**
   * Sends a message to a connected device.
   *
   * @param deviceId Identifier for the recipient connected device.
   * @param message Message to send to the connected device.
   * @return true if on-device companion platform supports this action, false otherwise.
   */
  fun sendMessage(deviceId: String, message: ByteArray): Boolean

  /**
   * Processes log records in the logger with given identifier so it can be combined with log
   * records from other loggers.
   *
   * @param loggerId of the logger.
   * @param logRecords to process.
   * @return true if on-device companion platform supports this action, false otherwise.
   */
  fun processLogRecords(loggerId: Int, logRecords: ByteArray): Boolean

  /**
   * Retrieves all associated devices.
   *
   * @param listener that will be notified when the associated devices are retrieved.
   * @return true if on-device companion platform supports this action, false otherwise.
   */
  fun retrieveAssociatedDevices(listener: IInterface): Boolean

  /**
   * Clears internal status, no Companion events will be notified after this call.
   */
  fun cleanUp()

  companion object {
    /** All legacy APIs will be designated version zero. */
    const val LEGACY_VERSION = 0
  }
}
