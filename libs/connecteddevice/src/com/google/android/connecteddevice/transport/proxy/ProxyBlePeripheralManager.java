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

package com.google.android.connecteddevice.transport.proxy;

import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logi;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.transport.ble.BlePeripheralManager;
import com.google.protobuf.MessageLite;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** An implementation that proxies BLE peripheral operations as network requests. */
public class ProxyBlePeripheralManager extends BlePeripheralManager {
  private static final String TAG = "ProxyBlePeripheralManager";

  private static final int MTU_SIZE = 20;
  private static final String ADVERTISED_NAME = "EmulatorBleProxy";

  private final SocketFactory socketFactory;
  private final ScheduledExecutorService executor;

  @VisibleForTesting
  final MessageReader.Callback messageReaderCallback =
      new MessageReader.Callback() {
        @Override
        public void onAdvertisingStarted() {
          logi(TAG, "Proxy session did start advertising.");
          // Create fake value as the settings will be ignored.
          AdvertiseSettings settings = new AdvertiseSettings.Builder().build();
          advertiseCallback.onStartSuccess(settings);
        }

        @Override
        public void onRemoteDeviceConnected(BluetoothDevice device) {
          logi(TAG, "Remote device connected: " + device.getAddress());
          connectedDevice = device;
          for (BlePeripheralManager.Callback callback : callbacks) {
            callback.onRemoteDeviceConnected(device);
          }
        }

        @Override
        public void onRemoteDeviceDisconnected(BluetoothDevice device) {
          logi(TAG, "Remote device disconnected: " + device.getAddress());
          connectedDevice = null;
          for (BlePeripheralManager.Callback callback : callbacks) {
            callback.onRemoteDeviceDisconnected(device);
          }
        }

        @Override
        public void onCharacteristicWrite(
            BluetoothDevice device, BluetoothGattCharacteristic characteristic) {
          logi(TAG, "Remote device wrote to characteristic: " + characteristic.getUuid());
          for (OnCharacteristicWriteListener listener : writeListeners) {
            listener.onCharacteristicWrite(device, characteristic, characteristic.getValue());
          }
        }

        @Override
        public void onCharacteristicUpdated(BluetoothDevice device) {
          logi(TAG, "Characteristic has been updated.");
          for (OnCharacteristicReadListener listener : readListeners) {
            listener.onCharacteristicRead(connectedDevice);
          }
        }

        @Override
        public void onInputStreamFailure() {
          loge(TAG, "MessageReader input stream failed. Closing connection.");
          cleanup();
        }
      };

  private final MessageWriter.Callback messageWriterCallback =
      new MessageWriter.Callback() {
        @Override
        public void onMessageSent(MessageLite message) {
          logi(TAG, "Succeeded sending message.");
        }

        @Override
        public void onWriteMessageFailed() {
          loge(TAG, "MessageWriter output stream failed. Closing connection.");
          cleanup();
        }
      };

  private Socket socket;
  private MessageWriter messageWriter;
  private MessageReader messageReader;

  // The connected device via proxy. For simplicity, assume only one device is connected at a time.
  private BluetoothDevice connectedDevice;
  private AdvertiseCallback advertiseCallback;

  public ProxyBlePeripheralManager(SocketFactory socketFactory) {
    this(socketFactory, Executors.newSingleThreadScheduledExecutor());
  }

  public ProxyBlePeripheralManager(SocketFactory socketFactory, ScheduledExecutorService executor) {
    this.socketFactory = socketFactory;
    this.executor = executor;
  }

  @Override
  public int getMtuSize() {
    return MTU_SIZE;
  }

  /**
   * Requests to start advertising.
   *
   * <p>Advertising is assumed to be entry point of a connection session, so we will perform the
   * following in order to start advertising:
   *
   * <ol>
   *   <li>Creating a socket - potentially blocking the thread;
   *   <li>Requesting to add GATT service to proxy server;
   *   <li>Requesting to start adverting with a fixed name;
   * </ol>
   */
  @Override
  public void startAdvertising(
      BluetoothGattService service,
      AdvertiseData advertiseData,
      AdvertiseData scanResponse,
      AdvertiseCallback advertiseCallback) {
    logi(TAG, "Request to start advertising with service " + service.getUuid() + ".");

    this.advertiseCallback = advertiseCallback;

    if (socket == null) {
      initSocketAndMessageReaderWriter();
    }

    execute(
        () -> {
          messageWriter.sendText("Hello from the Emulator ProxyBlePeripheralManager.");
          messageWriter.addService(service, advertiseData);
          messageWriter.startAdvertising(ADVERTISED_NAME);
        });
  }

  private void initSocketAndMessageReaderWriter() {
    execute(
        () -> {
          socket = socketFactory.createSocket();
          if (socket == null) {
            loge(TAG, "Could not create new socket.");
            return;
          }

          try {
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();
            messageReader = new MessageReader(inputStream, messageReaderCallback, executor);
            messageReader.startProcessingInputStream();
            messageWriter = new MessageWriter(outputStream, messageWriterCallback);
          } catch (IOException e) {
            loge(TAG, "Could not retrieve input/output stream from socket.", e);
            return;
          }
          logi(TAG, "New socket created.");
        });
  }

  /**
   * Requests to stop advertising.
   *
   * <p>This method ignores the parameter advertiseCallback, i.e. it will always request to stop.
   */
  @Override
  public void stopAdvertising(AdvertiseCallback advertiseCallback) {
    logi(TAG, "Request to stop advertising.");
    advertiseCallback = null;
    execute(
        () -> {
          if (messageWriter != null) {
            messageWriter.stopAdvertising();
          }
        });
  }

  /** Requests to update a characteristic. */
  @Override
  public void notifyCharacteristicChanged(
      @NonNull BluetoothDevice device,
      @NonNull BluetoothGattCharacteristic characteristic,
      boolean confirm) {
    if (!device.getAddress().equals(connectedDevice.getAddress())) {
      loge(TAG, "Requested to write unknown device. Ignored.");
      return;
    }

    execute(
        () -> {
          messageWriter.updateCharacteristic(characteristic);
        });
  }

  /** Closes the proxy socket and its dependent components. */
  @Override
  public void cleanup() {
    super.cleanup();

    logi(TAG, "Cleaning up manager.");
    if (socket == null) {
      loge(TAG, "Attempted to invalidate without a socket. Ignored.");
      return;
    }
    stopAdvertising(advertiseCallback);
    // Submit a task because stopAdvertising still needs the socket to send request.
    execute(
        () -> {
          messageReader.invalidate();
          messageReader = null;
          messageWriter.invalidate();
          messageWriter = null;

          try {
            socket.close();
          } catch (IOException e) {
            loge(TAG, "Could not close socket.", e);
            return;
          }
          socket = null;
        });
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  protected void execute(Runnable runnable) {
    executor.submit(runnable);
  }
}
