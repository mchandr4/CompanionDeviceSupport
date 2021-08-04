/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.android.connecteddevice.transport.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import androidx.annotation.VisibleForTesting
import com.google.android.connecteddevice.transport.BluetoothDeviceProvider
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.util.ByteUtils
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logw
import java.time.Duration
import java.util.UUID

/**
 * A ble peripheral communication protocol that provides actions and event notifications for
 * interacting with devices.
 */
class BlePeripheralProtocol(
  private val blePeripheralManager: BlePeripheralManager,
  private val associationServiceUuid: UUID,
  private val reconnectServiceUuid: UUID,
  private val reconnectDataUuid: UUID,
  advertiseDataCharacteristicUuid: UUID,
  writeCharacteristicUuid: UUID,
  readCharacteristicUuid: UUID,
  private val maxReconnectAdvertisementDuration: Duration,
  defaultMtuSize: Int,
) : ConnectionProtocol(), BluetoothDeviceProvider {
  override val isDeviceVerificationRequired = true

  private val writeCharacteristic =
    BluetoothGattCharacteristic(
      writeCharacteristicUuid,
      BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
      BluetoothGattCharacteristic.PERMISSION_READ
    )
  private val readCharacteristic =
    BluetoothGattCharacteristic(
      readCharacteristicUuid,
      BluetoothGattCharacteristic.PROPERTY_WRITE or
        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
      BluetoothGattCharacteristic.PERMISSION_WRITE
    )
  private val advertiseDataCharacteristic =
    BluetoothGattCharacteristic(
      advertiseDataCharacteristicUuid,
      BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
      BluetoothGattCharacteristic.PERMISSION_READ
    )

  private val peripheralCallback: BlePeripheralManager.Callback =
    object : BlePeripheralManager.Callback {
      override fun onDeviceNameRetrieved(deviceName: String?) {
        // TODO(b/187092343): Retrieve device name with query request
        // This callback will be removed
      }

      override fun onMtuSizeChanged(size: Int) {
        maxWriteSize = size - ATT_PROTOCOL_BYTES
        protocolId?.let { id ->
          deviceCallbacks[id]?.invoke { it.onDeviceMaxDataSizeChanged(id, maxWriteSize) }
        }
      }

      override fun onRemoteDeviceConnected(device: BluetoothDevice) {
        logd(TAG, "Remote device ${device.address} connected.")
        bluetoothDevice = device
        val currentProtocolId = createProtocolId()
        protocolId = currentProtocolId
        stopAdvertising()
        discoveryCallback?.onDeviceConnected(currentProtocolId)
        blePeripheralManager.addOnCharacteristicWriteListener(
          this@BlePeripheralProtocol::onCharacteristicWrite
        )
        blePeripheralManager.addOnCharacteristicReadListener(
          this@BlePeripheralProtocol::onCharacteristicRead
        )
        advertiseCallback = null
      }

      override fun onRemoteDeviceDisconnected(device: BluetoothDevice) {
        logd(TAG, "Remote device ${device.address} disconnected.")
        if (device != bluetoothDevice) {
          loge(
            TAG,
            "Disconnected from device ${device.address} that is not the expected device " +
              "${bluetoothDevice?.address}. Ignoring."
          )
          return
        }
        protocolId?.let { id -> deviceCallbacks[id]?.invoke { it.onDeviceDisconnected(id) } }
        reset()
      }
    }

  private val timeoutRunnable: Runnable = Runnable {
    logd(TAG, "Timeout period expired without a connection. Restarting advertisement.")
    stopAdvertising()
    val currentDeviceId = deviceId
    val currentDiscoveryCallback = discoveryCallback
    val currentChallenge = connectChallenge
    if (currentDeviceId != null && currentDiscoveryCallback != null && currentChallenge != null) {
      reset()
      startConnectionDiscovery(currentDeviceId, currentChallenge, currentDiscoveryCallback)
    }
  }

  private val timeoutHandlerThread: HandlerThread = HandlerThread(TIMEOUT_HANDLER_THREAD_NAME)
  private var maxWriteSize: Int = defaultMtuSize - ATT_PROTOCOL_BYTES
  private var deviceId: UUID? = null
  private var bluetoothDevice: BluetoothDevice? = null
  private var protocolId: String? = null
  private var connectChallenge: ConnectChallenge? = null
  private var advertiseCallback: AdvertiseCallback? = null
  private var discoveryCallback: DiscoveryCallback? = null
  private var timeoutHandler: Handler? = null
  private var dataSendCallback: DataSendCallback? = null

  init {
    writeCharacteristic.addDescriptor(createBluetoothGattDescriptor())
    readCharacteristic.addDescriptor(createBluetoothGattDescriptor())
    advertiseDataCharacteristic.addDescriptor(createBluetoothGattDescriptor())
    timeoutHandlerThread.start()
    timeoutHandler = Handler(timeoutHandlerThread.looper)
  }

  override fun startAssociationDiscovery(name: String, callback: DiscoveryCallback) {
    if (!isReadyToStartDiscovery()) {
      return
    }
    if (BluetoothAdapter.getDefaultAdapter() == null) {
      loge(TAG, "Bluetooth is unavailable on this device. Unable to start associating.")
      callback.onDiscoveryFailedToStart()
      return
    }
    reset()
    discoveryCallback = callback
    blePeripheralManager.registerCallback(peripheralCallback)
    val associationAdvertiseCallback =
      object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
          super.onStartSuccess(settingsInEffect)
          logd(TAG, "Successfully started advertising for association.")
          callback.onDiscoveryStartedSuccessfully()
        }

        override fun onStartFailure(errorCode: Int) {
          super.onStartFailure(errorCode)
          logd(TAG, "Failed to start advertising for association. Error code: $errorCode")
          callback.onDiscoveryFailedToStart()
        }
      }
    advertiseCallback = associationAdvertiseCallback
    startAdvertising(
      associationServiceUuid,
      associationAdvertiseCallback,
      scanResponse = ByteUtils.hexStringToByteArray(name),
      scanResponseUuid = reconnectDataUuid
    )
  }

  override fun startConnectionDiscovery(
    id: UUID,
    challenge: ConnectChallenge,
    callback: DiscoveryCallback
  ) {
    if (!isReadyToStartDiscovery()) {
      return
    }
    if (BluetoothAdapter.getDefaultAdapter() == null) {
      loge(TAG, "Bluetooth is unavailable on this device. Unable to start connection.")
      callback.onDiscoveryFailedToStart()
      return
    }
    reset()
    deviceId = id
    discoveryCallback = callback
    connectChallenge = challenge
    blePeripheralManager.registerCallback(peripheralCallback)
    val connectionAdvertiseCallback =
      object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
          super.onStartSuccess(settingsInEffect)
          timeoutHandler?.postDelayed(timeoutRunnable, maxReconnectAdvertisementDuration.toMillis())
          logd(TAG, "Successfully started advertising for device $id.")
          callback.onDiscoveryStartedSuccessfully()
        }

        override fun onStartFailure(errorCode: Int) {
          super.onStartFailure(errorCode)
          callback.onDiscoveryFailedToStart()
        }
      }
    advertiseCallback = connectionAdvertiseCallback
    val advertiseData = createConnectData(challenge)
    if (advertiseData == null) {
      loge(TAG, "Unable to create advertisement data. Aborting connecting.")
      callback.onDiscoveryFailedToStart()
      return
    }
    startAdvertising(
      reconnectServiceUuid,
      connectionAdvertiseCallback,
      advertiseData,
      reconnectDataUuid
    )
  }

  override fun stopAssociationDiscovery() {
    if (deviceId != null || advertiseCallback == null) {
      logd(TAG, "No association discovery is happening, ignoring.")
      return
    }
    reset()
  }

  override fun stopConnectionDiscovery(id: UUID) {
    if (id != deviceId || advertiseCallback == null) {
      logd(TAG, "No connection discovery is happening for device $id, ignoring.")
      return
    }
    reset()
  }

  override fun sendData(protocolId: String, data: ByteArray, callback: DataSendCallback?) {
    val device: BluetoothDevice? = bluetoothDevice
    if (device == null) {
      loge(TAG, "Failed to send data, no connected device.")
      callback?.onDataFailedToSend()
      return
    }
    if (protocolId != this.protocolId) {
      loge(TAG, "Failed to send data, no connected device matches protocol id $protocolId.")
      callback?.onDataFailedToSend()
      return
    }
    if (data.size > maxWriteSize) {
      loge(
        TAG,
        "Failed to send data, data size ${data.size} exceeds the max write size $maxWriteSize."
      )
      callback?.onDataFailedToSend()
      return
    }
    dataSendCallback = callback
    writeCharacteristic.value = data
    blePeripheralManager.notifyCharacteristicChanged(
      device,
      writeCharacteristic,
      /* confirm= */ false
    )
  }

  override fun disconnectDevice(protocolId: String) {
    if (protocolId == this.protocolId) {
      reset()
    }
  }

  override fun reset() {
    super.reset()
    logd(TAG, "Resetting protocol.")
    stopAdvertising()
    timeoutHandler?.removeCallbacks(timeoutRunnable)
    blePeripheralManager.cleanup()
    deviceId = null
    bluetoothDevice = null
    connectChallenge = null
    protocolId = null
    advertiseCallback = null
    discoveryCallback = null
    dataSendCallback = null
  }

  override fun getMaxWriteSize(protocolId: String): Int = maxWriteSize

  private fun isReadyToStartDiscovery(): Boolean {
    if (protocolId != null) {
      logd(
        TAG,
        "A device is already in connection with protocol id $protocolId." +
          " Ignore the start association request."
      )
      return false
    }
    if (advertiseCallback != null) {
      logd(TAG, "There is already a ongoing discovery. Ignore the start association request.")
      return false
    }
    return true
  }

  override fun getBluetoothDeviceById(protocolId: String) =
    if (protocolId == this.protocolId) bluetoothDevice else null

  private fun startAdvertising(
    serviceUuid: UUID,
    callback: AdvertiseCallback,
    advertiseData: ByteArray? = null,
    advertiseDataUuid: UUID? = null,
    scanResponse: ByteArray? = null,
    scanResponseUuid: UUID? = null
  ) {
    logd(TAG, "Starting advertising for service $serviceUuid.")
    val gattService = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
    gattService.addCharacteristic(writeCharacteristic)
    gattService.addCharacteristic(readCharacteristic)
    val uuid = ParcelUuid(serviceUuid)
    val advertiseDataBuilder = AdvertiseData.Builder().addServiceUuid(uuid)

    if (advertiseData != null) {
      val dataUuid = if (advertiseDataUuid != null) ParcelUuid(advertiseDataUuid) else uuid
      advertiseDataBuilder.addServiceData(dataUuid, advertiseData)

      // Also embed the advertise data into a fixed GATT service characteristic.
      advertiseDataCharacteristic.value = advertiseData
      gattService.addCharacteristic(advertiseDataCharacteristic)
    }

    val scanResponseBuilder = AdvertiseData.Builder()
    if (scanResponse != null && scanResponseUuid != null) {
      val scanResponseParcelUuid = ParcelUuid(scanResponseUuid)
      scanResponseBuilder.addServiceData(scanResponseParcelUuid, scanResponse)
    }

    blePeripheralManager.startAdvertising(
      gattService,
      advertiseDataBuilder.build(),
      scanResponseBuilder.build(),
      callback
    )
  }

  private fun stopAdvertising() {
    logd(TAG, "Attempting to stop advertising.")
    timeoutHandler?.removeCallbacks(timeoutRunnable)
    advertiseCallback?.let { blePeripheralManager.stopAdvertising(it) }
    advertiseCallback = null
  }

  private fun onCharacteristicRead(device: BluetoothDevice) {
    if (device != bluetoothDevice) {
      logw(
        TAG,
        "Received a read notification from device ${device.address} that is not the expected" +
          " device ${bluetoothDevice?.address}. Ignoring."
      )
      return
    }
    dataSendCallback?.onDataSentSuccessfully()
    dataSendCallback = null
  }

  private fun onCharacteristicWrite(
    device: BluetoothDevice,
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray
  ) {
    logd(TAG, "Received a message from device ${device.address}.")
    if (device != bluetoothDevice) {
      logw(
        TAG,
        "Received a message from device ${device.address} that is not the expected" +
          " device ${bluetoothDevice?.address}. Ignoring."
      )
      return
    }
    if (characteristic.uuid != readCharacteristic.uuid) {
      logw(
        TAG,
        "Received a write to a characteristic ${characteristic.uuid} that is not the expected" +
          " UUID ${readCharacteristic.uuid}. Ignoring."
      )
      return
    }
    protocolId?.let { id -> deviceCallbacks[id]?.invoke { it.onDataReceived(id, value) } }
  }

  private fun createBluetoothGattDescriptor(): BluetoothGattDescriptor {
    val descriptor =
      BluetoothGattDescriptor(
        CLIENT_CHARACTERISTIC_CONFIG,
        BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
      )
    descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
    return descriptor
  }

  private fun createProtocolId() = UUID.randomUUID().toString()

  private fun createConnectData(challenge: ConnectChallenge) =
    ByteUtils.concatByteArrays(challenge.challenge.copyOf(TRUNCATED_BYTES), challenge.salt)

  companion object {
    private const val TAG = "BlePeripheralProtocol"

    // Attribute protocol bytes attached to message. Available write size is MTU size minus att
    // bytes.
    @VisibleForTesting internal const val ATT_PROTOCOL_BYTES = 3
    private const val TRUNCATED_BYTES = 3
    private const val TIMEOUT_HANDLER_THREAD_NAME = "peripheralThread"
    private val CLIENT_CHARACTERISTIC_CONFIG: UUID =
      UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
  }
}
