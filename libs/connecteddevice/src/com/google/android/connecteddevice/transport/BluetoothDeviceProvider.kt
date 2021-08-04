package com.google.android.connecteddevice.transport

import android.bluetooth.BluetoothDevice

/**
 * Provide the capability to return the connected [BluetoothDevice] according to the provided
 * connection identifier.
 *
 * Should be implemented by [ConnectionProtocol]s which establish connections based on the
 * [BluetoothDevice] object.
 */
interface BluetoothDeviceProvider {
  /**
   * Returns the remote [BluetoothDevice] which has the corresponding [protocolId], otherwise
   * returns `null` if the [BluetoothDevice] is currently unavailable.
   */
  fun getBluetoothDeviceById(protocolId: String): BluetoothDevice?
}
