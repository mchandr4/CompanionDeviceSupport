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

import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.NonNull;
import com.google.android.connecteddevice.api.CompanionConnector;
import com.google.android.connecteddevice.api.Connector;
import com.google.android.connecteddevice.core.DeviceController;
import com.google.android.connecteddevice.core.FeatureCoordinator;
import com.google.android.connecteddevice.core.MultiProtocolDeviceController;
import com.google.android.connecteddevice.logging.LoggingFeature;
import com.google.android.connecteddevice.logging.LoggingManager;
import com.google.android.connecteddevice.oob.OobRunner;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage;
import com.google.android.connecteddevice.system.SystemFeature;
import com.google.android.connecteddevice.transport.IConnectionProtocol;
import com.google.android.connecteddevice.transport.ProtocolDelegate;
import com.google.android.connecteddevice.util.EventLog;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Early start service that hosts the core companion platform. */
public final class ConnectedDeviceService extends TrunkService {

  private static final String TAG = "ConnectedDeviceService";

  /** {@code String} UUID for association advertisement. */
  private static final String META_ASSOCIATION_SERVICE_UUID =
      "com.google.android.connecteddevice.association_service_uuid";

  private static final String META_EAP_OOB_PROTOCOL_NAME =
      "com.google.android.connecteddevice.car_eap_oob_protocol_name";

  // The name should be reverse-DNS strings.
  // Source:
  // https://developer.apple.com/library/archive/featuredarticles/ExternalAccessoryPT/Introduction/Introduction.html#//apple_ref/doc/uid/TP40009502
  private static final String DEFAULT_EAP_OOB_PROTOCOL_NAME =
      "com.google.companion.oob-association";

  private static final String META_ENABLE_PASSENGER =
      "com.google.android.connecteddevice.enable_passenger";

  private static final boolean ENABLE_PASSENGER_BY_DEFAULT = false;

  private final AtomicBoolean isEveryFeatureInitialized = new AtomicBoolean(false);

  private final ScheduledExecutorService scheduledExecutorService =
      Executors.newSingleThreadScheduledExecutor();

  private final ProtocolDelegate protocolDelegate = new ProtocolDelegate();

  private LoggingManager loggingManager;

  private FeatureCoordinator featureCoordinator;

  private ConnectedDeviceStorage storage;

  private SystemFeature systemFeature;

  private LoggingFeature loggingFeature;

  @Override
  public void onCreate() {
    super.onCreate();
    logd(TAG, "Service created.");
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
  }

  private void initializeFeatureCoordinator() {
    if (featureCoordinator != null) {
      return;
    }
    logd(TAG, "Initializing FeatureCoordinator version of the platform.");
    UUID associationUuid = UUID.fromString(requireMetaString(META_ASSOCIATION_SERVICE_UUID));
    boolean enablePassenger = getMetaBoolean(META_ENABLE_PASSENGER, ENABLE_PASSENGER_BY_DEFAULT);
    String oobProtocolName =
        getMetaString(META_EAP_OOB_PROTOCOL_NAME, DEFAULT_EAP_OOB_PROTOCOL_NAME);
    OobRunner oobRunner = new OobRunner(protocolDelegate, oobProtocolName);
    DeviceController deviceController =
        new MultiProtocolDeviceController(
            protocolDelegate, storage, oobRunner, associationUuid, enablePassenger);
    featureCoordinator = new FeatureCoordinator(deviceController, storage, loggingManager);
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
            this,
            storage,
            CompanionConnector.createLocalConnector(
                this, Connector.USER_TYPE_ALL, featureCoordinator));
  }

  @Override
  public IBinder onBind(Intent intent) {
    if (intent == null || intent.getAction() == null) {
      return null;
    }
    logd(TAG, "Service bound. Action: " + intent.getAction());
    String action = intent.getAction();
    switch (action) {
      case CompanionProtocolRegistry.ACTION_BIND_PROTOCOL:
        return protocolDelegate;
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
    cleanup();
    super.onDestroy();
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
            })
        .start();
  }
}
