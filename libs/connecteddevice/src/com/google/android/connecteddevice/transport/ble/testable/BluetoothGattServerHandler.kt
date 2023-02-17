package com.google.android.connecteddevice.transport.ble.testable

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattService

/**
 * Delegates [BluetoothGattServer] calls. Unit test is not available for this class so avoid
 * applying complex logic in this class.
 */
open class BluetoothGattServerHandler(private val server: BluetoothGattServer) {
  /** Directly connect to the remote [device]. See more details in [BluetoothGattServer.connect]. */
  open fun connect(device: BluetoothDevice) {
    server.connect(device, /* autoConnect= */ false)
  }

  /** Delegates calls into [BluetoothGattServer.sendResponse] */
  open fun sendResponse(
    device: BluetoothDevice,
    requestId: Int,
    status: Int,
    offset: Int,
    value: ByteArray
  ) {
    server.sendResponse(device, requestId, status, offset, value)
  }

  /** Delegates calls into [BluetoothGattServer.notifyCharacteristicChanged] */
  open fun notifyCharacteristicChanged(
    device: BluetoothDevice,
    characteristic: BluetoothGattCharacteristic,
    confirm: Boolean
  ): Boolean {
    return server.notifyCharacteristicChanged(device, characteristic, confirm)
  }

  /** Delegates calls into [BluetoothGattServer.cancelConnection] */
  open fun cancelConnection(device: BluetoothDevice) {
    server.cancelConnection(device)
  }

  /** Delegates calls into [BluetoothGattServer.clearServices] */
  open fun clearServices() {
    server.clearServices()
  }

  /** Delegates calls into [BluetoothGattServer.close] */
  open fun close() {
    server.close()
  }

  /** Delegates calls into [BluetoothGattServer.addService] */
  open fun addService(service: BluetoothGattService) {
    server.addService(service)
  }

  override fun equals(other: Any?): Boolean =
    (other is BluetoothGattServerHandler) && server == other.server
}
