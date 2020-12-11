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

import static com.google.android.connecteddevice.model.Errors.DEVICE_ERROR_UNEXPECTED_DISCONNECTION;
import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelUuid;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.connection.AssociationCallback;
import com.google.android.connecteddevice.connection.AssociationSecureChannel;
import com.google.android.connecteddevice.connection.CarBluetoothManager;
import com.google.android.connecteddevice.connection.OobAssociationSecureChannel;
import com.google.android.connecteddevice.connection.ReconnectSecureChannel;
import com.google.android.connecteddevice.connection.SecureChannel;
import com.google.android.connecteddevice.oob.OobChannel;
import com.google.android.connecteddevice.oob.OobConnectionManager;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage;
import com.google.android.connecteddevice.util.ByteUtils;
import com.google.android.connecteddevice.util.EventLog;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Future;

/** Communication manager that allows for targeted connections to a specific device in the car. */
public class CarBlePeripheralManager extends CarBluetoothManager {

  private static final String TAG = "CarBlePeripheralManager";

  // Attribute protocol bytes attached to message. Available write size is MTU size minus att
  // bytes.
  private static final int ATT_PROTOCOL_BYTES = 3;

  private static final UUID CLIENT_CHARACTERISTIC_CONFIG =
      UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

  private static final int SALT_BYTES = 8;

  private static final int TOTAL_AD_DATA_BYTES = 16;

  private static final int TRUNCATED_BYTES = 3;

  private static final String TIMEOUT_HANDLER_THREAD_NAME = "peripheralThread";

  private final BlePeripheralManager blePeripheralManager;

  private final UUID associationServiceUuid;

  private final UUID reconnectServiceUuid;

  private final UUID reconnectDataUuid;

  private final BluetoothGattCharacteristic writeCharacteristic;

  private final BluetoothGattCharacteristic readCharacteristic;

  private final BluetoothGattCharacteristic advertiseDataCharacteristic;

  private HandlerThread timeoutHandlerThread;

  private Handler timeoutHandler;

  private final Duration maxReconnectAdvertisementDuration;

  private final int defaultMtuSize;

  private String reconnectDeviceId;

  private byte[] reconnectChallenge;

  private AdvertiseCallback advertiseCallback;

  private OobConnectionManager oobConnectionManager;

  private Future<?> bluetoothNameTask;

  private AssociationCallback associationCallback;

  /**
   * Initialize a new instance of manager.
   *
   * @param blePeripheralManager {@link BlePeripheralManager} for establishing connection.
   * @param connectedDeviceStorage Shared {@link ConnectedDeviceStorage} for companion features.
   * @param associationServiceUuid {@link UUID} of association service.
   * @param reconnectServiceUuid {@link UUID} of reconnect service.
   * @param reconnectDataUuid {@link UUID} key of reconnect advertisement data.
   * @param advertiseDataCharacteristicUuid {@link UUID} of characteristic that contains advertise
   *     data.
   * @param writeCharacteristicUuid {@link UUID} of characteristic the car will write to.
   * @param readCharacteristicUuid {@link UUID} of characteristic the device will write to.
   * @param maxReconnectAdvertisementDuration Maximum duration to advertise for reconnect before
   *     restarting.
   * @param defaultMtuSize Default MTU size for new channels.
   * @param enableCompression Enable compression on outgoing messages.
   */
  public CarBlePeripheralManager(
      @NonNull BlePeripheralManager blePeripheralManager,
      @NonNull ConnectedDeviceStorage connectedDeviceStorage,
      @NonNull UUID associationServiceUuid,
      @NonNull UUID reconnectServiceUuid,
      @NonNull UUID reconnectDataUuid,
      @NonNull UUID advertiseDataCharacteristicUuid,
      @NonNull UUID writeCharacteristicUuid,
      @NonNull UUID readCharacteristicUuid,
      @NonNull Duration maxReconnectAdvertisementDuration,
      int defaultMtuSize,
      boolean enableCompression) {
    super(connectedDeviceStorage, enableCompression);
    this.blePeripheralManager = blePeripheralManager;
    this.associationServiceUuid = associationServiceUuid;
    this.reconnectServiceUuid = reconnectServiceUuid;
    this.reconnectDataUuid = reconnectDataUuid;

    writeCharacteristic =
        new BluetoothGattCharacteristic(
            writeCharacteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ);
    writeCharacteristic.addDescriptor(createBluetoothGattDescriptor());

    readCharacteristic =
        new BluetoothGattCharacteristic(
            readCharacteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE
                | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE);
    readCharacteristic.addDescriptor(createBluetoothGattDescriptor());

    advertiseDataCharacteristic =
        new BluetoothGattCharacteristic(
            advertiseDataCharacteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ);
    advertiseDataCharacteristic.addDescriptor(createBluetoothGattDescriptor());

    this.maxReconnectAdvertisementDuration = maxReconnectAdvertisementDuration;
    this.defaultMtuSize = defaultMtuSize;
  }

