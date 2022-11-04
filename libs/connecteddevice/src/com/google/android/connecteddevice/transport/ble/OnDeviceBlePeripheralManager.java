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

package com.google.android.connecteddevice.transport.ble;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import androidx.annotation.NonNull;
import com.google.android.connecteddevice.util.ByteUtils;
import java.util.concurrent.atomic.AtomicReference;

/** An implementation that uses Android platform API for BLE peripheral operations. */
public class OnDeviceBlePeripheralManager extends BlePeripheralManager {
  private static final String TAG = "OnDeviceBlePeripheralManager";

  private static final int BLE_RETRY_LIMIT = 5;
  private static final int BLE_RETRY_INTERVAL_MS = 1000;

  private static final int GATT_SERVER_RETRY_LIMIT = 20;
  private static final int GATT_SERVER_RETRY_DELAY_MS = 200;

  private final Handler handler;

  private final Context context;
  private final AtomicReference<BluetoothGattServer> gattServer = new AtomicReference<>();
  private final AtomicReference<BluetoothLeAdvertiser> advertiser = new AtomicReference<>();
  private final AtomicReference<BluetoothDevice> connectedDevice = new AtomicReference<>();
  private final BluetoothManager bluetoothManager;

  private int mtuSize = 20;

  private int advertiserStartCount;
  private int gattServerRetryStartCount;
  private BluetoothGattService bluetoothGattService;
  private AdvertiseCallback advertiseCallback;
  private AdvertiseData advertiseData;
  private AdvertiseData scanResponse;

  public OnDeviceBlePeripheralManager(Context context) {
    this.context = context;
    handler = new Handler(this.context.getMainLooper());
    bluetoothManager = context.getSystemService(BluetoothManager.class);
  }

