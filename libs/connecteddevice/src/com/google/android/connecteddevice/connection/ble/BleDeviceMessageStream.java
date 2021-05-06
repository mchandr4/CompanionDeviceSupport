/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.google.android.connecteddevice.connection.ble;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import androidx.annotation.NonNull;
import com.google.android.connecteddevice.connection.DeviceMessageStream;
import com.google.android.connecteddevice.transport.ble.BlePeripheralManager;

/** BLE message stream to a device. */
public class BleDeviceMessageStream extends DeviceMessageStream {

  private static final String TAG = "BleDeviceMessageStream";

  private final BlePeripheralManager blePeripheralManager;

  private final BluetoothDevice device;

  private final BluetoothGattCharacteristic writeCharacteristic;

  private final BluetoothGattCharacteristic readCharacteristic;

  BleDeviceMessageStream(
      @NonNull BlePeripheralManager blePeripheralManager,
      @NonNull BluetoothDevice device,
      @NonNull BluetoothGattCharacteristic writeCharacteristic,
      @NonNull BluetoothGattCharacteristic readCharacteristic,
      int defaultMaxWriteSize) {
    super(defaultMaxWriteSize);
    this.blePeripheralManager = blePeripheralManager;
    this.device = device;
    this.writeCharacteristic = writeCharacteristic;
    this.readCharacteristic = readCharacteristic;
    this.blePeripheralManager.addOnCharacteristicWriteListener(this::onCharacteristicWrite);
    this.blePeripheralManager.addOnCharacteristicReadListener(this::onCharacteristicRead);
  }

  @Override
  protected void send(byte[] data) {
    writeCharacteristic.setValue(data);
    blePeripheralManager.notifyCharacteristicChanged(
        device, writeCharacteristic, /* confirm= */ false);
  }

  private void onCharacteristicRead(@NonNull BluetoothDevice device) {
    if (!this.device.equals(device)) {
      logw(
          TAG,
          "Received a read notification from a device ("
              + device.getAddress()
              + ") that is not the expected device ("
              + this.device.getAddress()
              + ") registered "
              + "to this stream. Ignoring.");
      return;
    }

    logd(TAG, "Releasing lock on characteristic.");
    sendCompleted();
  }

  private void onCharacteristicWrite(
      @NonNull BluetoothDevice device,
      @NonNull BluetoothGattCharacteristic characteristic,
      @NonNull byte[] value) {
    logd(TAG, "Received a message from a device (" + device.getAddress() + ").");
    if (!this.device.equals(device)) {
      logw(
          TAG,
          "Received a message from a device ("
              + device.getAddress()
              + ") that is not "
              + "the expected device ("
              + this.device.getAddress()
              + ") registered to this "
              + "stream. Ignoring.");
      return;
    }

    if (!characteristic.getUuid().equals(readCharacteristic.getUuid())) {
      logw(
          TAG,
          "Received a write to a characteristic ("
              + characteristic.getUuid()
              + ") that"
              + " is not the expected UUID ("
              + readCharacteristic.getUuid()
              + "). "
              + "Ignoring.");
      return;
    }
    onDataReceived(value);
  }
}