  private BluetoothGattDescriptor createBluetoothGattDescriptor() {
    BluetoothGattDescriptor descriptor =
        new BluetoothGattDescriptor(
            CLIENT_CHARACTERISTIC_CONFIG,
            BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
    descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
    return descriptor;
  }

  @Override
  public void start() {
    super.start();
    timeoutHandlerThread = new HandlerThread(TIMEOUT_HANDLER_THREAD_NAME);
    timeoutHandlerThread.start();
    timeoutHandler = new Handler(timeoutHandlerThread.getLooper());
  }

  @Override
  public void stop() {
    super.stop();
    if (timeoutHandlerThread != null) {
      timeoutHandlerThread.quit();
    }
    reset();
  }

  @Override
  public void disconnectDevice(@NonNull String deviceId) {
    if (deviceId.equals(reconnectDeviceId)) {
      logd(TAG, "Reconnection canceled for device " + deviceId + ".");
      reset();
      return;
    }
    ConnectedRemoteDevice connectedDevice = getConnectedDevice();
    if (connectedDevice == null || !deviceId.equals(connectedDevice.deviceId)) {
      return;
    }
    reset();
  }

  @Override
  public AssociationCallback getAssociationCallback() {
    return associationCallback;
  }

  @Override
  public void setAssociationCallback(AssociationCallback callback) {
    associationCallback = callback;
  }

  @Override
  public void reset() {
    super.reset();
    logd(TAG, "Resetting state.");
    if (timeoutHandler != null) {
      timeoutHandler.removeCallbacks(timeoutRunnable);
    }
    blePeripheralManager.cleanup();
    reconnectDeviceId = null;
    reconnectChallenge = null;
    oobConnectionManager = null;
    associationCallback = null;
    if (bluetoothNameTask != null) {
      bluetoothNameTask.cancel(true);
    }
    bluetoothNameTask = null;
  }

  @Override
  public void initiateConnectionToDevice(@NonNull UUID deviceId) {
    reconnectDeviceId = deviceId.toString();
    advertiseCallback =
        new AdvertiseCallback() {
          @Override
          public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            timeoutHandler.postDelayed(
                timeoutRunnable, maxReconnectAdvertisementDuration.toMillis());
            logd(TAG, "Successfully started advertising for device " + deviceId + ".");
          }
        };
    blePeripheralManager.unregisterCallback(associationPeripheralCallback);
    blePeripheralManager.registerCallback(reconnectPeripheralCallback);
    timeoutHandler.removeCallbacks(timeoutRunnable);
    byte[] advertiseData = createReconnectData(reconnectDeviceId);
    if (advertiseData == null) {
      loge(TAG, "Unable to create advertisement data. Aborting reconnect.");
      return;
    }
    startAdvertising(
        reconnectServiceUuid,
        advertiseCallback,
        advertiseData,
        reconnectDataUuid,
        /* scanResponse= */ null,
        /* scanResponseUuid= */ null);
  }

  /**
   * Create data for reconnection advertisement.
   *
   * <p>
   *
   * <p>Process:
   *
   * <ol>
   *   <li>Generate random {@value SALT_BYTES} byte salt and zero-pad to {@value
   *       TOTAL_AD_DATA_BYTES} bytes.
   *   <li>Hash with stored challenge secret and truncate to {@value TRUNCATED_BYTES} bytes.
   *   <li>Concatenate hashed {@value TRUNCATED_BYTES} bytes with salt and return.
   * </ol>
   */
  @Nullable
  private byte[] createReconnectData(String deviceId) {
    byte[] salt = ByteUtils.randomBytes(SALT_BYTES);
    byte[] zeroPadded =
        ByteUtils.concatByteArrays(salt, new byte[TOTAL_AD_DATA_BYTES - SALT_BYTES]);
    reconnectChallenge = storage.hashWithChallengeSecret(deviceId, zeroPadded);
    if (reconnectChallenge == null) {
      return null;
    }
    return ByteUtils.concatByteArrays(Arrays.copyOf(reconnectChallenge, TRUNCATED_BYTES), salt);
  }

