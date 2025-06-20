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
package com.google.android.connecteddevice.connection

import com.google.android.connecteddevice.transport.ProtocolDevice

/** Factory for creating [ProtocolStream]. */
interface ProtocolStreamFactory {

  /** Creates a [ProtocolStream] with provided [device]. */
  fun createProtocolStream(device: ProtocolDevice): ProtocolStream
}

/** Implementation of [ProtocolStreamFactory]. */
class ProtocolStreamFactoryImpl : ProtocolStreamFactory {

  override fun createProtocolStream(device: ProtocolDevice): ProtocolStream = ProtocolStream(device)
}
