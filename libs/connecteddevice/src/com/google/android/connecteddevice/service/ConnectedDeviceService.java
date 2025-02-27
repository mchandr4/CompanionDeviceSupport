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

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.UserHandle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.R;
import com.google.android.connecteddevice.api.CompanionConnector;
import com.google.android.connecteddevice.api.Connector;
import com.google.android.connecteddevice.api.SafeConnector;
import com.google.android.connecteddevice.beacon.BeaconFeature;
import com.google.android.connecteddevice.core.DeviceController;
import com.google.android.connecteddevice.core.FeatureCoordinator;
import com.google.android.connecteddevice.core.MultiProtocolDeviceController;
import com.google.android.connecteddevice.logging.LoggingFeature;
import com.google.android.connecteddevice.logging.LoggingManager;
import com.google.android.connecteddevice.oob.OobRunner;
import com.google.android.connecteddevice.ping.PeriodicPingFeature;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage;
import com.google.android.connecteddevice.system.SystemFeature;
import com.google.android.connecteddevice.transport.IConnectionProtocol;
import com.google.android.connecteddevice.transport.ProtocolDelegate;
import com.google.android.connecteddevice.util.EventLog;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Early start service that hosts the core companion platform. */
public final class ConnectedDeviceService extends TrunkService {

  private static final String TAG = "ConnectedDeviceService";

  /** {@code String} UUID for association advertisement. */
  private static final String META_ASSOCIATION_SERVICE_UUID =
      "com.google.android.connecteddevice.association_service_uuid";

  private static final String META_ENABLE_BEACON =
      "com.google.android.connecteddevice.enable_beacon";

  private static final String META_ENABLE_PERIODIC_PING =
      "com.google.android.connecteddevice.enable_periodic_ping";

  private static final String META_EAP_OOB_PROTOCOL_NAME =
      "com.google.android.connecteddevice.car_eap_oob_protocol_name";

  // The name should be reverse-DNS strings.
  // Source:
  // https://developer.apple.com/library/archive/featuredarticles/ExternalAccessoryPT/Introduction/Introduction.html#//apple_ref/doc/uid/TP40009502
  private static final String DEFAULT_EAP_OOB_PROTOCOL_NAME =
      "com.google.companion.oob-association";

  private static final String META_ENABLE_PASSENGER =
      "com.google.android.connecteddevice.enable_passenger";

  private final AtomicBoolean isEveryFeatureInitialized = new AtomicBoolean(false);

  private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

  private final ProtocolDelegate protocolDelegate = new ProtocolDelegate();