  @Override
  public void startAssociation(
      @NonNull String nameForAssociation, @NonNull AssociationCallback callback) {
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    if (adapter == null) {
      loge(TAG, "Bluetooth is unavailable on this device. Unable to start associating.");
      return;
    }

    reset();
    associationCallback = callback;
    blePeripheralManager.unregisterCallback(reconnectPeripheralCallback);
    blePeripheralManager.registerCallback(associationPeripheralCallback);
    advertiseCallback =
        new AdvertiseCallback() {
          @Override
          public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            callback.onAssociationStartSuccess(nameForAssociation);
            logd(TAG, "Successfully started advertising for association.");
          }

          @Override
          public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            callback.onAssociationStartFailure();
            logd(TAG, "Failed to start advertising for association. Error code: " + errorCode);
          }
        };

    startAdvertising(
        associationServiceUuid,
        advertiseCallback,
        /* advertiseData= */ null,
        /* advertiseDataUuid= */ null,
        nameForAssociation.getBytes(),
        reconnectDataUuid);
  }

  /** Start the association with a new device using out of band verification code exchange */
  @Override
  public void startOutOfBandAssociation(
      @NonNull String nameForAssociation,
      @NonNull OobChannel oobChannel,
      @NonNull AssociationCallback callback) {

    logd(TAG, "Starting out of band association.");
    startAssociation(
        nameForAssociation,
        new AssociationCallback() {
          @Override
          public void onAssociationStartSuccess(String deviceName) {
            associationCallback = callback;
            boolean success = oobConnectionManager.startOobExchange(oobChannel);
            if (!success) {
              callback.onAssociationStartFailure();
              return;
            }
            callback.onAssociationStartSuccess(deviceName);
          }

          @Override
          public void onAssociationStartFailure() {
            callback.onAssociationStartFailure();
          }
        });
    oobConnectionManager = new OobConnectionManager();
  }

  /** Set the timeout handler for testing. This should be called after {@link #start()}. */
  @VisibleForTesting
  void setTimeoutHandler(Handler handler) {
    timeoutHandler = handler;
  }

  private void startAdvertising(
      @NonNull UUID serviceUuid,
      @NonNull AdvertiseCallback callback,
      @Nullable byte[] advertiseData,
      @Nullable UUID advertiseDataUuid,
      @Nullable byte[] scanResponse,
      @Nullable UUID scanResponseUuid) {
    BluetoothGattService gattService =
        new BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY);
    gattService.addCharacteristic(writeCharacteristic);
    gattService.addCharacteristic(readCharacteristic);

    AdvertiseData.Builder advertisementBuilder =
        new AdvertiseData.Builder();
    ParcelUuid uuid = new ParcelUuid(serviceUuid);
    advertisementBuilder.addServiceUuid(uuid);
    if (advertiseData != null) {
      ParcelUuid dataUuid = uuid;
      if (advertiseDataUuid != null) {
        dataUuid = new ParcelUuid(advertiseDataUuid);
      }
      advertisementBuilder.addServiceData(dataUuid, advertiseData);

      // Also embed the advertise data into a fixed GATT service characteristic.
      advertiseDataCharacteristic.setValue(advertiseData);
      gattService.addCharacteristic(advertiseDataCharacteristic);
    }

    AdvertiseData.Builder scanResponseBuilder =
        new AdvertiseData.Builder();
    if (scanResponse != null && scanResponseUuid != null) {
      ParcelUuid scanResponseParcelUuid = new ParcelUuid(scanResponseUuid);
      scanResponseBuilder.addServiceData(scanResponseParcelUuid, scanResponse);
    }

    blePeripheralManager.startAdvertising(gattService, advertisementBuilder.build(),
        scanResponseBuilder.build(), callback);
  }

  private void addConnectedDevice(BluetoothDevice device, boolean isReconnect) {
    addConnectedDevice(device, isReconnect, /* oobConnectionManager= */ null);
  }

  private void addConnectedDevice(
      @NonNull BluetoothDevice device,
      boolean isReconnect,
      @Nullable OobConnectionManager oobConnectionManager) {
    EventLog.onDeviceConnected();
    blePeripheralManager.stopAdvertising(advertiseCallback);
    if (timeoutHandler != null) {
      timeoutHandler.removeCallbacks(timeoutRunnable);
    }

    if (device.getName() == null) {
      logd(
          TAG, "Device connected, but name is null; issuing request to retrieve device " + "name.");
      blePeripheralManager.retrieveDeviceName(device);
    } else {
      setClientDeviceName(device.getName());
    }
    setClientDeviceAddress(device.getAddress());

    BleDeviceMessageStream secureStream =
        new BleDeviceMessageStream(
            blePeripheralManager,
            device,
            writeCharacteristic,
            readCharacteristic,
            defaultMtuSize - ATT_PROTOCOL_BYTES);
    secureStream.setMessageReceivedErrorListener(
        exception -> {
          disconnectWithError("Error occurred in stream: " + exception.getMessage());
        });
    SecureChannel secureChannel;
    if (isReconnect) {
      secureChannel =
          new ReconnectSecureChannel(
              secureStream, storage, reconnectDeviceId, reconnectChallenge);
    } else if (oobConnectionManager != null) {
      secureChannel = new OobAssociationSecureChannel(secureStream, storage, oobConnectionManager);
    } else {
      secureChannel = new AssociationSecureChannel(secureStream, storage);
    }
    secureChannel.registerCallback(secureChannelCallback);
    ConnectedRemoteDevice connectedDevice = new ConnectedRemoteDevice(device, /* gatt= */ null);
    connectedDevice.secureChannel = secureChannel;
    addConnectedDevice(connectedDevice);
    if (isReconnect) {
      setDeviceIdAndNotifyCallbacks(reconnectDeviceId);
      reconnectDeviceId = null;
      reconnectChallenge = null;
    }
  }

  private void setMtuSize(int mtuSize) {
    ConnectedRemoteDevice connectedDevice = getConnectedDevice();
    if (connectedDevice != null
        && connectedDevice.secureChannel != null
        && connectedDevice.secureChannel.getStream() != null) {
        connectedDevice.secureChannel.getStream()
          .setMaxWriteSize(mtuSize - ATT_PROTOCOL_BYTES);
    }
  }

  private final BlePeripheralManager.Callback reconnectPeripheralCallback =
      new BlePeripheralManager.Callback() {

        @Override
        public void onDeviceNameRetrieved(String deviceName) {
          // Ignored.
        }

        @Override
        public void onMtuSizeChanged(int size) {
          setMtuSize(size);
        }

        @Override
        public void onRemoteDeviceConnected(BluetoothDevice device) {
          addConnectedDevice(device, /* isReconnect= */ true);
        }

        @Override
        public void onRemoteDeviceDisconnected(BluetoothDevice device) {
          String deviceId = reconnectDeviceId;
          ConnectedRemoteDevice connectedDevice = getConnectedDevice(device);
          // Reset before invoking callbacks to avoid a race condition with reconnect
          // logic.
          reset();
          if (connectedDevice != null) {
            deviceId = connectedDevice.deviceId;
          }
          final String finalDeviceId = deviceId;
          if (finalDeviceId == null) {
            logw(
                TAG,
                "Callbacks were not issued for disconnect because the device id " + "was null.");
            return;
          }
          logd(TAG, "Connected device " + finalDeviceId + " disconnected.");
          callbacks.invoke(callback -> callback.onDeviceDisconnected(finalDeviceId));
        }
      };

  private final BlePeripheralManager.Callback associationPeripheralCallback =
      new BlePeripheralManager.Callback() {
        @Override
        public void onDeviceNameRetrieved(String deviceName) {
          if (deviceName == null) {
            return;
          }
          setClientDeviceName(deviceName);
          ConnectedRemoteDevice connectedDevice = getConnectedDevice();
          if (connectedDevice == null || connectedDevice.deviceId == null) {
            return;
          }
          storage.updateAssociatedDeviceName(connectedDevice.deviceId, deviceName);
        }

        @Override
        public void onMtuSizeChanged(int size) {
          setMtuSize(size);
        }

        @Override
        public void onRemoteDeviceConnected(BluetoothDevice device) {
          addConnectedDevice(device, /* isReconnect= */ false, oobConnectionManager);
          ConnectedRemoteDevice connectedDevice = getConnectedDevice();
          if (connectedDevice == null || connectedDevice.secureChannel == null) {
            return;
          }
          ((AssociationSecureChannel) connectedDevice.secureChannel)
              .setShowVerificationCodeListener(
                  code -> {
                    if (!isAssociating()) {
                      loge(TAG, "No valid callback for association.");
                      return;
                    }
                    associationCallback.onVerificationCodeAvailable(code);
                  });
        }

        @Override
        public void onRemoteDeviceDisconnected(BluetoothDevice device) {
          logd(TAG, "Remote device disconnected.");
          ConnectedRemoteDevice connectedDevice = getConnectedDevice(device);
          if (isAssociating()) {
            associationCallback.onAssociationError(DEVICE_ERROR_UNEXPECTED_DISCONNECTION);
          }
          // Reset before invoking callbacks to avoid a race condition with reconnect
          // logic.
          reset();
          if (connectedDevice == null || connectedDevice.deviceId == null) {
            logw(TAG, "Callbacks were not issued for disconnect.");
            return;
          }
          callbacks.invoke(callback -> callback.onDeviceDisconnected(connectedDevice.deviceId));
        }
      };

  private final Runnable timeoutRunnable =
      new Runnable() {
        @Override
        public void run() {
          logd(TAG, "Timeout period expired without a connection. Restarting advertisement.");
          blePeripheralManager.stopAdvertising(advertiseCallback);
          connectToDevice(UUID.fromString(reconnectDeviceId));
        }
      };
}
