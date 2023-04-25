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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.R;
import com.google.android.connecteddevice.api.external.ISafeBinderVersion;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A service that is used to maintain the lifecycle of branch services. */
public abstract class TrunkService extends MetaDataService {

  private static final String TAG = "TrunkService";

  /** {@code string-array} List of services to start early. */
  @VisibleForTesting
  static final String META_EARLY_SERVICES =
      "com.google.android.connecteddevice.early_services";

  @VisibleForTesting
  static final int MAX_BIND_ATTEMPTS = 3;

  private static final Duration BIND_RETRY_DURATION = Duration.ofSeconds(1);

  private final Multiset<String> bindAttempts = HashMultiset.create();

  @SuppressWarnings("AndroidConcurrentHashMap")
  private final Map<ComponentName, ServiceConnection> startedServices = new ConcurrentHashMap<>();

  protected ISafeBinderVersion binderVersion;

  @Override
  public void onCreate() {
    super.onCreate();
    startBranchServices(META_EARLY_SERVICES);
    binderVersion =
        new ISafeBinderVersion.Stub() {
          @Override
          public int getVersion() {
            return getResources().getInteger(R.integer.hu_companion_binder_version);
          }
        };
  }

  @Override
  public void onDestroy() {
    stopBranchServices();
    super.onDestroy();
  }

  /**
   * Start services defined in meta-data tag.
   *
   * @param metaDataName {@code android:name} in meta-data tag.
   */
  protected final void startBranchServices(@NonNull String metaDataName) {
    String[] services = getMetaStringArray(metaDataName, /* defaultValue= */ new String[0]);
    if (services.length == 0) {
      logw(TAG, "No services were found in " + metaDataName + ".");
      return;
    }
    for (String service : services) {
      ComponentName name = ComponentName.unflattenFromString(service);
      if (name == null) {
        loge(TAG, "Invalid branch service: " + service + ". Unable to start.");
        continue;
      }
      bindToService(name);
    }
  }

  /** Stop all branch services that have been started by this service. */
  protected final void stopBranchServices() {
    logd(TAG, "Stopping connected branch services.");
    for (Map.Entry<ComponentName, ServiceConnection> connection : startedServices.entrySet()) {
      ComponentName name = connection.getKey();
      ServiceConnection serviceConnection = connection.getValue();
      logd(TAG, "Attempting to stop " + name.flattenToString());
      unbindService(serviceConnection);
    }
    startedServices.clear();
  }

  private void bindToService(@NonNull ComponentName componentName) {
    Intent intent = new Intent();
    intent.setComponent(componentName);
    String flatComponentName = componentName.flattenToString();
    boolean success = bindService(intent, createServiceConnection(), Context.BIND_AUTO_CREATE);
    logd(TAG, "Attempted to start " + flatComponentName + " with success: " + success + ".");
    if (success) {
      bindAttempts.remove(flatComponentName);
      return;
    }
    bindAttempts.add(flatComponentName);
    int attempts = bindAttempts.count(flatComponentName);
    if (attempts > MAX_BIND_ATTEMPTS) {
      loge(
          TAG,
          "Failed to bind to " + flatComponentName + " after " + attempts + " attempts. Aborting.");
      return;
    }
    logw(TAG, "Unable to bind to " + flatComponentName + ". Trying again.");
    new Handler(Looper.getMainLooper())
        .postDelayed(() -> bindToService(componentName), BIND_RETRY_DURATION.toMillis());
  }

  private void onBranchServiceStarted(ComponentName name, ServiceConnection connection) {
    logd(TAG, name.flattenToString() + " started successfully.");
    startedServices.put(name, connection);
  }

  private  ServiceConnection createServiceConnection() {
    return new ServiceConnection() {
      @Override
      public void onServiceConnected(ComponentName name, IBinder service) {
        onBranchServiceStarted(name, this);
      }

      @Override
      public void onServiceDisconnected(ComponentName name) {
        loge(TAG, "Lost connection to " + name.flattenToString() + ". Attempting to reconnect.");
        startedServices.remove(name);
        bindAttempts.setCount(name.flattenToString(), 0);
        bindToService(name);
      }

      @Override
      public void onNullBinding(ComponentName name) {
        onBranchServiceStarted(name, this);
      }
    };
  }
}
