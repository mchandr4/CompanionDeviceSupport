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
package com.google.android.connecteddevice.transport

/** Representation of a single device within a [ConnectionProtocol]. */
data class ProtocolDevice(val protocol: IConnectionProtocol, val protocolId: String) {
  // IConnectionProtocol is an AIDL generated interface. It'll always be logged as object address.
  override fun toString(): String = "ProtocolDevice($protocolId)"
}
