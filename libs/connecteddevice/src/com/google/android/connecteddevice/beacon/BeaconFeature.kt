/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.android.connecteddevice.beacon

import android.Manifest.permission
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.google.android.companionprotos.BeaconMessageProto.BeaconMessage
import com.google.android.companionprotos.BeaconMessageProto.BeaconMessage.MessageType
import com.google.android.connecteddevice.R
import com.google.android.connecteddevice.api.Connector
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.util.ByteUtils
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.protobuf.InvalidProtocolBufferException
import java.nio.ByteBuffer
import java.util.UUID

/** Companion feature that advertises a fixed beacon. */
class BeaconFeature
internal constructor(
  private val context: Context,
  private val beaconUuid: UUID,
  private val connector: Connector,
) : Connector.Callback {
  // Creates beacon data conforming to Apple's iBeacon format.
  // https://support.kontakt.io/hc/en-gb/articles/4413251561106-iBeacon-packets
  private val beaconAdvertiseData: AdvertiseData = run {
    val uuid = ByteUtils.uuidToBytes(beaconUuid)
    val manufacturerData =
      ByteBuffer.allocate(23).apply {
        // Add beacon type
        put(0, 0x02)
        // Add beacon length
        put(1, 0x15)

        for (i in 2..17) {
          // Add the beacon UUID
          put(i, uuid[i - 2])
        }

        // Add first byte of Major
        put(18, 0x00)
        // Add second byte of Major
        put(19, 0x01)
        // Add first byte of Minor
        put(20, 0x00)
        // Add second byte of Minor
        put(21, 0x01)
        // Add txPower
        put(22, 0x0C)
      }

    AdvertiseData.Builder().run {
      // Add Apple manufacturer id
      addManufacturerData(76, manufacturerData.array())
      build()
    }
  }

  init {
    connector.apply {
      featureId = FEATURE_ID
      callback = this@BeaconFeature
    }
  }

  override fun onSecureChannelEstablished(device: ConnectedDevice) {
    stopAdvertising()
  }

  override fun onDeviceDisconnected(device: ConnectedDevice) {
    startAdvertising()
  }

  fun start() {
    connector.connect()
    startAdvertising()
  }

  fun stop() {
    stopAdvertising()
    connector.disconnect()
  }

  private fun startAdvertising() {
    logd(TAG, "Start beacon advertisement.")

    val advertiser = context.getBluetoothLeAdvertiser()
    if (advertiser == null) {
      loge(TAG, "Null advertiser; could not start.")
      return
    }

    // Stop existing (if any) advertisement first.
    advertiser.stopAdvertising(ADVERTISEMENT_CALLBACK)

    advertiser.startAdvertising(ADVERTISE_SETTINGS, beaconAdvertiseData, ADVERTISEMENT_CALLBACK)
  }

  private fun stopAdvertising() {
    logd(TAG, "Stop beacon advertisement.")

    val advertiser = context.getBluetoothLeAdvertiser()
    if (advertiser == null) {
      loge(TAG, "Null advertiser; could not stop.")
      return
    }

    advertiser.stopAdvertising(ADVERTISEMENT_CALLBACK)
  }

  override fun onMessageReceived(device: ConnectedDevice, message: ByteArray) {
    val beaconMessage =
      try {
        BeaconMessage.parseFrom(message)
      } catch (e: InvalidProtocolBufferException) {
        loge(TAG, "Received invalid message. Ignore.")
        return
      }

    when (beaconMessage.messageType) {
      MessageType.QUERY -> sendAck(device)
      else -> loge(TAG, "Received invalid message type: ${beaconMessage.messageType}. Ignore.")
    }
  }

  private fun sendAck(device: ConnectedDevice) {
    logd(TAG, "Sending Ack message.")
    val message = BeaconMessage.newBuilder().setMessageType(MessageType.ACK).build()
    connector.sendMessageSecurely(device, message.toByteArray())
  }

  companion object {
    /**
     * Creates a BeaconFeature.
     *
     * The beacon UUID is specified by the xml config resource `R.string.beacon_uuid`.
     */
    @JvmStatic
    fun create(context: Context, connector: Connector): BeaconFeature {
      val beaconUuid = UUID.fromString(context.resources.getString(R.string.beacon_uuid))
      return BeaconFeature(context, beaconUuid, connector)
    }

    private const val TAG = "BeaconFeature"
    private val FEATURE_ID = ParcelUuid.fromString("9eb6528d-bb65-4239-b196-6789196cf2b1")

    private val ADVERTISE_SETTINGS: AdvertiseSettings =
      with(AdvertiseSettings.Builder()) {
        setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
        setConnectable(false)
        build()
      }

    private val ADVERTISEMENT_CALLBACK: AdvertiseCallback =
      object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
          logd(TAG, "Failed to start advertisement.")
        }
      }

    /** Returns a BLE advertiser if all prerequisites are met; `null` otherwise. */
    internal fun Context.getBluetoothLeAdvertiser(): BluetoothLeAdvertiser? {
      if (!checkPermission()) {
        loge(TAG, "Required permission is not granted. Aborting.")
        return null
      }

      val bluetoothManager = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
      if (
        !this.getPackageManager().hasSystemFeature(FEATURE_BLUETOOTH_LE) ||
          !bluetoothManager.getAdapter().isMultipleAdvertisementSupported()
      ) {
        loge(TAG, "System does not meet BLE advertisement requirement. Aborting.")
        return null
      }

      // This call can still return null if BT is turned off on this device.
      return bluetoothManager.getAdapter().getBluetoothLeAdvertiser()
    }

    /** Returns if the required permission is granted. */
    private fun Context.checkPermission(): Boolean {
      val advertisePermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          permission.BLUETOOTH_ADVERTISE
        } else {
          permission.BLUETOOTH
        }
      return ContextCompat.checkSelfPermission(this, advertisePermission) == PERMISSION_GRANTED
    }
  }
}
