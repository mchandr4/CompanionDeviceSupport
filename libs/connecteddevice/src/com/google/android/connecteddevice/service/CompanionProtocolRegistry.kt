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
import androidx.core.content.getSystemService
import com.google.android.connecteddevice.model.TransportProtocols
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.IProtocolDelegate
import com.google.android.connecteddevice.util.EventLog
import com.google.android.connecteddevice.util.MetaDataParser
import com.google.android.connecteddevice.util.MetaDataProvider
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logw
import com.google.android.connecteddevice.util.aliveOrNull
import java.lang.IllegalArgumentException
import java.util.concurrent.ConcurrentHashMap

/**
 * Registers the [protocolInitializer] to the Companion platform so that the [ConnectionProtocol]
 * will be initiated and be used by the Companion App successfully when necessary.
 */
@SuppressLint("UnprotectedReceiver") // Broadcasts are protected.
class CompanionProtocolRegistry(
  private val context: Context,
  metaDataParser: MetaDataProvider = MetaDataParser(context),
  private val protocolFactory: (protocols: Set<String>) -> Map<String, ConnectionProtocol>,
) : ProtocolRegistry {
  private var bluetoothManager: BluetoothManager = context.getSystemService<BluetoothManager>()!!
  private val supportedMainProtocols =
    metaDataParser
      .getMetaStringArray(META_SUPPORTED_TRANSPORT_PROTOCOLS, DEFAULT_TRANSPORT_PROTOCOLS)
      .toSet()
  private val supportedOobProtocols =
    metaDataParser.getMetaStringArray(META_SUPPORTED_OOB_CHANNELS, emptyArray()).toSet()

  /** Maps the currently supported protocol names to the actual protocol implementation. */
  @VisibleForTesting
  internal val initializedProtocols = ConcurrentHashMap<String, ConnectionProtocol>()

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
        val allSupportedProtocols = supportedOobProtocols + supportedMainProtocols
        val nonBluetoothProtocols = allSupportedProtocols - BLUETOOTH_PROTOCOLS
        val protocols =
          if (bluetoothManager.adapter.isEnabled) {
            allSupportedProtocols
          } else {
            logd(TAG, "Bluetooth adapter is currently disabled. Skipping bluetooth protocols.")
            nonBluetoothProtocols
          }
        registerProtocols(protocolFactory(protocols))
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

  private var delegate: IProtocolDelegate? = null

  init {
    context.registerReceiver(
      bluetoothBroadcastReceiver,
      IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
    )
    bindToService()
  }

  private fun bindToService() {
    val intent =
      Intent(context, ConnectedDeviceService::class.java).apply { action = ACTION_BIND_PROTOCOL }
    context.bindService(intent, serviceConnection, /* flags= */ 0)
  }

  private fun onBluetoothStateChanged(state: Int) {
    logd(TAG, "The bluetooth state has changed to $state.")
    val supportedBluetoothProtocols: Set<String> =
      (supportedMainProtocols + supportedOobProtocols).intersect(BLUETOOTH_PROTOCOLS)
    when (state) {
      BluetoothAdapter.STATE_ON -> {
        EventLog.onBleOn()
        registerProtocols(
          protocolFactory(supportedBluetoothProtocols - initializedProtocols.keys)
        )
      }
      BluetoothAdapter.STATE_OFF,
      BluetoothAdapter.STATE_TURNING_OFF -> {
        disconnectProtocols(supportedBluetoothProtocols.intersect(initializedProtocols.keys))
      }
    }
  }

  private fun disconnectProtocols(protocols: Set<String>) {
    for (protocolName in protocols) {
      val protocol = initializedProtocols[protocolName]
      if (protocol != null) {
        logd(TAG, "Disconnecting $protocolName.")
        protocol.reset()
        initializedProtocols.remove(protocolName)
        if (delegate?.aliveOrNull() == null) {
          logw(
            TAG,
            "Protocol $protocolName disconnected, while delegate is null, failed to inform " +
              "remote service."
          )
          return
        }
        if (protocolName in supportedMainProtocols) {
          delegate?.aliveOrNull()?.removeProtocol(protocol)
        }
        if (protocolName in supportedOobProtocols) {
          delegate?.aliveOrNull()?.removeOobProtocol(protocol)
        }
      }
    }
  }

  /**
   * Registers the given [protocols] as valid protocols to be used for establishing a connection
   * with a remote device.
   */
  private fun registerProtocols(protocols: Map<String, ConnectionProtocol>) {
    val delegate = delegate?.aliveOrNull()
    if (delegate == null) {
      logw(TAG, "Delegate is not currently connected. Unable to initialize protocols.")
      return
    }
    protocols.filter { it.key !in initializedProtocols.keys }.forEach{ (name, protocol) ->
      initializedProtocols[name] = protocol
      if (name in supportedMainProtocols) {
        logd(TAG, "Add protocol $name to general transport protocol.")
        delegate.addProtocol(protocol)
      }
      if (name in supportedOobProtocols) {
        logd(TAG, "Add protocol $name to OOB transport protocol.")
        delegate.addOobProtocol(protocol)
      }
    }
  }

  override fun cleanUp() {
    logd(TAG, "Service destroyed.")
    disconnectProtocols(initializedProtocols.keys)
    try {
      context.unregisterReceiver(bluetoothBroadcastReceiver)
    } catch (e: IllegalArgumentException) {
      logw(TAG, "Attempted to unregister a receiver that had not been registered.")
    }
    context.unbindService(serviceConnection)
  }

  companion object {
    private const val TAG = "CompanionProtocolRegistry"
    /** Intent action used to bind for a [IProtocolDelegate]. */
    const val ACTION_BIND_PROTOCOL = "com.google.android.connecteddevice.BIND_PROTOCOL"

    private val BLUETOOTH_PROTOCOLS =
      setOf(
        TransportProtocols.PROTOCOL_BLE_PERIPHERAL,
        TransportProtocols.PROTOCOL_SPP,
        TransportProtocols.PROTOCOL_EAP
      )
    private val DEFAULT_TRANSPORT_PROTOCOLS = arrayOf(TransportProtocols.PROTOCOL_BLE_PERIPHERAL)
    /** `string-array` Supported transport protocols. */
    @VisibleForTesting
    internal const val META_SUPPORTED_TRANSPORT_PROTOCOLS =
      "com.google.android.connecteddevice.transport_protocols"

    @VisibleForTesting
    internal const val META_SUPPORTED_OOB_CHANNELS =
      "com.google.android.connecteddevice.supported_oob_channels"
  }
}
