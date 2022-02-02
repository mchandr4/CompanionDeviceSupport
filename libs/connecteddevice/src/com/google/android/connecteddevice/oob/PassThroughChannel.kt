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

import com.google.android.connecteddevice.transport.ProtocolDevice

/**
 * A out of band data exchange channel.
 *
 * This class performs no-op. It is a pass-through when call [completeOobDataExchange], it will
 * return `true` and issue [Callback.onOobExchangeSuccess] immediately.
 */
class PassThroughChannel : OobChannel {
  override fun completeOobDataExchange(
    protocolDevice: ProtocolDevice,
    callback: OobChannel.Callback
  ): Boolean {
    callback.onOobExchangeSuccess()
    return true
  }

  override fun sendOobData(oobData: ByteArray) {}

  override fun interrupt() {}
}
