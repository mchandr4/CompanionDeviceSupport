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

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import androidx.annotation.VisibleForTesting
import com.google.android.connecteddevice.model.TransportProtocols
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.IProtocolDelegate
import com.google.android.connecteddevice.transport.ble.BlePeripheralProtocol
import com.google.android.connecteddevice.transport.ble.OnDeviceBlePeripheralManager
import com.google.android.connecteddevice.transport.eap.EapProtocol
import com.google.android.connecteddevice.transport.proxy.NetworkSocketFactory
import com.google.android.connecteddevice.transport.proxy.ProxyBlePeripheralManager
import com.google.android.connecteddevice.transport.spp.SppProtocol
import com.google.android.connecteddevice.util.EventLog
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logi
import com.google.android.connecteddevice.util.SafeLog.logw
import com.google.android.connecteddevice.util.aliveOrNull
import java.lang.IllegalArgumentException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Service for hosting all protocol processes. This service must run in the foreground user's
 * context to properly receive runtime permissions.
 */
open class TransportService : MetaDataService() {

  private val scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

  /** Maps the currently supported protocol names to the actual protocol implementation. */
  @VisibleForTesting
  internal val initializedProtocols = ConcurrentHashMap<String, ConnectionProtocol>()

  private lateinit var supportedOobProtocolsName: Set<String>

  private lateinit var supportedTransportProtocolsName: Set<String>

  private lateinit var bluetoothManager: BluetoothManager

