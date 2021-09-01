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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.UserManager;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.api.CompanionConnector;
import com.google.android.connecteddevice.api.IConnectedDeviceManager;
import com.google.android.connecteddevice.api.IFeatureCoordinator;

/**
 * Service responsible for starting services that must be run in the active foreground user.
 * External features running in the foreground user may bind with {@code ACTION_BIND_REMOTE_FEATURE}
 * to access the {@link IConnectedDeviceManager} instance.
 */
public final class ConnectedDeviceFgUserService extends TrunkService {

  private static final String TAG = "ConnectedDeviceFgUserService";

  /** {@code string-array} List of services to start after the user has unlocked. */
  private static final String META_UNLOCK_SERVICES =
      "com.google.android.connecteddevice.unlock_services";

  /**
   * {@code boolean} Use the {@link IFeatureCoordinator} instead of {@link IConnectedDeviceManager}
   */
  private static final String META_ENABLE_FEATURE_COORDINATOR =
      "com.google.android.connecteddevice.enable_feature_coordinator";

  private boolean receiversRegistered = true;

  private IFeatureCoordinator featureCoordinator;

  private IConnectedDeviceManager connectedDeviceManager;

  private boolean useFeatureCoordinator;

  private CompanionConnector connector;

  @Override
  public void onCreate() {
    super.onCreate();
    useFeatureCoordinator = getMetaBoolean(META_ENABLE_FEATURE_COORDINATOR, false);
    logd(TAG, "Starting service with feature coordinator: " + useFeatureCoordinator + ".");
    // Listen to the unlock event to know when a new user has come to the foreground and storage is
    // unlocked.
    registerReceiver(userUnlockedReceiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
    // Listen for user going to the background so we can clean up.
    registerReceiver(userBackgroundReceiver, new IntentFilter(Intent.ACTION_USER_BACKGROUND));
    connector = new CompanionConnector(this,
        new CompanionConnector.Callback() {
          @Override
          public void onConnected() {
            logd(TAG, "Successfully bound to platform.");
            featureCoordinator = connector.getFeatureCoordinator();
            connectedDeviceManager = connector.getConnectedDeviceManager();
          }

          @Override
          public void onDisconnected() {
            loge(TAG, "Lost connection to companion. Stopping service.");
            stopSelf();
          }

          @Override
          public void onFailedToConnect() {
            loge(TAG, "Unable to establish connection with companion. Stopping service.");
            stopSelf();
          }
        });
    connector.connect();
    UserManager userManager = getSystemService(UserManager.class);
    if (userManager.isUserUnlocked()) {
      logd(TAG, "User was already unlocked on service start.");
      onUserUnlocked();
    }
  }

  @Override
  public void onDestroy() {
    connector.disconnect();
    unregisterReceivers();
    logd(TAG, "Service was destroyed.");
    super.onDestroy();
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    if (intent == null || intent.getAction() == null) {
      return null;
    }

    String action = intent.getAction();
    logd(TAG, "Service bound. Action: " + action);
    if (action.equals(CompanionConnector.ACTION_BIND_REMOTE_FEATURE_FG)) {
        return connectedDeviceManager.asBinder();
    } else if (action.equals(CompanionConnector.ACTION_BIND_FEATURE_COORDINATOR_FG)
        && useFeatureCoordinator) {
      return featureCoordinator.asBinder();
    }

    return null;
  }

  private void onUserUnlocked() {
    logd(TAG, "Starting unlock branch services.");
    startBranchServices(META_UNLOCK_SERVICES);
  }

  private void unregisterReceivers() {
    if (receiversRegistered) {
      receiversRegistered = false;
      unregisterReceiver(userUnlockedReceiver);
      unregisterReceiver(userBackgroundReceiver);
    }
  }

  private final BroadcastReceiver userUnlockedReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          logd(TAG, "User has been unlocked.");
          onUserUnlocked();
        }
      };

  private final BroadcastReceiver userBackgroundReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      logd(TAG, "User has been placed into the background. Stopping foreground user services.");
      unregisterReceivers();
      stopBranchServices();
      stopSelf();
    }
  };
}
