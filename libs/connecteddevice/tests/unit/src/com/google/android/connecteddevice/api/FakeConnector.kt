package com.google.android.connecteddevice.api

import android.os.IBinder
import android.os.ParcelUuid
import com.google.android.connecteddevice.model.ConnectedDevice

/** Fake implementation of a [Connector] to be used in tests. */
open class FakeConnector : Connector {
  override var featureId: ParcelUuid? = null
  override var callback: Connector.Callback? = null
  override val connectedDevices = mutableListOf<ConnectedDevice>()
  override var isConnected = false

  override fun connect() {
    callback?.onConnected()
    isConnected = true
  }

  override fun disconnect() {
    val wasConnected = isConnected
    isConnected = false
    if (wasConnected) {
      callback?.onDisconnected()
    }
  }

  override fun binderForAction(action: String): IBinder? = null

  override fun sendMessageSecurely(deviceId: String, message: ByteArray) {}

  override fun sendMessageSecurely(device: ConnectedDevice, message: ByteArray) {}

  override fun sendQuerySecurely(
    deviceId: String,
    request: ByteArray,
    parameters: ByteArray?,
    callback: Connector.QueryCallback
  ) {}

  override fun sendQuerySecurely(
    device: ConnectedDevice,
    request: ByteArray,
    parameters: ByteArray?,
    callback: Connector.QueryCallback
  ) {}

  override fun respondToQuerySecurely(
    device: ConnectedDevice,
    queryId: Int,
    success: Boolean,
    response: ByteArray?
  ) {}

  override fun getConnectedDeviceById(deviceId: String): ConnectedDevice? {
    return connectedDevices.find { it.deviceId == deviceId }
  }

  override fun retrieveCompanionApplicationName(
    device: ConnectedDevice,
    callback: Connector.AppNameCallback
  ) {}
}
