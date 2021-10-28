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
import static java.util.Objects.requireNonNull;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.ParcelUuid;
import androidx.annotation.NonNull;
import com.google.android.connecteddevice.ConnectedDeviceManager;
import com.google.android.connecteddevice.api.CompanionConnector;
import com.google.android.connecteddevice.api.Connector;
import com.google.android.connecteddevice.api.IAssociatedDeviceManager;
import com.google.android.connecteddevice.api.IAssociationCallback;
import com.google.android.connecteddevice.api.IConnectedDeviceManager;
import com.google.android.connecteddevice.api.IConnectionCallback;
import com.google.android.connecteddevice.api.IDeviceAssociationCallback;
import com.google.android.connecteddevice.api.IDeviceCallback;
import com.google.android.connecteddevice.api.IFeatureCoordinator;
import com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener;
import com.google.android.connecteddevice.api.IOnLogRequestedListener;
import com.google.android.connecteddevice.connection.CarBluetoothManager;
import com.google.android.connecteddevice.connection.ble.CarBlePeripheralManager;
import com.google.android.connecteddevice.connection.spp.CarSppManager;
import com.google.android.connecteddevice.core.DeviceController;
import com.google.android.connecteddevice.core.FeatureCoordinator;
import com.google.android.connecteddevice.core.MultiProtocolDeviceController;
import com.google.android.connecteddevice.logging.LoggingFeature;
import com.google.android.connecteddevice.logging.LoggingManager;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.DeviceMessage;
import com.google.android.connecteddevice.model.TransportProtocols;
import com.google.android.connecteddevice.oob.BluetoothRfcommChannel;
import com.google.android.connecteddevice.oob.OobChannelFactory;
import com.google.android.connecteddevice.oob.OobRunner;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage;
import com.google.android.connecteddevice.system.SystemFeature;
import com.google.android.connecteddevice.transport.ConnectionProtocol;
import com.google.android.connecteddevice.transport.ble.BlePeripheralManager;
import com.google.android.connecteddevice.transport.ble.BlePeripheralProtocol;
import com.google.android.connecteddevice.transport.ble.OnDeviceBlePeripheralManager;
import com.google.android.connecteddevice.transport.proxy.NetworkSocketFactory;
import com.google.android.connecteddevice.transport.proxy.ProxyBlePeripheralManager;
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder;
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder.OnRemoteCallbackSetListener;
import com.google.android.connecteddevice.transport.spp.SppProtocol;
import com.google.android.connecteddevice.util.EventLog;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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

  /**
   * {@code string-array} Supported transport protocols. If the {@link FeatureCoordinator} is not
   * enabled, the first protocol in the list will be selected to initialize the
   * {@link ConnectedDeviceManager}.
   */
  private static final String META_SUPPORTED_TRANSPORT_PROTOCOLS =
      "com.google.android.connecteddevice.transport_protocols";
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
  /**
   * {@code boolean} Use the {@link IFeatureCoordinator} instead of {@link IConnectedDeviceManager}
   */
  private static final String META_ENABLE_FEATURE_COORDINATOR =
      "com.google.android.connecteddevice.enable_feature_coordinator";

  private static final String META_SUPPORTED_OOB_CHANNELS =
      "com.google.android.connecteddevice.supported_oob_channels";

  private static final boolean PROXY_ENABLED_BY_DEFAULT = false;

  private static final String DEFAULT_RECONNECT_UUID = "000000e0-0000-1000-8000-00805f9b34fb";

  private static final String DEFAULT_RECONNECT_DATA_UUID = "00000020-0000-1000-8000-00805f9b34fb";

  private static final String DEFAULT_ADVERTISE_DATA_CHARACTERISTIC_UUID =
      "24289b40-af40-4149-a5f4-878ccff87566";

  private static final String DEFAULT_WRITE_UUID = "5e2a68a5-27be-43f9-8d1e-4546976fabd7";

  private static final String DEFAULT_READ_UUID = "5e2a68a6-27be-43f9-8d1e-4546976fabd7";

  private static final int DEFAULT_MTU_SIZE = 185; // Max allowed for iOS.

  private static final int DEFAULT_SPP_PACKET_SIZE = 700;

  private static final boolean ENABLE_COMPRESSION_BY_DEFAULT = true;

  private static final boolean ENABLE_CAPABILITIES_EXCHANGE_BY_DEFAULT = false;

  private static final boolean ENABLE_FEATURE_COORDINATOR_BY_DEFAULT = false;

  private static final String[] DEFAULT_TRANSPORT_PROTOCOLS =
      { TransportProtocols.PROTOCOL_BLE_PERIPHERAL };

  private final BroadcastReceiver bluetoothBroadcastReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
            onBluetoothStateChanged(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1));
          }
        }
      };

  private final AtomicBoolean isSppServiceBound = new AtomicBoolean(false);

  private final AtomicBoolean isEveryFeatureInitialized = new AtomicBoolean(false);

  private final ScheduledExecutorService scheduledExecutorService =
      Executors.newSingleThreadScheduledExecutor();

  private ConnectedDeviceManager connectedDeviceManager;

  private LoggingManager loggingManager;

  private FeatureCoordinator featureCoordinator;

  private ConnectedDeviceStorage storage;

  private IConnectedDeviceManager.Stub connectedDeviceManagerBinder;

  private IAssociatedDeviceManager.Stub associationBinder;

  private ConnectedDeviceSppDelegateBinder sppDelegateBinder;

  private boolean useFeatureCoordinator;

  private List<String> supportedTransportProtocols;

  private SystemFeature systemFeature;

  private LoggingFeature loggingFeature;

  @Override
  public void onCreate() {
    super.onCreate();
    logd(TAG, "Service created.");

    EventLog.onServiceStarted();
    BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
    OnRemoteCallbackSetListener onRemoteCallbackSetListener = isSet -> {
      if (bluetoothManager.getAdapter().isEnabled()) {
        initializeFeatures();
      }
      isSppServiceBound.set(isSet);
    };
    supportedTransportProtocols =
        Arrays.asList(
            requireNonNull(
                getMetaStringArray(
                    META_SUPPORTED_TRANSPORT_PROTOCOLS,
                    DEFAULT_TRANSPORT_PROTOCOLS)));
    if (supportedTransportProtocols.isEmpty()) {
      loge(TAG,
          "Transport protocols are empty. There must be at least one protocol provided to start "
              + "this service. Reverting to default values.");
      supportedTransportProtocols = Arrays.asList(DEFAULT_TRANSPORT_PROTOCOLS);
    }
    useFeatureCoordinator =
        getMetaBoolean(META_ENABLE_FEATURE_COORDINATOR, ENABLE_FEATURE_COORDINATOR_BY_DEFAULT);

    loggingManager = new LoggingManager(this);
    storage = new ConnectedDeviceStorage(this);
    sppDelegateBinder = new ConnectedDeviceSppDelegateBinder(onRemoteCallbackSetListener);
    if (useFeatureCoordinator) {
      initializeFeatureCoordinator();
    } else {
      initializeConnectedDeviceManager();
    }

    populateFeatures();

    registerReceiver(
        bluetoothBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    if (isTransportSupported(TransportProtocols.PROTOCOL_BLE_PERIPHERAL)
        && BluetoothAdapter.getDefaultAdapter().isEnabled()) {
      initializeFeatures();
    }
  }

  private void initializeFeatureCoordinator() {
    logd(TAG, "Initializing FeatureCoordinator version of the platform.");
    Set<ConnectionProtocol> protocols = new HashSet<>();
    for (String protocol : supportedTransportProtocols) {
      logd(TAG, "Adding protocol " + protocol + " to supported protocols.");
      switch (protocol) {
        case TransportProtocols.PROTOCOL_BLE_PERIPHERAL:
          protocols.add(createBlePeripheralProtocol());
          break;
        case TransportProtocols.PROTOCOL_SPP:
          protocols.add(createSppProtocol());
          break;
        default:
          loge(TAG, "Protocol type " + protocol + " is not recognized. Ignoring.");
      }
    }

    OobRunner oobRunner =
        new OobRunner(
            new OobChannelFactory(sppDelegateBinder),
            Arrays.asList(
                requireNonNull(
                    getMetaStringArray(
                      META_SUPPORTED_OOB_CHANNELS,
                      /* defaultValue= */ new String[0]))));
    UUID associationUuid = UUID.fromString(requireMetaString(META_ASSOCIATION_SERVICE_UUID));
    DeviceController deviceController =
        new MultiProtocolDeviceController(protocols, storage, oobRunner, associationUuid);
    featureCoordinator = new FeatureCoordinator(deviceController, storage, loggingManager);
    logd(TAG, "Wrapping FeatureCoordinator in legacy binders for backwards compatibility.");
    connectedDeviceManagerBinder = createConnectedDeviceManagerWrapper();
    associationBinder = createAssociatedDeviceManagerWrapper();
  }

  private BlePeripheralProtocol createBlePeripheralProtocol() {
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
      blePeripheralManager =
          new ProxyBlePeripheralManager(new NetworkSocketFactory(this), scheduledExecutorService);
    } else {
      blePeripheralManager = new OnDeviceBlePeripheralManager(this);
    }
    return new BlePeripheralProtocol(
        blePeripheralManager,
        reconnectUuid,
        reconnectDataUuid,
        advertiseDataCharacteristicUuid,
        writeUuid,
        readUuid,
        MAX_ADVERTISEMENT_DURATION,
        defaultMtuSize);
  }

  private SppProtocol createSppProtocol() {
    int maxSppPacketSize = getMetaInt(META_SPP_PACKET_BYTES, DEFAULT_SPP_PACKET_SIZE);
    return new SppProtocol(sppDelegateBinder, maxSppPacketSize);
  }

  private void initializeConnectedDeviceManager() {
    logd(TAG, "Initializing ConnectedDeviceManager version of the platform.");
    boolean isCompressionEnabled =
        getMetaBoolean(META_COMPRESS_OUTGOING_MESSAGES, ENABLE_COMPRESSION_BY_DEFAULT);
    boolean isCapabilitiesEligible =
        getMetaBoolean(META_ENABLE_CAPABILITIES_EXCHANGE, ENABLE_CAPABILITIES_EXCHANGE_BY_DEFAULT);
    CarBluetoothManager carBluetoothManager;
    String defaultProtocol = supportedTransportProtocols.get(0);
    logd(TAG, "Setting " + defaultProtocol + " as the transport.");
    switch (defaultProtocol) {
      case TransportProtocols.PROTOCOL_SPP:
        carBluetoothManager =
            createSppManager(storage, isCompressionEnabled, isCapabilitiesEligible);
        break;
      case TransportProtocols.PROTOCOL_BLE_PERIPHERAL:
      default:
        carBluetoothManager =
            createBleManager(storage, isCompressionEnabled, isCapabilitiesEligible);
    }
    connectedDeviceManager = new ConnectedDeviceManager(carBluetoothManager, storage);
    connectedDeviceManagerBinder =
        new ConnectedDeviceManagerBinder(connectedDeviceManager, loggingManager);
    associationBinder = new AssociationBinder(connectedDeviceManager);
  }

  private void populateFeatures() {
    logd(TAG, "Populating features.");
    if (featureCoordinator != null) {
      loggingFeature =
          new LoggingFeature(
              this,
              loggingManager,
              CompanionConnector.createLocalConnector(
                  this, Connector.USER_TYPE_DRIVER, featureCoordinator));
      systemFeature =
          new SystemFeature(
              this,
              storage,
              CompanionConnector.createLocalConnector(
                  this, Connector.USER_TYPE_DRIVER, featureCoordinator));
    } else {
      loggingFeature =
          new LoggingFeature(
              this,
              loggingManager,
              CompanionConnector.createLocalConnector(
                  this, Connector.USER_TYPE_ALL, connectedDeviceManagerBinder, associationBinder));
      systemFeature =
          new SystemFeature(
              this,
              storage,
              CompanionConnector.createLocalConnector(
                  this, Connector.USER_TYPE_ALL, connectedDeviceManagerBinder, associationBinder));
    }
  }

  private CarBluetoothManager createSppManager(
      @NonNull ConnectedDeviceStorage storage,
      boolean isCompressionEnabled,
      boolean isCapabilitiesEligible) {
    UUID sppServiceUuid = UUID.fromString(requireMetaString(META_ASSOCIATION_SERVICE_UUID));
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
      blePeripheralManager =
          new ProxyBlePeripheralManager(new NetworkSocketFactory(this), scheduledExecutorService);
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
      case CompanionConnector.ACTION_BIND_ASSOCIATION:
        return associationBinder;
      case CompanionConnector.ACTION_BIND_REMOTE_FEATURE:
        return connectedDeviceManagerBinder;
      case ConnectedDeviceSppDelegateBinder.ACTION_BIND_SPP:
        return sppDelegateBinder;
      case CompanionConnector.ACTION_BIND_FEATURE_COORDINATOR:
        return featureCoordinator;
      default:
        loge(TAG, "Unexpected action found while binding: " + action);
        return null;
    }
  }

  @Override
  public void onDestroy() {
    logd(TAG, "Service was destroyed.");

    scheduledExecutorService.shutdown();
    unregisterReceiver(bluetoothBroadcastReceiver);
    cleanup();
    super.onDestroy();
  }

  private void onBluetoothStateChanged(int state) {
    logd(TAG, "onBluetoothStateChanged: " + state);
    switch (state) {
      case BluetoothAdapter.STATE_ON:
        EventLog.onBleOn();
        if (isTransportSupported(TransportProtocols.PROTOCOL_BLE_PERIPHERAL)
            || isSppServiceBound.get()) {
          initializeFeatures();
        }
        break;
      case BluetoothAdapter.STATE_TURNING_OFF:
      case BluetoothAdapter.STATE_OFF:
        cleanup();
        break;
      default:
        // Ignore.
    }
  }

  private void cleanup() {
    logd(TAG, "Cleaning up features.");
    if (!isEveryFeatureInitialized.compareAndSet(true, false)) {
      logd(TAG, "Features are already cleaned up. No need to clean up again.");
      return;
    }
    if (useFeatureCoordinator) {
      featureCoordinator.reset();
    } else {
      connectedDeviceManager.reset();
    }
    loggingManager.reset();
    systemFeature.stop();
    loggingFeature.stop();
  }

  private void initializeFeatures() {
    if (!isEveryFeatureInitialized.compareAndSet(false, true)) {
      logd(TAG, "Features are already initialized. No need to initialize again.");
      return;
    }
    // Room cannot be accessed on main thread.
    Executors.defaultThreadFactory()
        .newThread(
            () -> {
              logd(TAG, "Initializing features.");
              loggingManager.start();
              if (useFeatureCoordinator) {
                featureCoordinator.start();
              } else {
                connectedDeviceManager.start();
              }
              systemFeature.start();
              loggingFeature.start();
            })
        .start();
  }

  private boolean isTransportSupported(@NonNull String protocol) {
    return supportedTransportProtocols.contains(protocol);
  }

  private IConnectedDeviceManager.Stub createConnectedDeviceManagerWrapper() {
    return new IConnectedDeviceManager.Stub() {
      @Override
      public List<ConnectedDevice> getActiveUserConnectedDevices() {
        return featureCoordinator.getConnectedDevicesForDriver();
      }

      @Override
      public void registerActiveUserConnectionCallback(IConnectionCallback callback) {
        featureCoordinator.registerDriverConnectionCallback(callback);
      }

      @Override
      public void unregisterConnectionCallback(IConnectionCallback callback) {
        featureCoordinator.unregisterConnectionCallback(callback);
      }

      @Override
      public void registerDeviceCallback(
          ConnectedDevice connectedDevice, ParcelUuid recipientId, IDeviceCallback callback) {
        featureCoordinator.registerDeviceCallback(connectedDevice, recipientId, callback);
      }

      @Override
      public void unregisterDeviceCallback(
          ConnectedDevice connectedDevice, ParcelUuid recipientId, IDeviceCallback callback) {
        featureCoordinator.unregisterDeviceCallback(connectedDevice, recipientId, callback);
      }

      @Override
      public boolean sendMessage(ConnectedDevice connectedDevice, DeviceMessage message) {
        return featureCoordinator.sendMessage(connectedDevice, message);
      }

      @Override
      public void registerDeviceAssociationCallback(IDeviceAssociationCallback callback) {
        featureCoordinator.registerDeviceAssociationCallback(callback);
      }

      @Override
      public void unregisterDeviceAssociationCallback(IDeviceAssociationCallback callback) {
        featureCoordinator.unregisterDeviceAssociationCallback(callback);
      }

      @Override
      public void registerOnLogRequestedListener(int loggerId, IOnLogRequestedListener listener) {
        featureCoordinator.registerOnLogRequestedListener(loggerId, listener);
      }

      @Override
      public void unregisterOnLogRequestedListener(int loggerId, IOnLogRequestedListener listener) {
        featureCoordinator.unregisterOnLogRequestedListener(loggerId, listener);
      }

      @Override
      public void processLogRecords(int loggerId, byte[] logRecords) {
        featureCoordinator.processLogRecords(loggerId, logRecords);
      }
    };
  }

  private IAssociatedDeviceManager.Stub createAssociatedDeviceManagerWrapper() {
    return new IAssociatedDeviceManager.Stub() {

      @Override
      public void startAssociation(IAssociationCallback callback) {
        featureCoordinator.startAssociation(callback);
      }

      @Override
      public void stopAssociation() {
        featureCoordinator.stopAssociation();
      }

      @Override
      public void retrievedActiveUserAssociatedDevices(
          IOnAssociatedDevicesRetrievedListener listener) {
        featureCoordinator.retrieveAssociatedDevicesForDriver(listener);
      }

      @Override
      public void acceptVerification() {
        featureCoordinator.acceptVerification();
      }

      @Override
      public void removeAssociatedDevice(String deviceId) {
        featureCoordinator.removeAssociatedDevice(deviceId);
      }

      @Override
      public void registerDeviceAssociationCallback(IDeviceAssociationCallback callback) {
        featureCoordinator.registerDeviceAssociationCallback(callback);
      }

      @Override
      public void unregisterDeviceAssociationCallback(IDeviceAssociationCallback callback) {
        featureCoordinator.unregisterDeviceAssociationCallback(callback);
      }

      @Override
      public List<ConnectedDevice> getActiveUserConnectedDevices() {
        return featureCoordinator.getConnectedDevicesForDriver();
      }

      @Override
      public void registerConnectionCallback(IConnectionCallback callback) {
        featureCoordinator.registerDriverConnectionCallback(callback);
      }

      @Override
      public void unregisterConnectionCallback(IConnectionCallback callback) {
        featureCoordinator.unregisterConnectionCallback(callback);
      }

      @Override
      public void enableAssociatedDeviceConnection(String deviceId) {
        featureCoordinator.enableAssociatedDeviceConnection(deviceId);
      }

      @Override
      public void disableAssociatedDeviceConnection(String deviceId) {
        featureCoordinator.disableAssociatedDeviceConnection(deviceId);
      }
    };
  }
}
