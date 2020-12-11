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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A generic class that manages BLE peripheral operations like start/stop advertising, notifying
 * connects/disconnects and reading/writing values to GATT characteristics.
 *
 * <p>The base class provides methods for un/registering the callbacks. BLE operations are left for
 * the implementations.
 */
public abstract class BlePeripheralManager {
  private static final String TAG = "BlePeripheralManager";

  protected final Set<Callback> callbacks = new CopyOnWriteArraySet<>();
  protected final Set<OnCharacteristicWriteListener> writeListeners = new HashSet<>();
  protected final Set<OnCharacteristicReadListener> readListeners = new HashSet<>();

  /**
   * Registers the given callback to be notified of various events within the {@link
   * BlePeripheralManager}.
   *
   * @param callback The callback to be notified.
   */
  void registerCallback(@NonNull Callback callback) {
    callbacks.add(callback);
  }

  /**
   * Unregisters a previously registered callback.
   *
   * @param callback The callback to unregister.
   */
  void unregisterCallback(@NonNull Callback callback) {
    callbacks.remove(callback);
  }

  /**
   * Adds a listener to be notified of a write to characteristics.
   *
   * @param listener The listener to invoke.
   */
  void addOnCharacteristicWriteListener(@NonNull OnCharacteristicWriteListener listener) {
    writeListeners.add(listener);
  }

  /**
   * Removes the given listener from being notified of characteristic writes.
   *
   * @param listener The listener to remove.
   */
  void removeOnCharacteristicWriteListener(@NonNull OnCharacteristicWriteListener listener) {
    writeListeners.remove(listener);
  }

  /**
   * Adds a listener to be notified of reads to characteristics.
   *
   * @param listener The listener to invoke.
   */
  void addOnCharacteristicReadListener(@NonNull OnCharacteristicReadListener listener) {
    readListeners.add(listener);
  }

  /**
   * Removes the given listener from being notified of characteristic reads.
   *
   * @param listener The listener to remove.
   */
  void removeOnCharacteristicReadistener(@NonNull OnCharacteristicReadListener listener) {
    readListeners.remove(listener);
  }

  /**
   * Returns the current MTU size.
   *
   * @return The size of the MTU in bytes.
   */
  protected abstract int getMtuSize();

  /**
   * Starts the GATT server with the given {@link BluetoothGattService} and begins advertising.
   *
   * <p>It is possible that BLE service is still in TURNING_ON state when this method is invoked.
   * Therefore, several retries will be made to ensure advertising is started.
   *
   * @param service {@link BluetoothGattService} that will be discovered by clients
   * @param advertiseData {@link AdvertiseData} data to advertise
   * @param scanResponse {@link AdvertiseData} scan response
   * @param advertiseCallback {@link AdvertiseCallback} callback for advertiser
   */
  protected abstract void startAdvertising(
      BluetoothGattService service,
      AdvertiseData advertiseData,
      AdvertiseData scanResponse,
      AdvertiseCallback advertiseCallback);

  /**
   * Stops the GATT server from advertising.
   *
   * @param advertiseCallback The callback that is associated with the advertisement.
   */
  protected abstract void stopAdvertising(AdvertiseCallback advertiseCallback);

  /** Notifies the characteristic change via {@link BluetoothGattServer} */
  protected abstract void notifyCharacteristicChanged(
      @NonNull BluetoothDevice device,
      @NonNull BluetoothGattCharacteristic characteristic,
      boolean confirm);

  /** Connects the Gatt server of the remote device to retrieve device name. */
  protected abstract void retrieveDeviceName(BluetoothDevice device);

  /** Cleans up the BLE GATT server state. */
  @CallSuper
  protected void cleanup() {
    // Clears all registered listeners. IHU only supports single connection in peripheral role.
    readListeners.clear();
    writeListeners.clear();
  }

  /** Interface to be notified of various events within the {@link BlePeripheralManager}. */
  protected interface Callback {
    /**
     * Triggered when the name of the remote device is retrieved.
     *
     * @param deviceName Name of the remote device.
     */
    void onDeviceNameRetrieved(@Nullable String deviceName);

    /**
     * Triggered if a remote client has requested to change the MTU for a given connection.
     *
     * @param size The new MTU size.
     */
    void onMtuSizeChanged(int size);

    /**
     * Triggered when a device (GATT client) connected.
     *
     * @param device Remote device that connected on BLE.
     */
    void onRemoteDeviceConnected(@NonNull BluetoothDevice device);

    /**
     * Triggered when a device (GATT client) disconnected.
     *
     * @param device Remote device that disconnected on BLE.
     */
    void onRemoteDeviceDisconnected(@NonNull BluetoothDevice device);
  }

  /** An interface for classes that wish to be notified of writes to a characteristic. */
  protected interface OnCharacteristicWriteListener {
    /**
     * Triggered when this BlePeripheralManager receives a write request from a remote device.
     *
     * @param device The bluetooth device that holds the characteristic.
     * @param characteristic The characteristic that was written to.
     * @param value The value that was written.
     */
    void onCharacteristicWrite(
        @NonNull BluetoothDevice device,
        @NonNull BluetoothGattCharacteristic characteristic,
        @NonNull byte[] value);
  }

  /** An interface for classes that wish to be notified of reads on a characteristic. */
  protected interface OnCharacteristicReadListener {
    /**
     * Triggered when this BlePeripheralManager receives a read request from a remote device.
     *
     * @param device The bluetooth device that holds the characteristic.
     */
    void onCharacteristicRead(@NonNull BluetoothDevice device);
  }
}
