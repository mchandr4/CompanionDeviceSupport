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

package com.google.android.connecteddevice.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.annotation.VisibleForTesting
import com.google.android.connecteddevice.model.TransportProtocols
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.ble.BlePeripheralProtocol
import com.google.android.connecteddevice.transport.ble.OnDeviceBlePeripheralManager
import com.google.android.connecteddevice.transport.proxy.NetworkSocketFactory
import com.google.android.connecteddevice.transport.proxy.ProxyBlePeripheralManager
import com.google.android.connecteddevice.transport.spp.SppProtocol
import com.google.android.connecteddevice.util.MetaDataParser
import com.google.android.connecteddevice.util.MetaDataProvider
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.logi
import com.google.android.connecteddevice.util.SafeLog.logw
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Service for hosting all protocol processes. This service must run in the foreground user's
 * context to properly receive runtime permissions.
 */
open class TransportService : Service() {

  private val scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

  @VisibleForTesting internal lateinit var metaDataProvider: MetaDataProvider
  @VisibleForTesting internal lateinit var protocolRegister: ProtocolRegistry

  override fun onCreate() {
    super.onCreate()
    logd(TAG, "Service created.")
    metaDataProvider = MetaDataParser(this)
    protocolRegister = CompanionProtocolRegistry(this, metaDataProvider, this::initializeProtocols)
  }

  override fun onDestroy() {
    protocolRegister.cleanUp()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? {
    return null
  }

  @VisibleForTesting
  internal fun initializeProtocols(protocols: Set<String>): Map<String, ConnectionProtocol> {
    logd(TAG, "Initializing ${protocols.size} supported protocols.")
    val initializedProtocols = mutableMapOf<String, ConnectionProtocol>()
    for (protocolName in protocols) {
      val protocol: ConnectionProtocol =
        when (protocolName) {
          TransportProtocols.PROTOCOL_BLE_PERIPHERAL -> createBlePeripheralProtocol()
          TransportProtocols.PROTOCOL_SPP -> createSppProtocol()
          else -> {
            logw(TAG, "Protocol type $protocolName is not recognized. Ignoring.")
            continue
          }
        }
      logd(TAG, "Adding protocol $protocolName to supported protocols.")
      initializedProtocols[protocolName] = protocol
    }
    return initializedProtocols
  }

  private fun createBlePeripheralProtocol(): BlePeripheralProtocol {
    val reconnectUuid =
      UUID.fromString(
        metaDataProvider.getMetaString(META_RECONNECT_SERVICE_UUID, DEFAULT_RECONNECT_UUID)
      )
    val reconnectDataUuid =
      UUID.fromString(
        metaDataProvider.getMetaString(META_RECONNECT_DATA_UUID, DEFAULT_RECONNECT_DATA_UUID)
      )
    val advertiseDataCharacteristicUuid =
      UUID.fromString(
        metaDataProvider.getMetaString(
          META_ADVERTISE_DATA_CHARACTERISTIC_UUID,
          DEFAULT_ADVERTISE_DATA_CHARACTERISTIC_UUID
        )
      )
    val writeUuid =
      UUID.fromString(metaDataProvider.getMetaString(META_WRITE_UUID, DEFAULT_WRITE_UUID))
    val readUuid =
      UUID.fromString(metaDataProvider.getMetaString(META_READ_UUID, DEFAULT_READ_UUID))
    val defaultMtuSize = metaDataProvider.getMetaInt(META_DEFAULT_MTU_BYTES, DEFAULT_MTU_SIZE)
    val isProxyEnabled =
      metaDataProvider.getMetaBoolean(META_ENABLE_PROXY, PROXY_ENABLED_BY_DEFAULT)
    val blePeripheralManager =
      if (isProxyEnabled) {
        logi(TAG, "Initializing with ProxyBlePeripheralManager")
        ProxyBlePeripheralManager(NetworkSocketFactory(this), scheduledExecutorService)
      } else {
        OnDeviceBlePeripheralManager(this)
      }
    return BlePeripheralProtocol(
      blePeripheralManager,
      reconnectUuid,
      reconnectDataUuid,
      advertiseDataCharacteristicUuid,
      writeUuid,
      readUuid,
      MAX_ADVERTISEMENT_DURATION,
      defaultMtuSize
    )
  }

  private fun createSppProtocol(): SppProtocol {
    val maxSppPacketSize =
      metaDataProvider.getMetaInt(META_SPP_PACKET_BYTES, DEFAULT_SPP_PACKET_SIZE_BYTES)
    return SppProtocol(context = this, maxSppPacketSize)
  }

  companion object {
    private const val TAG = "TransportService"

    // The mac address randomly rotates every 7-15 minutes. To be safe, we will rotate our
    // reconnect advertisement every 6 minutes to avoid crossing a rotation.
    private val MAX_ADVERTISEMENT_DURATION = Duration.ofMinutes(6)

    /** `String` UUID for reconnection advertisement. */
    private const val META_RECONNECT_SERVICE_UUID =
      "com.google.android.connecteddevice.reconnect_service_uuid"

    /** `String` UUID for extra reconnection advertisement data. */
    private const val META_RECONNECT_DATA_UUID =
      "com.google.android.connecteddevice.reconnect_data_uuid"

    /** `String` UUID for characteristic that contains the advertise data. */
    private const val META_ADVERTISE_DATA_CHARACTERISTIC_UUID =
      "com.google.android.connecteddevice.advertise_data_characteristic_uuid"

    /** `String` UUID for write characteristic. */
    private const val META_WRITE_UUID = "com.google.android.connecteddevice.write_uuid"

    /** `String` UUID for read characteristic. */
    private const val META_READ_UUID = "com.google.android.connecteddevice.read_uuid"

    /** `int` Number of bytes for the default BLE MTU size. */
    private const val META_DEFAULT_MTU_BYTES =
      "com.google.android.connecteddevice.default_mtu_bytes"

    /** `boolean` Enable BLE proxy. */
    private const val META_ENABLE_PROXY = "com.google.android.connecteddevice.enable_proxy"

    /** `int` Maximum number of bytes each SPP packet can contain. */
    private const val META_SPP_PACKET_BYTES = "com.google.android.connecteddevice.spp_packet_bytes"

    private const val DEFAULT_RECONNECT_UUID = "000000e0-0000-1000-8000-00805f9b34fb"

    private const val DEFAULT_RECONNECT_DATA_UUID = "00000020-0000-1000-8000-00805f9b34fb"

    private const val DEFAULT_ADVERTISE_DATA_CHARACTERISTIC_UUID =
      "24289b40-af40-4149-a5f4-878ccff87566"

    private const val DEFAULT_WRITE_UUID = "5e2a68a5-27be-43f9-8d1e-4546976fabd7"

    private const val DEFAULT_READ_UUID = "5e2a68a6-27be-43f9-8d1e-4546976fabd7"

    private const val DEFAULT_MTU_SIZE = 185 // Max allowed for iOS.

    // TODO(b/166538373): Find a suitable packet size for SPP rather than the arbitrary number.
    private const val DEFAULT_SPP_PACKET_SIZE_BYTES = 700

    private const val PROXY_ENABLED_BY_DEFAULT = false
  }
}
