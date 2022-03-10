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

package com.google.android.connecteddevice.oob

import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder

/** Factory that creates [OobChannel]. */
open class OobChannelFactory(private val sppBinder: ConnectedDeviceSppDelegateBinder) {
  /**
   * Returns [OobChannel] of the type [oobChannelType].
   *
   * Throws [IllegalArgumentException] if the [oobChannelType] passed is not supported.
   */
  open fun createOobChannel(oobChannelType: String): OobChannel =
    when (oobChannelType) {
      BT_RFCOMM -> BluetoothRfcommChannel(sppBinder)
      PRE_ASSOCIATION -> PassThroughChannel()
      else -> throw IllegalArgumentException("Unknown OOB channel type: $oobChannelType")
    }

  companion object {
    /** Should be passed to [createOobChannel] to create corresponding OOB channel. */
    const val BT_RFCOMM = "BT_RFCOMM"
    const val PRE_ASSOCIATION = "PRE_ASSOCIATION"
  }
}
