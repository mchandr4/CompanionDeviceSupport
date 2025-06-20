package com.google.android.connecteddevice.api

import android.os.IBinder
import android.os.ParcelUuid
import com.google.android.connecteddevice.model.AssociatedDevice
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.common.util.concurrent.ListenableFuture
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future

/** Fake implementation of a [Connector] to be used in tests. */
open class FakeConnector : Connector {
  /** Current user id for testing associated devices for other users. */
  var currentUserId = 10

  /**
   * Fakes the status of a device that has cached the status.
   *
   * Used by [isFeatureSupportedCached].
   */
  var isFeatureSupportedCachedByDevice = mutableSetOf<String>()

  /**
   * Fakes the status of a device that requires sending a query and receiving the response.
   *
   * Used by [isFeatureSupported].
   */
  var isFeatureSupportedByDevice = mutableSetOf<String>()

  /** List of associated devices to be returned in [retrieveAssociatedDevices] or user variants. */
  private val associatedDevices = mutableListOf<AssociatedDevice>()

  override var featureId: ParcelUuid? = null
  override var callback: Connector.Callback? = null
  override val connectedDevices = mutableListOf<ConnectedDevice>()
  override var isConnected = false

  val sentMessages = mutableMapOf<String, MutableList<ByteArray>>()

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

  override fun sendMessageSecurely(deviceId: String, message: ByteArray) {
    sentMessages.getOrPut(deviceId) { mutableListOf() }.add(message)
  }

  override fun sendMessageSecurely(device: ConnectedDevice, message: ByteArray) {
    sentMessages.getOrPut(device.deviceId) { mutableListOf() }.add(message)
  }

  override fun sendQuerySecurely(
    deviceId: String,
    request: ByteArray,
    parameters: ByteArray?,
    callback: Connector.QueryCallback,
  ) {}

  override fun sendQuerySecurely(
    device: ConnectedDevice,
    request: ByteArray,
    parameters: ByteArray?,
    callback: Connector.QueryCallback,
  ) {}

  override fun respondToQuerySecurely(
    device: ConnectedDevice,
    queryId: Int,
    success: Boolean,
    response: ByteArray?,
  ) {}

  override suspend fun isFeatureSupported(device: ConnectedDevice): Boolean =
    isFeatureSupportedByDevice.contains(device.deviceId)

  override suspend fun queryFeatureSupportStatuses(
    device: ConnectedDevice,
    queriedFeatures: List<UUID>,
  ): List<Pair<UUID, Boolean>> = emptyList()

  override fun isFeatureSupportedFuture(device: ConnectedDevice): ListenableFuture<Boolean> {
    return CoroutineScope(Dispatchers.Main).future { isFeatureSupported(device) }
  }

  override fun getConnectedDeviceById(deviceId: String): ConnectedDevice? {
    return connectedDevices.find { it.deviceId == deviceId }
  }

  override fun retrieveCompanionApplicationName(
    device: ConnectedDevice,
    callback: Connector.AppNameCallback,
  ) {}

  override fun startAssociation(callback: IAssociationCallback) {}

  override fun startAssociation(identifier: ParcelUuid, callback: IAssociationCallback) {}

  override fun stopAssociation() {}

  override fun acceptVerification() {}

  override fun removeAssociatedDevice(deviceId: String) {}

  override fun enableAssociatedDeviceConnection(deviceId: String) {}

  override fun disableAssociatedDeviceConnection(deviceId: String) {}

  override fun retrieveAssociatedDevices(listener: IOnAssociatedDevicesRetrievedListener) {
    listener.onAssociatedDevicesRetrieved(associatedDevices)
  }

  override fun retrieveAssociatedDevicesForDriver(listener: IOnAssociatedDevicesRetrievedListener) {
    listener.onAssociatedDevicesRetrieved(associatedDevices.filter { it.userId == currentUserId })
  }

  override fun retrieveAssociatedDevicesForPassengers(
    listener: IOnAssociatedDevicesRetrievedListener
  ) {
    listener.onAssociatedDevicesRetrieved(associatedDevices.filter { it.userId != currentUserId })
  }

  override fun claimAssociatedDevice(deviceId: String) {
    val device = associatedDevices.firstOrNull { it.id == deviceId } ?: return
    associatedDevices.remove(device)
    associatedDevices.add(device.cloneWith(currentUserId))
  }

  override fun removeAssociatedDeviceClaim(deviceId: String) {
    val device = associatedDevices.firstOrNull { it.id == deviceId } ?: return
    associatedDevices.remove(device)
    associatedDevices.add(device.cloneWith(AssociatedDevice.UNCLAIMED_USER_ID))
  }

  override fun isFeatureSupportedCached(device: ConnectedDevice): Boolean? =
    isFeatureSupportedCachedByDevice.contains(device.deviceId)

  fun addAssociatedDevice(device: AssociatedDevice) {
    associatedDevices.add(device)
    callback?.onAssociatedDeviceAdded(device)
  }

  fun removeAssociatedDevice(device: AssociatedDevice) {
    if (associatedDevices.remove(device)) {
      callback?.onAssociatedDeviceRemoved(device)
    }
  }

  private fun AssociatedDevice.cloneWith(userId: Int): AssociatedDevice =
    AssociatedDevice(id, address, name, isConnectionEnabled, userId)
}
