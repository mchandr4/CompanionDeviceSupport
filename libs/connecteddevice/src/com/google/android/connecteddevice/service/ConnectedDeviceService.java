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

package com.google.android.connecteddevice.service;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logi;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import androidx.annotation.NonNull;
import com.google.android.connecteddevice.ConnectedDeviceManager;
import com.google.android.connecteddevice.api.ConnectedDeviceManagerBinder;
import com.google.android.connecteddevice.api.IAssociatedDeviceManager;
import com.google.android.connecteddevice.api.RemoteFeature;
import com.google.android.connecteddevice.connection.CarBluetoothManager;
import com.google.android.connecteddevice.connection.ble.CarBlePeripheralManager;
import com.google.android.connecteddevice.connection.spp.CarSppManager;
import com.google.android.connecteddevice.logging.LoggingFeature;
import com.google.android.connecteddevice.logging.LoggingManager;
import com.google.android.connecteddevice.oob.BluetoothRfcommChannel;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage;
import com.google.android.connecteddevice.system.SystemFeature;
import com.google.android.connecteddevice.transport.ble.BlePeripheralManager;
import com.google.android.connecteddevice.transport.ble.OnDeviceBlePeripheralManager;
import com.google.android.connecteddevice.transport.proxy.ProxyBlePeripheralManager;
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder;
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder.OnRemoteCallbackSetListener;
import com.google.android.connecteddevice.util.EventLog;
import com.google.android.connecteddevice.util.Logger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Early start service that holds a {@link ConnectedDeviceManager} reference to support companion
 * device features.
 */
public final class ConnectedDeviceService extends TrunkService {

  private static final String TAG = "ConnectedDeviceService";

  // The mac address randomly rotates every 7-15 minutes. To be safe, we will rotate our
  // reconnect advertisement every 6 minutes to avoid crossing a rotation.
  private static final Duration MAX_ADVERTISEMENT_DURATION = Duration.ofMinutes(6);

  /** {@code boolean} Enable SPP. */
  private static final String META_ENABLE_SPP = "com.google.android.connecteddevice.enable_spp";
  /** {@code String} UUID for SPP server. */
  private static final String META_SPP_SERVICE_UUID =
      "com.google.android.connecteddevice.spp_service_uuid";
  /** {@code String} UUID for association advertisement. */
  private static final String META_ASSOCIATION_SERVICE_UUID =
      "com.google.android.connecteddevice.association_service_uuid";
  /** {@code String} UUID for reconnection advertisement. */
  private static final String META_RECONNECT_SERVICE_UUID =
      "com.google.android.connecteddevice.reconnect_service_uuid";
  /** {@code String} UUID for extra reconnection advertisement data. */
  private static final String META_RECONNECT_DATA_UUID =
      "com.google.android.connecteddevice.reconnect_data_uuid";

  /** {@code String} UUID for characteristic that contains the advertise data. */
  private static final String META_ADVERTISE_DATA_CHARACTERISTIC_UUID =
      "com.google.android.connecteddevice.advertise_data_characteristic_uuid";
  /** {@code String} UUID for write characteristic. */
  private static final String META_WRITE_UUID = "com.google.android.connecteddevice.write_uuid";
  /** {@code String} UUID for read characteristic. */
  private static final String META_READ_UUID = "com.google.android.connecteddevice.read_uuid";
  /** {@code int} Number of bytes for the default BLE MTU size. */
  private static final String META_DEFAULT_MTU_BYTES =
      "com.google.android.connecteddevice.default_mtu_bytes";
  /** {@code int} Maximum number of bytes each SPP packet can contain. */
  private static final String META_SPP_PACKET_BYTES =
      "com.google.android.connecteddevice.spp_packet_bytes";
  /** {@code boolean} Whether to compress outgoing messages. */
  private static final String META_COMPRESS_OUTGOING_MESSAGES =
      "com.google.android.connecteddevice.compress_outgoing_messages";
  /** {@code boolean} Enable BLE proxy. */
  private static final String META_ENABLE_PROXY = "com.google.android.connecteddevice.enable_proxy";
  /** {@code boolean} Enable a capabilities exchange during association. */
  private static final String META_ENABLE_CAPABILITIES_EXCHANGE =
      "com.google.android.connecteddevice.enable_capabilities_exchange";

  private static final boolean SPP_ENABLED_BY_DEFAULT = false;

  private static final boolean PROXY_ENABLED_BY_DEFAULT = false;

  private static final String DEFAULT_RECONNECT_UUID = "000000e0-0000-1000-8000-00805f9b34fb";

  private static final String DEFAULT_RECONNECT_DATA_UUID = "00000020-0000-1000-8000-00805f9b34fb";

  private static final String DEFAULT_ADVERTISE_DATA_CHARACTERISTIC_UUID =
      "24289b40-af40-4149-a5f4-878ccff87566";

