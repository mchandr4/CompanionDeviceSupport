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
package com.google.android.companiondevicesupport.eap

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.annotation.VisibleForTesting
import com.google.android.connecteddevice.model.TransportProtocols
import com.google.android.connecteddevice.service.CompanionProtocolRegistry
import com.google.android.connecteddevice.service.ProtocolRegistry
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.util.MetaDataParser
import com.google.android.connecteddevice.util.MetaDataProvider
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.logw

/** Service for hosting EAP protocol sessions. */
internal open class EapService : Service() {
  @VisibleForTesting internal lateinit var metaDataProvider: MetaDataProvider
  @VisibleForTesting internal lateinit var protocolRegistry: ProtocolRegistry

  override fun onCreate() {
    super.onCreate()
    logd(TAG, "Service created.")
    metaDataProvider = MetaDataParser(this)
    protocolRegistry = CompanionProtocolRegistry(this, metaDataProvider, this::initializeProtocols)
  }

  override fun onBind(intent: Intent?): IBinder? {
    return null
  }

  override fun onDestroy() {
    protocolRegistry.cleanUp()
    super.onDestroy()
  }

  @VisibleForTesting
  /** Returns the supported protocol name to protocol map. */
  internal fun initializeProtocols(protocols: Set<String>): Map<String, ConnectionProtocol> {
    if (!protocols.contains(TransportProtocols.PROTOCOL_EAP)) {
      logw(TAG, "EAP is not the supported protocol, ignore.")
      return emptyMap()
    }
    val maxSppPacketSize =
      metaDataProvider.getMetaInt(META_SPP_PACKET_BYTES, DEFAULT_SPP_PACKET_SIZE_BYTES)
    val eapClientName = metaDataProvider.getMetaString(META_EAP_CLIENT_NAME, "")
    val eapServiceName = metaDataProvider.getMetaString(META_EAP_SERVICE_NAME, "")
    if (eapClientName.isEmpty() || eapServiceName.isEmpty()) {
      logw(TAG, "Invalid EAP service configuration. Ignore.")
      return emptyMap()
    }
    return mapOf(
      TransportProtocols.PROTOCOL_EAP to
        EapProtocol(eapClientName, eapServiceName, maxSppPacketSize)
    )
  }
  companion object {
    private const val TAG = "EapService"
    private const val META_EAP_CLIENT_NAME =
      "com.google.android.connecteddevice.car_eap_client_name"

    private const val META_EAP_SERVICE_NAME =
      "com.google.android.connecteddevice.car_eap_service_name"
    // TODO(b/166538373): Find a suitable packet size for SPP rather than the arbitrary number.
    private const val DEFAULT_SPP_PACKET_SIZE_BYTES = 700

    /** `int` Maximum number of bytes each SPP packet can contain. */
    private const val META_SPP_PACKET_BYTES = "com.google.android.connecteddevice.spp_packet_bytes"
  }
}