  /**
   * Returns the current MTU size.
   *
   * @return The size of the MTU in bytes.
   */
  @Override
  public int getMtuSize() {
    return mtuSize;
  }

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
  @Override
  public void startAdvertising(
      BluetoothGattService service,
      AdvertiseData advertiseData,
      AdvertiseData scanResponse,
      AdvertiseCallback advertiseCallback) {
    logd(TAG, "Request to start advertising with service " + service.getUuid() + ".");
    if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        || !bluetoothManager.getAdapter().isMultipleAdvertisementSupported()) {
      loge(TAG, "Attempted to start advertising, but system does not fully support BLE. Aborting.");
      return;
    }
    // Clears previous session before starting advertising.
    cleanup();
    bluetoothGattService = service;
    this.advertiseCallback = advertiseCallback;
    this.advertiseData = advertiseData;
    this.scanResponse = scanResponse;
    gattServerRetryStartCount = 0;
    openGattServer();
  }

  /**
   * Stops the GATT server from advertising.
   *
   * @param advertiseCallback The callback that is associated with the advertisement.
   */
  @Override
  public void stopAdvertising(AdvertiseCallback advertiseCallback) {
    BluetoothLeAdvertiser advertiser = this.advertiser.getAndSet(null);
    if (advertiser != null) {
      advertiser.stopAdvertising(advertiseCallback);
      logd(TAG, "Advertising stopped.");
    }
  }

  /** Notifies the characteristic change via {@link BluetoothGattServer} */
  @Override
  public void notifyCharacteristicChanged(
      @NonNull BluetoothDevice device,
      @NonNull BluetoothGattCharacteristic characteristic,
      boolean confirm) {
    BluetoothGattServer gattServer = this.gattServer.get();
    if (gattServer == null) {
      return;
    }

    if (!gattServer.notifyCharacteristicChanged(device, characteristic, confirm)) {
      loge(TAG, "notifyCharacteristicChanged failed");
    }
  }

  /** Cleans up the BLE GATT server state. */
  @Override
  public void cleanup() {
    super.cleanup();

    logd(TAG, "Cleaning up manager.");
    // Stops the advertiser, scanner and GATT server. This needs to be done to avoid leaks.
    stopAdvertising(advertiseCallback);

    BluetoothGattServer gattServer = this.gattServer.getAndSet(null);
    if (gattServer == null) {
      logw(TAG, "BluetoothGattServer was null. Connection has already been cleaned up.");
      return;
    }
    BluetoothDevice device = connectedDevice.getAndSet(null);
    if (device != null) {
      logd(TAG, "Canceling connection on currently connected device.");
      gattServer.cancelConnection(device);
      for (Callback callback : callbacks) {
        callback.onRemoteDeviceDisconnected(device);
      }
    } else {
      logd(TAG, "No device currently connected.");
    }
    logd(TAG, "Closing gatt server.");
    gattServer.clearServices();
    gattServer.close();
  }

  private void openGattServer() {
    // Only open one Gatt server.
    if (this.gattServer.get() == null) {
      BluetoothGattServer newGatt = bluetoothManager.openGattServer(context, gattServerCallback);
      if (newGatt != null) {
        this.gattServer.set(newGatt);
      } else if (gattServerRetryStartCount < GATT_SERVER_RETRY_LIMIT) {
        logw(
            TAG,
            "Failed to create Gatt server now, retry in " + GATT_SERVER_RETRY_DELAY_MS + "ms.");
        gattServerRetryStartCount++;
        handler.postDelayed(this::openGattServer, GATT_SERVER_RETRY_DELAY_MS);
        return;
      } else {
        loge(TAG, "Gatt server not created - exceeded retry limit.");
        return;
      }
    }
    BluetoothGattServer gattServer = this.gattServer.get();
    logd(TAG, "Gatt Server created, retry count: " + gattServerRetryStartCount);
    gattServerRetryStartCount = 0;

    gattServer.clearServices();
    gattServer.addService(bluetoothGattService);
    AdvertiseSettings settings =
        new AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build();
    advertiserStartCount = 0;
    startAdvertisingInternally(settings, advertiseData, scanResponse, advertiseCallback);
  }

  private void startAdvertisingInternally(
      AdvertiseSettings settings,
      AdvertiseData advertisement,
      AdvertiseData scanResponse,
      AdvertiseCallback advertiseCallback) {
    BluetoothAdapter adapter = bluetoothManager.getAdapter();
    if (adapter != null) {
      advertiser.compareAndSet(null, adapter.getBluetoothLeAdvertiser());
    }
    BluetoothLeAdvertiser advertiser = this.advertiser.get();
    if (advertiser != null) {
      logd(TAG, "Advertiser created, retry count: " + advertiserStartCount);
      advertiser.startAdvertising(settings, advertisement, scanResponse, advertiseCallback);
      advertiserStartCount = 0;
    } else if (advertiserStartCount < BLE_RETRY_LIMIT) {
      handler.postDelayed(
          () ->
              startAdvertisingInternally(settings, advertisement, scanResponse, advertiseCallback),
          BLE_RETRY_INTERVAL_MS);
      advertiserStartCount += 1;
    } else {
      loge(TAG, "Cannot start BLE Advertisement. Advertise Retry count: " + advertiserStartCount);
    }
  }

  private final BluetoothGattServerCallback gattServerCallback =
      new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
          if (status != BluetoothGatt.GATT_SUCCESS) {
            logd(
                TAG,
                "Received a connection state of "
                    + newState
                    + " with unsuccessful status "
                    + status
                    + ". Ignoring.");
            return;
          }
          switch (newState) {
            case BluetoothProfile.STATE_CONNECTED:
              logd(TAG, "BLE Connection State Change: CONNECTED");
              BluetoothGattServer gattServer = OnDeviceBlePeripheralManager.this.gattServer.get();
              if (gattServer == null) {
                return;
              }
              gattServer.connect(device, /* autoConnect= */ false);
              boolean isNew = connectedDevice.compareAndSet(null, device);
              if (!isNew) {
                logd(TAG, "This device has already connected. No further action required.");
                return;
              }
              for (Callback callback : callbacks) {
                callback.onRemoteDeviceConnected(device);
              }
              break;
            case BluetoothProfile.STATE_DISCONNECTED:
              logd(TAG, "BLE Connection State Change: DISCONNECTED");
              cleanup();
              break;
            default:
              logw(TAG, "Connection state not connecting or disconnecting; ignoring: " + newState);
          }
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
          logd(TAG, "Service added status: " + status + " uuid: " + service.getUuid());
        }

        @Override
        public void onCharacteristicWriteRequest(
            BluetoothDevice device,
            int requestId,
            BluetoothGattCharacteristic characteristic,
            boolean preparedWrite,
            boolean responseNeeded,
            int offset,
            byte[] value) {
          BluetoothGattServer gattServer = OnDeviceBlePeripheralManager.this.gattServer.get();
          if (gattServer == null) {
            return;
          }
          gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
          for (OnCharacteristicWriteListener listener : writeListeners) {
            listener.onCharacteristicWrite(device, characteristic, value);
          }
        }

        @Override
        public void onDescriptorWriteRequest(
            BluetoothDevice device,
            int requestId,
            BluetoothGattDescriptor descriptor,
            boolean preparedWrite,
            boolean responseNeeded,
            int offset,
            byte[] value) {
          logd(
              TAG,
              "Write request for descriptor: "
                  + descriptor.getUuid()
                  + "; value: "
                  + ByteUtils.byteArrayToHexString(value));
          BluetoothGattServer gattServer = OnDeviceBlePeripheralManager.this.gattServer.get();
          if (gattServer == null) {
            return;
          }
          gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
          logd(TAG, "onMtuChanged: " + mtu + " for device " + device.getAddress());

          mtuSize = mtu;

          for (Callback callback : callbacks) {
            callback.onMtuSizeChanged(mtu);
          }
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
          super.onNotificationSent(device, status);
          if (status == BluetoothGatt.GATT_SUCCESS) {
            logd(
                TAG,
                "Notification sent successfully. Device: "
                    + device.getAddress()
                    + ", Status: "
                    + status
                    + ". Notifying all listeners.");
            for (OnCharacteristicReadListener listener : readListeners) {
              listener.onCharacteristicRead(device);
            }
          } else {
            loge(TAG, "Notification failed. Device: " + device + ", Status: " + status);
          }
        }
      };
}
