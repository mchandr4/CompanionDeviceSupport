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

import com.google.android.connecteddevice.util.BinderUtils.registerBinderDiedListener
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.logw
import java.util.concurrent.CopyOnWriteArrayList

/** Delegate for registering protocols to the platform. */
class ProtocolDelegate : IProtocolDelegate.Stub() {
  private val _protocols = CopyOnWriteArrayList<IConnectionProtocol>()
  /** The list of currently attached [IConnectionProtocol]s. */
  val protocols: List<IConnectionProtocol>
    get() {
      return _protocols.toList()
    }

  private val _oobProtocols = CopyOnWriteArrayList<IConnectionProtocol>()

  /** Protocols that support OOB data exchange. */
  val oobProtocols: List<IConnectionProtocol>
    get() {
      return _oobProtocols.toList()
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
    val success =
      protocol.asBinder().registerBinderDiedListener {
        logd(
          TAG,
          "Remote OOB protocol $protocol process is dead; clear the reference of this protocol."
        )
        removeOobProtocol(protocol)
      }
    if (!success) {
      logw(TAG, "Protocol $protocol process already died. Ignore the request to add protocol.")
      return
    }

    _oobProtocols.add(protocol)
    logd(
      TAG,
      "Added a new OOB protocol. There are now ${_oobProtocols.size} attached OOB protocols."
    )
  }

  override fun removeOobProtocol(protocol: IConnectionProtocol) {
    for (current in _oobProtocols) {
      if (current.asBinder().equals(protocol.asBinder())) {
        _oobProtocols.remove(current)
        logd(
          TAG,
          "Removed a protocol. There are ${_oobProtocols.size} remaining attached protocols."
        )
      }
    }
  }

  override fun addProtocol(protocol: IConnectionProtocol) {
    val success =
      protocol.asBinder().registerBinderDiedListener {
        logd(TAG, "Remote protocol $protocol process dead, clear the reference this protocol.")
        removeProtocol(protocol)
      }
    if (!success) {
      logw(TAG, "Protocol $protocol process already died. Ignore the request to add protocol.")
      return
    }
    _protocols.add(protocol)
    logd(TAG, "Added a new protocol. There are now ${_protocols.size} attached protocols.")
    callback?.onProtocolAdded(protocol)
  }

  override fun removeProtocol(protocol: IConnectionProtocol) {
    for (current in _protocols) {
      if (current.asBinder().equals(protocol.asBinder())) {
        _protocols.remove(current)
        logd(TAG, "Removed a protocol. There are ${_protocols.size} remaining attached protocols.")
        callback?.onProtocolRemoved(protocol)
      }
    }
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