  private static final String DEFAULT_WRITE_UUID = "5e2a68a5-27be-43f9-8d1e-4546976fabd7";

  private static final String DEFAULT_READ_UUID = "5e2a68a6-27be-43f9-8d1e-4546976fabd7";

  /** Max allowed for iOS. */
  private static final int DEFAULT_MTU_SIZE = 185;

  private static final int DEFAULT_SPP_PACKET_SIZE = 700;

  private static final boolean ENABLE_COMPRESSION_BY_DEFAULT = true;

  private static final boolean ENABLE_CAPABILITIES_EXCHANGE_BY_DEFAULT = false;

  /**
   * When a client calls {@link Context#bindService(Intent, ServiceConnection, int)} to get the
   * {@link IAssociatedDeviceManager}, this action is required in the param {@link Intent}.
   */
  public static final String ACTION_BIND_ASSOCIATION =
      "com.google.android.connecteddevice.BIND_ASSOCIATION";

  private final BroadcastReceiver bleBroadcastReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (BluetoothAdapter.ACTION_BLE_STATE_CHANGED.equals(intent.getAction())) {
            onBluetoothStateChanged(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1));
          }
        }
      };

  private final AtomicBoolean isSppServiceBound = new AtomicBoolean(false);

  private final List<RemoteFeature> localFeatures = new ArrayList<>();

  private final AtomicBoolean isEveryFeatureInitialized = new AtomicBoolean(false);

  private final OnRemoteCallbackSetListener onRemoteCallbackSetListener =
      isSet -> {
        if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
          initializeFeatures();
        }
        isSppServiceBound.set(isSet);
      };

  private ConnectedDeviceManager connectedDeviceManager;

  private LoggingManager loggingManager;

  private boolean isSppSupported;

  private ConnectedDeviceManagerBinder connectedDeviceManagerBinder;

  private AssociationBinder associationBinder;

  private ConnectedDeviceSppDelegateBinder sppDelegateBinder;

  @Override
  public void onCreate() {
    super.onCreate();
    logd(TAG, "Service created.");
    EventLog.onServiceStarted();
    isSppSupported = getMetaBoolean(META_ENABLE_SPP, SPP_ENABLED_BY_DEFAULT);

    ConnectedDeviceStorage storage = new ConnectedDeviceStorage(this);
    boolean isCompressionEnabled =
        getMetaBoolean(META_COMPRESS_OUTGOING_MESSAGES, ENABLE_COMPRESSION_BY_DEFAULT);
    boolean isCapabilitiesEligible =
        getMetaBoolean(META_ENABLE_CAPABILITIES_EXCHANGE, ENABLE_CAPABILITIES_EXCHANGE_BY_DEFAULT);
    sppDelegateBinder = new ConnectedDeviceSppDelegateBinder(onRemoteCallbackSetListener);
    CarBluetoothManager carBluetoothManager;
    if (isSppSupported) {
      carBluetoothManager = createSppManager(storage, isCompressionEnabled, isCapabilitiesEligible);
    } else {
      carBluetoothManager = createBleManager(storage, isCompressionEnabled, isCapabilitiesEligible);
    }
    connectedDeviceManager =
        new ConnectedDeviceManager(carBluetoothManager, storage, sppDelegateBinder);
    loggingManager = new LoggingManager(this);
    connectedDeviceManagerBinder =
        new ConnectedDeviceManagerBinder(connectedDeviceManager, loggingManager);
    populateFeatures(storage);
    associationBinder = new AssociationBinder(connectedDeviceManager);
    registerReceiver(
        bleBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_BLE_STATE_CHANGED));
    if (!isSppSupported && BluetoothAdapter.getDefaultAdapter().isLeEnabled()) {
      initializeFeatures();
    }
  }

  private void populateFeatures(ConnectedDeviceStorage storage) {
    // TODO(b/187523735) Remove listener from here and place in LoggingManager
    Logger logger = Logger.getLogger();
    logd(TAG, "Registering listener for logger.");
    loggingManager.addOnLogRequestedListener(
        logger.getLoggerId(),
        () -> loggingManager.prepareLocalLogRecords(logger.getLoggerId(), logger.toByteArray()),
        Executors.newSingleThreadExecutor());
    localFeatures.add(new LoggingFeature(this, connectedDeviceManagerBinder, loggingManager));
    localFeatures.add(new SystemFeature(this, connectedDeviceManagerBinder, storage));
  }

  private CarBluetoothManager createSppManager(
      @NonNull ConnectedDeviceStorage storage,
      boolean isCompressionEnabled,
      boolean isCapabilitiesEligible) {
    UUID sppServiceUuid = UUID.fromString(requireMetaString(META_SPP_SERVICE_UUID));
    int maxSppPacketSize = getMetaInt(META_SPP_PACKET_BYTES, DEFAULT_SPP_PACKET_SIZE);
    return new CarSppManager(
        sppDelegateBinder,
        storage,
        sppServiceUuid,
        maxSppPacketSize,
        isCompressionEnabled,
        isCapabilitiesEligible);
  }

  private CarBluetoothManager createBleManager(
      @NonNull ConnectedDeviceStorage storage,
      boolean isCompressionEnabled,
      boolean isCapabilitiesEligible) {
    UUID associationUuid = UUID.fromString(requireMetaString(META_ASSOCIATION_SERVICE_UUID));
    UUID reconnectUuid =
        UUID.fromString(getMetaString(META_RECONNECT_SERVICE_UUID, DEFAULT_RECONNECT_UUID));
    UUID reconnectDataUuid =
        UUID.fromString(getMetaString(META_RECONNECT_DATA_UUID, DEFAULT_RECONNECT_DATA_UUID));
    UUID advertiseDataCharacteristicUuid =
        UUID.fromString(
            getMetaString(
                META_ADVERTISE_DATA_CHARACTERISTIC_UUID,
                DEFAULT_ADVERTISE_DATA_CHARACTERISTIC_UUID));
    UUID writeUuid = UUID.fromString(getMetaString(META_WRITE_UUID, DEFAULT_WRITE_UUID));
    UUID readUuid = UUID.fromString(getMetaString(META_READ_UUID, DEFAULT_READ_UUID));
    int defaultMtuSize = getMetaInt(META_DEFAULT_MTU_BYTES, DEFAULT_MTU_SIZE);
    boolean isProxyEnabled = getMetaBoolean(META_ENABLE_PROXY, PROXY_ENABLED_BY_DEFAULT);
    BlePeripheralManager blePeripheralManager;
    if (isProxyEnabled) {
      logi(TAG, "Initializing with ProxyBlePeripheralManager");
      blePeripheralManager = new ProxyBlePeripheralManager(this);
    } else {
      blePeripheralManager = new OnDeviceBlePeripheralManager(this);
    }
    return new CarBlePeripheralManager(
        blePeripheralManager,
        storage,
        associationUuid,
        reconnectUuid,
        reconnectDataUuid,
        advertiseDataCharacteristicUuid,
        writeUuid,
        readUuid,
        MAX_ADVERTISEMENT_DURATION,
        defaultMtuSize,
        isCompressionEnabled,
        new BluetoothRfcommChannel(sppDelegateBinder),
        isCapabilitiesEligible);
  }

  @Override
  public IBinder onBind(Intent intent) {
    if (intent == null || intent.getAction() == null) {
      return null;
    }
    logd(TAG, "Service bound. Action: " + intent.getAction());
    String action = intent.getAction();
    switch (action) {
      case ACTION_BIND_ASSOCIATION:
        return associationBinder;
      case RemoteFeature.ACTION_BIND_REMOTE_FEATURE:
        return connectedDeviceManagerBinder;
      case ConnectedDeviceSppDelegateBinder.ACTION_BIND_SPP:
        return sppDelegateBinder;
      default:
        loge(TAG, "Unexpected action found while binding: " + action);
        return null;
    }
  }

  @Override
  public void onDestroy() {
    logd(TAG, "Service destroyed.");
    unregisterReceiver(bleBroadcastReceiver);
    cleanup();
    super.onDestroy();
  }

  private void onBluetoothStateChanged(int state) {
    logd(TAG, "onBluetoothStateChanged: " + state);
    switch (state) {
      case BluetoothAdapter.STATE_ON:
        EventLog.onBleOn();
        if (!isSppSupported || isSppServiceBound.get()) {
          initializeFeatures();
        }
        break;
      case BluetoothAdapter.STATE_OFF:
        cleanup();
        break;
      default:
        // Ignore.
    }
  }

  private void cleanup() {
    logd(TAG, "Cleaning up features.");
    if (!isEveryFeatureInitialized.get()) {
      logd(TAG, "Features are already cleaned up. No need to clean up again.");
      return;
    }
    connectedDeviceManager.reset();
    loggingManager.reset();
    for (RemoteFeature feature : localFeatures) {
      feature.stop();
    }
    isEveryFeatureInitialized.set(false);
  }

  private void initializeFeatures() {
    // Room cannot be accessed on main thread.
    Executors.defaultThreadFactory()
        .newThread(
            () -> {
              logd(TAG, "Initializing features.");
              if (isEveryFeatureInitialized.get()) {
                logd(TAG, "Features are already initialized. No need to initialize again.");
                return;
              }
              connectedDeviceManager.start();
              for (RemoteFeature feature : localFeatures) {
                feature.start();
              }
              isEveryFeatureInitialized.set(true);
            })
        .start();
  }

  /** Returns the service's instance of {@link ConnectedDeviceManager}. */
  protected ConnectedDeviceManager getConnectedDeviceManager() {
    return connectedDeviceManager;
  }
}