  private val bluetoothBroadcastReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent) {
        if (BluetoothAdapter.ACTION_STATE_CHANGED == intent.action) {
          onBluetoothStateChanged(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1))
        }
      }
    }

  private val serviceConnection =
    object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName, service: IBinder) {
        delegate = IProtocolDelegate.Stub.asInterface(service)
        logd(TAG, "Successfully bound to service and received delegate.")
        initializeProtocols(supportedTransportProtocolsName + supportedOobProtocolsName)
      }

      override fun onServiceDisconnected(name: ComponentName?) {
        delegate = null
        bindToService()
      }

      override fun onNullBinding(name: ComponentName?) {
        loge(
          TAG,
          "Received a null binding when attempting to connect to service. No protocols will " +
            "connect and the platform will be inoperable. Please verify there have not been " +
            "any modifications to the ConnectedDeviceService's onBind() method."
        )
      }
    }

  @VisibleForTesting internal var delegate: IProtocolDelegate? = null

  @SuppressLint("UnprotectedReceiver") // Broadcasts are protected.
  override fun onCreate() {
    super.onCreate()
    logd(TAG, "Service created.")
    bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    supportedTransportProtocolsName =
      getMetaStringArray(META_SUPPORTED_TRANSPORT_PROTOCOLS, DEFAULT_TRANSPORT_PROTOCOLS).toSet()
    supportedOobProtocolsName =
      getMetaStringArray(META_SUPPORTED_OOB_CHANNELS, emptyArray()).toSet()
    if (supportedTransportProtocolsName.isEmpty()) {
      loge(
        TAG,
        "Transport protocols are empty. There must be at least one protocol provided to start " +
          "this service. Reverting to default values."
      )
      supportedTransportProtocolsName = DEFAULT_TRANSPORT_PROTOCOLS.toSet()
    }

    registerReceiver(
      bluetoothBroadcastReceiver,
      IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
    )

    if (delegate == null) {
      bindToService()
    }
  }

  override fun onDestroy() {
    logd(TAG, "Service destroyed.")
    disconnectProtocols(initializedProtocols.keys)
    try {
      unregisterReceiver(bluetoothBroadcastReceiver)
    } catch (e: IllegalArgumentException) {
      logw(TAG, "Attempted to unregister a receiver that had not been registered.")
    }
    unbindService(serviceConnection)
    super.onDestroy()
  }

  private fun bindToService() {
    val intent =
      Intent(this, ConnectedDeviceService::class.java).apply { action = ACTION_BIND_PROTOCOL }
    bindService(intent, serviceConnection, /* flags= */ 0)
  }

  private fun onBluetoothStateChanged(state: Int) {
    logd(TAG, "The bluetooth state has changed to $state.")
    val supportedBluetoothProtocols =
      (supportedTransportProtocolsName + supportedOobProtocolsName).intersect(BLUETOOTH_PROTOCOLS)
    when (state) {
      BluetoothAdapter.STATE_ON -> {
        EventLog.onBleOn()
        initializeProtocols(supportedBluetoothProtocols)
      }
      BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
        disconnectProtocols(supportedBluetoothProtocols)
      }
    }
  }

  private fun initializeProtocols(targetProtocols: Set<String>) {
    val delegate = delegate?.aliveOrNull()
    if (delegate == null) {
      logw(TAG, "Delegate is not currently connected. Unable to initialize protocols.")
      return
    }
    logd(TAG, "Processing ${targetProtocols.size} supported protocols.")
    for (protocol in targetProtocols) {
      logd(TAG, "Adding protocol $protocol to supported protocols.")
      addBluetoothProtocol(protocol)
    }
  }

  private fun addBluetoothProtocol(protocolName: String) {
    if (!bluetoothManager.adapter.isEnabled) {
      logd(TAG, "Bluetooth adapter is currently disabled. Skipping $protocolName.")
      return
    }
    if (initializedProtocols.containsKey(protocolName)) {
      logd(TAG, "A $protocolName has already been created. Aborting.")
      return
    }
    val protocol: ConnectionProtocol =
      when (protocolName) {
        TransportProtocols.PROTOCOL_BLE_PERIPHERAL -> createBlePeripheralProtocol()
        TransportProtocols.PROTOCOL_SPP -> createSppProtocol()
        TransportProtocols.PROTOCOL_EAP -> createEapProtocol()
        else -> {
          loge(TAG, "Protocol type $protocolName is not recognized. Ignoring.")
          return
        }
      }
    initializedProtocols[protocolName] = protocol
    if (protocolName in supportedTransportProtocolsName) {
      delegate?.addProtocol(protocol)
    }
    if (protocolName in supportedOobProtocolsName) {
      delegate?.addOobProtocol(protocol)
    }
  }

  private fun createBlePeripheralProtocol(): BlePeripheralProtocol {
    val reconnectUuid =
      UUID.fromString(getMetaString(META_RECONNECT_SERVICE_UUID, DEFAULT_RECONNECT_UUID))
    val reconnectDataUuid =
      UUID.fromString(getMetaString(META_RECONNECT_DATA_UUID, DEFAULT_RECONNECT_DATA_UUID))
    val advertiseDataCharacteristicUuid =
      UUID.fromString(
        getMetaString(
          META_ADVERTISE_DATA_CHARACTERISTIC_UUID,
          DEFAULT_ADVERTISE_DATA_CHARACTERISTIC_UUID
        )
      )
    val writeUuid = UUID.fromString(getMetaString(META_WRITE_UUID, DEFAULT_WRITE_UUID))
    val readUuid = UUID.fromString(getMetaString(META_READ_UUID, DEFAULT_READ_UUID))
    val defaultMtuSize = getMetaInt(META_DEFAULT_MTU_BYTES, DEFAULT_MTU_SIZE)
    val isProxyEnabled = getMetaBoolean(META_ENABLE_PROXY, PROXY_ENABLED_BY_DEFAULT)
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
    val maxSppPacketSize = getMetaInt(META_SPP_PACKET_BYTES, DEFAULT_SPP_PACKET_SIZE_BYTES)
    return SppProtocol(context = this, maxSppPacketSize)
  }

  private fun createEapProtocol(): EapProtocol {
    val maxSppPacketSize = getMetaInt(META_SPP_PACKET_BYTES, DEFAULT_SPP_PACKET_SIZE_BYTES)
    val eapClientName = getMetaString(META_EAP_CLIENT_NAME, "")
    val eapServiceName = getMetaString(META_EAP_SERVICE_NAME, "")
    return EapProtocol(eapClientName, eapServiceName, maxSppPacketSize)
  }

  private fun disconnectProtocols(targetProtocols: Set<String>) {
    for (protocolName in targetProtocols) {
      val protocol = initializedProtocols[protocolName]
      if (protocol != null) {
        logd(TAG, "Disconnecting $protocolName.")
        protocol.reset()
        initializedProtocols.remove(protocolName)
        if (protocolName in supportedTransportProtocolsName) {
          delegate?.removeProtocol(protocol)
        }
        if (protocolName in supportedOobProtocolsName) {
          delegate?.removeOobProtocol(protocol)
        }
      }
    }
  }

  companion object {
    private const val TAG = "TransportService"

    /** Intent action used to bind for a [IProtocolDelegate]. */
    const val ACTION_BIND_PROTOCOL = "com.google.android.connecteddevice.BIND_PROTOCOL"

    /** `string-array` Supported transport protocols. */
    @VisibleForTesting
    internal const val META_SUPPORTED_TRANSPORT_PROTOCOLS =
      "com.google.android.connecteddevice.transport_protocols"

    @VisibleForTesting
    internal const val META_SUPPORTED_OOB_CHANNELS =
      "com.google.android.connecteddevice.supported_oob_channels"

    // The mac address randomly rotates every 7-15 minutes. To be safe, we will rotate our
    // reconnect advertisement every 6 minutes to avoid crossing a rotation.
    private val MAX_ADVERTISEMENT_DURATION = Duration.ofMinutes(6)

    private const val META_EAP_CLIENT_NAME =
      "com.google.android.connecteddevice.car_eap_client_name"

    private const val META_EAP_SERVICE_NAME =
      "com.google.android.connecteddevice.car_eap_service_name"

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

    private val DEFAULT_TRANSPORT_PROTOCOLS = arrayOf(TransportProtocols.PROTOCOL_BLE_PERIPHERAL)

    private const val PROXY_ENABLED_BY_DEFAULT = false

    private val BLUETOOTH_PROTOCOLS =
      setOf(
        TransportProtocols.PROTOCOL_BLE_PERIPHERAL,
        TransportProtocols.PROTOCOL_SPP,
        TransportProtocols.PROTOCOL_EAP
      )
  }
}