  private final BroadcastReceiver userRemovedBroadcastReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          UserHandle userHandle = intent.getParcelableExtra(Intent.EXTRA_USER);
          onUserRemoved(userHandle);
        }
      };

  private LoggingManager loggingManager;

  private FeatureCoordinator featureCoordinator;

  private ConnectedDeviceStorage storage;

  private SystemFeature systemFeature;

  private LoggingFeature loggingFeature;

  @Nullable private PeriodicPingFeature periodicPingFeature;

  @Nullable private BeaconFeature beaconFeature;

  @Override
  @SuppressLint("UnprotectedReceiver") // ACTION_USER_REMOVED is a protected broadcast.
  public void onCreate() {
    super.onCreate();
    logd(
        TAG,
        "Service created. Companion SDK version is "
            + getResources().getString(R.string.hu_companion_sdk_version));
    EventLog.onServiceStarted();
    protocolDelegate.setCallback(
        new ProtocolDelegate.Callback() {
          @Override
          public void onProtocolAdded(@NonNull IConnectionProtocol protocol) {
            logd(TAG, "A new protocol has been added.");
            initializeFeatures();
          }

          @Override
          public void onProtocolRemoved(@NonNull IConnectionProtocol protocol) {
            logd(TAG, "A protocol has been removed.");
            if (protocolDelegate.isEmpty()) {
              logd(TAG, "There are no remaining protocols. Cleaning up.");
              cleanup();
            }
          }
        });
    loggingManager = new LoggingManager(this);
    storage = new ConnectedDeviceStorage(this);

    initializeFeatureCoordinator();
    populateFeatures();
    logd(TAG, "Registering broadcast receiver for intent " + Intent.ACTION_USER_REMOVED);
    registerReceiver(userRemovedBroadcastReceiver, new IntentFilter(Intent.ACTION_USER_REMOVED));
  }

  private void initializeFeatureCoordinator() {
    if (featureCoordinator != null) {
      return;
    }
    logd(TAG, "Initializing FeatureCoordinator version of the platform.");
    UUID associationUuid = UUID.fromString(requireMetaString(META_ASSOCIATION_SERVICE_UUID));
    boolean enablePassenger = getMetaBoolean(META_ENABLE_PASSENGER, false);
    String oobProtocolName =
        getMetaString(META_EAP_OOB_PROTOCOL_NAME, DEFAULT_EAP_OOB_PROTOCOL_NAME);
    OobRunner oobRunner = new OobRunner(protocolDelegate, oobProtocolName);
    DeviceController deviceController =
        new MultiProtocolDeviceController(
            /* context= */ this,
            /* lifecycleOwner= */ this,
            protocolDelegate,
            storage,
            oobRunner,
            associationUuid,
            enablePassenger);
    featureCoordinator =
        new FeatureCoordinator(
            /* lifecycleOwner= */ this, deviceController, storage, loggingManager);
    logd(TAG, "Wrapping FeatureCoordinator in legacy binders for backwards compatibility.");
  }

  private void populateFeatures() {
    logd(TAG, "Populating features.");
    loggingFeature =
        new LoggingFeature(
            this,
            loggingManager,
            CompanionConnector.createLocalConnector(
                this, Connector.USER_TYPE_DRIVER, featureCoordinator));
    systemFeature =
        new SystemFeature(
            /* context= */ this,
            /* lifecycleOwner= */ this,
            storage,
            CompanionConnector.createLocalConnector(
                this, Connector.USER_TYPE_ALL, featureCoordinator));
    if (getMetaBoolean(META_ENABLE_PERIODIC_PING, false)) {
      logd(TAG, "Instantiating PeriodicPingFeature.");
      periodicPingFeature =
          new PeriodicPingFeature(
              CompanionConnector.createLocalConnector(
                  this, Connector.USER_TYPE_ALL, featureCoordinator));
    }
    if (getMetaBoolean(META_ENABLE_BEACON, false)) {
      logd(TAG, "Instantiating BeaconFeature.");
      beaconFeature =
          BeaconFeature.create(
              this,
              CompanionConnector.createLocalConnector(
                  this, Connector.USER_TYPE_ALL, featureCoordinator));
    }
  }

  private void onUserRemoved(UserHandle userHandle) {
    databaseExecutor.execute(
        () -> {
          int userId = userHandle.getIdentifier();
          logd(TAG, "Received USER_REMOVED broadcast for " + userId);

          FeatureCoordinator featurecoordinator = this.featureCoordinator;
          if (featurecoordinator == null) {
            logd(TAG, "User removed before feature coordinator is initiated. Ignored");
            return;
          }

          featureCoordinator.removeAssociatedDevicesForUser(userId);
        });
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    IBinder unused = super.onBind(intent);

    if (intent == null || intent.getAction() == null) {
      // This is likely the binding intent from VendorServiceController, which controls the
      // lifecycle of this service. The controller ignores the returned IBinder, so anything works.
      logd(TAG, "onBind: received intent with null action. Returning featureCoordinator.");
      return featureCoordinator;
    }
    String action = intent.getAction();
    logd(TAG, "Service bound. Action: " + action);
    switch (action) {
      case CompanionProtocolRegistry.ACTION_BIND_PROTOCOL:
        return protocolDelegate;
      case Connector.ACTION_BIND_FEATURE_COORDINATOR:
        return featureCoordinator;
      case SafeConnector.ACTION_BIND_SAFE_FEATURE_COORDINATOR:
        return featureCoordinator.getSafeFeatureCoordinator();
      case SafeConnector.ACTION_QUERY_API_VERSION:
        logd(TAG, "Return binder version to remote process");
        return binderVersion.asBinder();
      default:
        loge(TAG, "onBinder: unexpected action: " + action + ". Returning featureCoordinator");
        return featureCoordinator;
    }
  }

  @Override
  public void onDestroy() {
    logd(TAG, "Service was destroyed.");
    unregisterReceiver(userRemovedBroadcastReceiver);
    databaseExecutor.shutdown();
    cleanup();
    super.onDestroy();
  }

  /**
   * Writes the Companion library version for dumpsys or bug reports.
   *
   * <p>Dump for this service can be viewed using this command: {@code adb shell dumpsys activity
   * service
   * com.google.android.companiondevicesupport/com.google.android.connecteddevice.service.ConnectedDeviceService}
   */
  @Override
  protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
    super.dump(fd, writer, args);
    if (writer == null) {
      loge(TAG, "Failed to dump service info: writer is null.");
      return;
    }
    writer.printf(
        "Companion SDK version is %s\n",
        getResources().getString(R.string.hu_companion_sdk_version));
  }

  private void cleanup() {
    logd(TAG, "Cleaning up features.");
    if (!isEveryFeatureInitialized.compareAndSet(true, false)) {
      logd(TAG, "Features are already cleaned up. No need to clean up again.");
      return;
    }
    featureCoordinator.reset();
    loggingManager.reset();
    systemFeature.stop();
    loggingFeature.stop();
    if (periodicPingFeature != null) {
      periodicPingFeature.stop();
    }
    if (beaconFeature != null) {
      beaconFeature.stop();
    }
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
              featureCoordinator.start();
              systemFeature.start();
              loggingFeature.start();
              if (periodicPingFeature != null) {
                periodicPingFeature.start();
              }
              if (beaconFeature != null) {
                beaconFeature.start();
              }
            })
        .start();
  }
}
