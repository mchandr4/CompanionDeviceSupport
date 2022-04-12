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

package com.google.android.connecteddevice.transport

import com.google.android.connecteddevice.util.SafeLog.logd

/** Delegate for registering protocols to the platform. */
class ProtocolDelegate : IProtocolDelegate.Stub() {
  private val _protocols = mutableListOf<IConnectionProtocol>()
  /** The list of currently attached [IConnectionProtocol]s. */
  val protocols: List<IConnectionProtocol>
    get() {
      scrubDeadProtocols(_protocols)
      return _protocols
    }

  private val _oobProtocols = mutableListOf<IConnectionProtocol>()

  /** Protocols that support OOB data exchange. */
  val oobProtocols: List<IConnectionProtocol>
    get() {
      scrubDeadProtocols(_oobProtocols)
      return _oobProtocols
    }

  /** `true` if there are currently no general transport protocols attached. `false` otherwise. */
  val isEmpty: Boolean
    get() = protocols.isEmpty()

  /** `true` if there is at least one protocol currently attached. `false` otherwise. */
  val isNotEmpty: Boolean
    get() = protocols.isNotEmpty()

  /** Callback registered for protocol changes. */
  var callback: Callback? = null

  override fun addOobProtocol(protocol: IConnectionProtocol) {
    _oobProtocols.add(protocol)
    logd(
      TAG,
      "Added a new OOB protocol. There are now ${oobProtocols.size} attached OOB " + "protocols."
    )
  }

  override fun removeOobProtocol(protocol: IConnectionProtocol) {
    scrubDeadProtocols(_oobProtocols)
    _oobProtocols.removeAll { it.asBinder() == protocol.asBinder() }
    logd(
      TAG,
      "Removed a OOB protocol. There are ${oobProtocols.size} remaining OOB attached " +
        "protocols."
    )
  }

  override fun addProtocol(protocol: IConnectionProtocol) {
    _protocols.add(protocol)
    logd(TAG, "Added a new protocol. There are now ${protocols.size} attached protocols.")
    callback?.onProtocolAdded(protocol)
  }

  override fun removeProtocol(protocol: IConnectionProtocol) {
    scrubDeadProtocols(_protocols)
    if (!_protocols.removeAll { it.asBinder() == protocol.asBinder() }) {
      return
    }
    logd(TAG, "Removed a protocol. There are ${protocols.size} remaining attached protocols.")
    callback?.onProtocolRemoved(protocol)
  }

  private fun scrubDeadProtocols(protocols: MutableList<IConnectionProtocol>) =
    protocols.removeAll {
      val isNotAlive = !it.asBinder().isBinderAlive
      if (isNotAlive) {
        callback?.onProtocolRemoved(it)
      }
      isNotAlive
    }

  /** Callback to be invoked for protocol changes. */
  interface Callback {
    /** Invoked when a protocol has been added. */
    fun onProtocolAdded(protocol: IConnectionProtocol)

    /** Invoked when a protocol has been removed. */
    fun onProtocolRemoved(protocol: IConnectionProtocol)
  }

  companion object {
    private const val TAG = "ProtocolDelegate"
  }
}
