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
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.UserManager;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.api.IConnectedDeviceManager;
import com.google.android.connecteddevice.api.IConnectionCallback;
import com.google.android.connecteddevice.api.IDeviceAssociationCallback;
import com.google.android.connecteddevice.api.IDeviceCallback;
import com.google.android.connecteddevice.api.IFeatureCoordinator;
import com.google.android.connecteddevice.api.IOnLogRequestedListener;
import com.google.android.connecteddevice.api.RemoteFeature;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.DeviceMessage;
import java.util.List;

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

  private static final String FULLY_QUALIFIED_SERVICE_NAME =
      "com.google.android.connecteddevice.service.ConnectedDeviceService";

  /**
   * {@code boolean} Use the {@link IFeatureCoordinator} instead of {@link IConnectedDeviceManager}
   */
  private static final String META_ENABLE_FEATURE_COORDINATOR =
      "com.google.android.connecteddevice.enable_feature_coordinator";

  private static final long BIND_RETRY_DURATION_MS = 1000;

  private static final int MAX_BIND_ATTEMPTS = 3;

  private boolean receiversRegistered = true;

  private int bindAttempts;

  private IBinder connectedDeviceServiceBinder;

  private IFeatureCoordinator featureCoordinator;

  private IConnectedDeviceManager.Stub featureCoordinatorWrapper;

  private boolean useFeatureCoordinator;

  @Override
  public void onCreate() {
    super.onCreate();
    logd(TAG, "Starting service.");
    useFeatureCoordinator = getMetaBoolean(META_ENABLE_FEATURE_COORDINATOR, false);
    // Listen to the unlock event to know when a new user has come to the foreground and storage is
    // unlocked.
    registerReceiver(userUnlockedReceiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
    // Listen for user going to the background so we can clean up.
    registerReceiver(userBackgroundReceiver, new IntentFilter(Intent.ACTION_USER_BACKGROUND));
    bindToService();
    UserManager userManager = getSystemService(UserManager.class);
    if (userManager.isUserUnlocked()) {
      logd(TAG, "User was already unlocked on service start.");
      onUserUnlocked();
    }
  }

  @Override
  public void onDestroy() {
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
    if (action.equals(RemoteFeature.ACTION_BIND_REMOTE_FEATURE_FG)) {
      if (useFeatureCoordinator) {
        return featureCoordinatorWrapper;
      } else {
        return connectedDeviceServiceBinder;
      }
    } else if (action.equals(RemoteFeature.ACTION_BIND_FEATURE_COORDINATOR_FG)
        && useFeatureCoordinator) {
      return connectedDeviceServiceBinder;
    }

    return null;
  }

  private void bindToService() {
    String packageName = getApplicationContext().getPackageName();
    Intent intent = new Intent();
    intent.setComponent(new ComponentName(packageName, FULLY_QUALIFIED_SERVICE_NAME));
    if (useFeatureCoordinator) {
      intent.setAction(RemoteFeature.ACTION_BIND_FEATURE_COORDINATOR);
    } else {
      intent.setAction(RemoteFeature.ACTION_BIND_REMOTE_FEATURE);
    }
    boolean success = bindService(intent, serviceConnection, /* flags= */ 0);
    if (success) {
      return;
    }
    bindAttempts++;
    if (bindAttempts > MAX_BIND_ATTEMPTS) {
      loge(
          TAG,
          "Failed to bind to ConnectedDeviceService after "
              + bindAttempts
              + " attempts. Aborting.");
      return;
    }
    logw(TAG, "Unable to bind to ConnectedDeviceService. Trying again.");
    new Handler(Looper.getMainLooper()).postDelayed(this::bindToService, BIND_RETRY_DURATION_MS);
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

  private IConnectedDeviceManager.Stub createFeatureCoordinatorWrapper() {
    return new IConnectedDeviceManager.Stub() {
      @Override
      public List<ConnectedDevice> getActiveUserConnectedDevices() throws RemoteException {
        return featureCoordinator.getConnectedDevicesForDriver();
      }

      @Override
      public void registerActiveUserConnectionCallback(IConnectionCallback callback)
          throws RemoteException {
        featureCoordinator.registerDriverConnectionCallback(callback);
      }

      @Override
      public void unregisterConnectionCallback(IConnectionCallback callback)
          throws RemoteException {
        featureCoordinator.unregisterConnectionCallback(callback);
      }

      @Override
      public void registerDeviceCallback(ConnectedDevice connectedDevice, ParcelUuid recipientId,
          IDeviceCallback callback) throws RemoteException {
        featureCoordinator.registerDeviceCallback(connectedDevice, recipientId, callback);
      }

      @Override
      public void unregisterDeviceCallback(ConnectedDevice connectedDevice, ParcelUuid recipientId,
          IDeviceCallback callback) throws RemoteException {
        featureCoordinator.unregisterDeviceCallback(connectedDevice, recipientId, callback);
      }

      @Override
      public boolean sendMessage(ConnectedDevice connectedDevice, DeviceMessage message)
          throws RemoteException {
        return featureCoordinator.sendMessage(connectedDevice, message);
      }

      @Override
      public void registerDeviceAssociationCallback(IDeviceAssociationCallback callback)
          throws RemoteException {
        featureCoordinator.registerDeviceAssociationCallback(callback);
      }

      @Override
      public void unregisterDeviceAssociationCallback(IDeviceAssociationCallback callback)
          throws RemoteException {
        featureCoordinator.unregisterDeviceAssociationCallback(callback);
      }

      @Override
      public void registerOnLogRequestedListener(int loggerId, IOnLogRequestedListener listener)
          throws RemoteException {
        featureCoordinator.registerOnLogRequestedListener(loggerId, listener);
      }

      @Override
      public void unregisterOnLogRequestedListener(int loggerId, IOnLogRequestedListener listener)
          throws RemoteException {
        featureCoordinator.unregisterOnLogRequestedListener(loggerId, listener);
      }

      @Override
      public void processLogRecords(int loggerId, byte[] logRecords) throws RemoteException {
        featureCoordinator.processLogRecords(loggerId, logRecords);
      }
    };
  }

  private final ServiceConnection serviceConnection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
          logd(TAG, "Successfully bound to ConnectedDeviceService.");
          if (useFeatureCoordinator) {
           featureCoordinator = IFeatureCoordinator.Stub.asInterface(service);
           featureCoordinatorWrapper = createFeatureCoordinatorWrapper();
          } else {
            connectedDeviceServiceBinder = service;
          }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
          logd(TAG, "Disconnected from ConnectedDeviceService.");
        }
      };

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
