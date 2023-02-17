package com.google.android.connecteddevice.transport.ble.testable

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothManager
import android.content.Context

/**
 * Delegates calls into [BluetoothManager]. Unit test is not available for this class so avoid
 * applying complex logic in this class.
 */
open class BluetoothManagerHandler(private val context: Context) {
  private val manager = context.getSystemService(BluetoothManager::class.java)

  open val adapter: BluetoothAdapter? = manager.adapter

  /** Initiates a new [BluetoothGattServer]. Returns `null` if it fails. */
  open fun openGattServer(
    gattServerCallback: BluetoothGattServerCallback
  ): BluetoothGattServerHandler? {
    val gattServer = manager.openGattServer(context, gattServerCallback) ?: return null
    return BluetoothGattServerHandler(gattServer)
  }
}
