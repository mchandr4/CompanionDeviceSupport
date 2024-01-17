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
import com.google.android.connecteddevice.transport.ConnectChallenge
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.IDataSendCallback
import com.google.android.connecteddevice.transport.IDiscoveryCallback
import com.google.android.connecteddevice.util.ByteUtils
import com.google.android.connecteddevice.util.EventLog
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logw
import java.time.Duration
import java.util.UUID

/**
 * A ble peripheral communication protocol that provides actions and event notifications for
 * interacting with devices.
 */
class BlePeripheralProtocol
@JvmOverloads
constructor(
  private val blePeripheralManager: BlePeripheralManager,
  private val reconnectServiceUuid: UUID,
  private val reconnectDataUuid: UUID,
  advertiseDataCharacteristicUuid: UUID,
  writeCharacteristicUuid: UUID,
  readCharacteristicUuid: UUID,
  serviceChangedCharacteristicUuid: UUID,
  private val maxReconnectAdvertisementDuration: Duration,
  defaultMtuSize: Int,
) : ConnectionProtocol() {
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
  private val serviceChangedCharacteristic =
    BluetoothGattCharacteristic(
      serviceChangedCharacteristicUuid,
      BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
      BluetoothGattCharacteristic.PERMISSION_READ
    )
  private val advertiseDataCharacteristic =
    BluetoothGattCharacteristic(
      advertiseDataCharacteristicUuid,
      BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
      BluetoothGattCharacteristic.PERMISSION_READ
    )

  private val peripheralCallback: BlePeripheralManager.Callback =
    object : BlePeripheralManager.Callback {
      override fun onMtuSizeChanged(size: Int) {
        maxWriteSize = size - ATT_PROTOCOL_BYTES
        protocolId?.let { id ->
          maxDataSizeChangedListeners[id]?.invoke {
            it.onDeviceMaxDataSizeChanged(id, maxWriteSize)
          }
        }
      }

      override fun onRemoteDeviceConnected(device: BluetoothDevice) {
        bluetoothDevice = device
        val currentProtocolId = createProtocolId()
        protocolId = currentProtocolId

        logd(TAG, "Remote device ${device.address} connected. Protocol ID: $protocolId")

        discoveryCallback?.onDeviceConnected(currentProtocolId)

        blePeripheralManager.addOnCharacteristicWriteListener(
          this@BlePeripheralProtocol::onCharacteristicWrite
        )
        blePeripheralManager.addOnCharacteristicReadListener(
          this@BlePeripheralProtocol::onCharacteristicRead
        )
        stopAdvertising()
      }

      override fun onRemoteDeviceDisconnected(device: BluetoothDevice) {
        logd(
          TAG,
          "Remote device ${device.address} disconnected. Current connected device: " +
            "${bluetoothDevice?.address}"
        )

        if (device != bluetoothDevice) {
          loge(
            TAG,
            "Disconnected from device ${device.address} that is not the expected device " +
              "${bluetoothDevice?.address}. Ignoring."
          )
          return
        }
        bluetoothDevice = null

        val currentProtocolId = protocolId
        if (currentProtocolId == null) {
          logw(
            TAG,
            "Device disconnected but no protocol ID. Cannot notify disconnect listeners. Ignore."
          )
          return
        }

        protocolId = null
        val listener = deviceDisconnectedListeners[currentProtocolId]
        if (listener != null) {
          logd(TAG, "Valid disconnect listener exists for protocolId $protocolId. Notifying.")
          listener.invoke { it.onDeviceDisconnected(currentProtocolId) }
        } else {
          logw(TAG, "No disconnect listener exists for protocolId $protocolId.")
        }
        removeListeners(currentProtocolId)
      }
    }

  private val timeoutHandlerThread: HandlerThread =
    HandlerThread(TIMEOUT_HANDLER_THREAD_NAME).apply { start() }
  private var maxWriteSize: Int = defaultMtuSize - ATT_PROTOCOL_BYTES
  // Indicates ongoing connection discovery if not null
  private var deviceId: UUID? = null
  /** Current connected bluetooth device */
  private var bluetoothDevice: BluetoothDevice? = null
  /** The id of the current connection */
  private var protocolId: String? = null
  // Indicates ongoing association advertising if not null
  private var associationAdvertiseCallback: AdvertiseCallback? = null
  // Indicates ongoing reconnect advertising if not null
  private var connectionAdvertiseCallback: AdvertiseCallback? = null
  /** Indicates ongoing discovery if not null */
  private var discoveryCallback: IDiscoveryCallback? = null
  private var timeoutHandler = Handler(timeoutHandlerThread.looper)
  private var dataSendCallback: IDataSendCallback? = null

  init {
    writeCharacteristic.addDescriptor(createBluetoothGattDescriptor())
    readCharacteristic.addDescriptor(createBluetoothGattDescriptor())
    advertiseDataCharacteristic.addDescriptor(createBluetoothGattDescriptor())
  }

  override fun startAssociationDiscovery(
    name: String,
    identifier: ParcelUuid,
    callback: IDiscoveryCallback,
  ) {
    discoveryCallback = callback
    blePeripheralManager.registerCallback(peripheralCallback)
    val advertiseCallback =
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
    associationAdvertiseCallback = advertiseCallback
    startAdvertising(
      identifier.uuid,
      advertiseCallback,
      scanResponse = ByteUtils.hexStringToByteArray(name),
      scanResponseUuid = reconnectDataUuid
    )
  }

  override fun startConnectionDiscovery(
    id: ParcelUuid,
    challenge: ConnectChallenge,
    callback: IDiscoveryCallback
  ) {
    deviceId = id.uuid
    discoveryCallback = callback
    blePeripheralManager.registerCallback(peripheralCallback)
    val advertiseCallback =
      object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
          super.onStartSuccess(settingsInEffect)
          timeoutHandler.postDelayed(
            {
              logd(TAG, "Timeout period expired without a connection. Restarting advertisement.")
              retryConnectionDiscovery(id.uuid, challenge, callback)
            },
            maxReconnectAdvertisementDuration.toMillis()
          )
          logd(TAG, "Successfully started advertising for device $id.")
          callback.onDiscoveryStartedSuccessfully()
        }

        override fun onStartFailure(errorCode: Int) {
          super.onStartFailure(errorCode)
          callback.onDiscoveryFailedToStart()
        }
      }
    connectionAdvertiseCallback = advertiseCallback
    val advertiseData = createConnectData(challenge)
    if (advertiseData == null) {
      loge(TAG, "Unable to create advertisement data. Aborting connecting.")
      callback.onDiscoveryFailedToStart()
      return
    }
    EventLog.onDeviceSearchStarted()
    startAdvertising(reconnectServiceUuid, advertiseCallback, advertiseData, reconnectDataUuid)
  }

  private fun retryConnectionDiscovery(
    deviceId: UUID,
    connectChallenge: ConnectChallenge,
    discoveryCallback: IDiscoveryCallback
  ) {
    stopConnectionDiscovery(ParcelUuid(deviceId))
    startConnectionDiscovery(ParcelUuid(deviceId), connectChallenge, discoveryCallback)
  }

  override fun stopAssociationDiscovery() {
    if (associationAdvertiseCallback == null) {
      logd(TAG, "No association discovery is happening, ignoring.")
      return
    }
    blePeripheralManager.stopAdvertising(associationAdvertiseCallback)
    associationAdvertiseCallback = null
    discoveryCallback = null
  }

  override fun stopConnectionDiscovery(id: ParcelUuid) {
    if (id.uuid != deviceId || connectionAdvertiseCallback == null) {
      logd(TAG, "No connection discovery is happening for device $id, ignoring.")
      return
    }
    timeoutHandler.removeCallbacksAndMessages(null)
    blePeripheralManager.stopAdvertising(connectionAdvertiseCallback)
    connectionAdvertiseCallback = null
    discoveryCallback = null
    deviceId = null
  }

  override fun sendData(protocolId: String, data: ByteArray, callback: IDataSendCallback?) {
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
      blePeripheralManager.disconnect()
    }
  }

  /**
   * Resets all internal states.
   *
   * Please be cautious when calling this method, a disconnect usually does not need a reset, please
   * call [disconnectDevice] instead. This should only be called when Bluetooth is turned off.
   */
  override fun reset() {
    super.reset()
    logd(TAG, "Resetting protocol.")
    stopAdvertising()
    blePeripheralManager.cleanup()
    bluetoothDevice = null
    protocolId = null
    dataSendCallback = null
  }

  override fun getMaxWriteSize(protocolId: String): Int = maxWriteSize

  override fun isDeviceVerificationRequired(): Boolean = true

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
    gattService.addCharacteristic(serviceChangedCharacteristic)
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
    logd(TAG, "Attempting to stop all ongoing advertising.")
    stopAssociationDiscovery()
    if (deviceId != null) {
      stopConnectionDiscovery(ParcelUuid(deviceId))
    }
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
    // Clear the callback first because it may be set inside the callback's method call.
    val callback = dataSendCallback
    dataSendCallback = null
    callback?.onDataSentSuccessfully()
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
        "Received a message from device ${device.address} that is not expected" +
          " device ${bluetoothDevice?.address}. Disconnect."
      )
      blePeripheralManager.disconnect()
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
    protocolId?.let { id -> notifyDataReceived(id, value) }
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

    // Attribute protocol bytes attached to message. Available write size is MTU size minus ATT
    // bytes.
    // On Android 14 (U) the default MTU size is 517 bytes. For GATT writes that are longer than
    // 255 bytes, we need to reserve 5 bytes. See:
    // https://developer.android.com/about/versions/14/behavior-changes-all#mtu-set-to-517
    @VisibleForTesting internal const val ATT_PROTOCOL_BYTES = 5
    private const val TRUNCATED_BYTES = 3
    private const val TIMEOUT_HANDLER_THREAD_NAME = "peripheralThread"
    private val CLIENT_CHARACTERISTIC_CONFIG: UUID =
      UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
  }
}
