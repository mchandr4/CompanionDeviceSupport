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
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import java.time.Duration;

/** A service that is used to maintain the lifecycle of branch services. */
public abstract class TrunkService extends MetaDataService {

  private static final String TAG = "TrunkService";

  /** {@code string-array} List of services to start early. */
  private static final String META_EARLY_SERVICES =
      "com.google.android.connecteddevice.early_services";

  private static final Duration BIND_RETRY_DURATION = Duration.ofSeconds(1);

  private static final int MAX_BIND_ATTEMPTS = 3;

  private final Multiset<String> bindAttempts = HashMultiset.create();

  @Override
  public void onCreate() {
    super.onCreate();
    startBranchServices(META_EARLY_SERVICES);
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

  private void bindToService(@NonNull ComponentName componentName) {
    Intent intent = new Intent();
    intent.setComponent(componentName);
    String flatComponentName = componentName.flattenToString();
    logd(TAG, "Attempting to start " + flatComponentName + ".");
    boolean success = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    if (success) {
      bindAttempts.remove(flatComponentName);
      return;
    }
    bindAttempts.add(flatComponentName);
    int attempts = bindAttempts.count(flatComponentName);
    if (attempts > MAX_BIND_ATTEMPTS) {
      loge(
          TAG,
          "Failed to bind to " + flatComponentName + "after " + attempts + " attempts. Aborting.");
      return;
    }
    logw(TAG, "Unable to bind to " + flatComponentName + ". Trying again.");
    new Handler(Looper.getMainLooper())
        .postDelayed(() -> bindToService(componentName), BIND_RETRY_DURATION.toMillis());
  }

  private final ServiceConnection serviceConnection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
          logd(TAG, name.flattenToString() + " started successfully.");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
          loge(TAG, "Lost connection to " + name.flattenToString() + ". Attempting to reconnect.");
          bindAttempts.setCount(name.flattenToString(), 0);
          bindToService(name);
        }
      };
}
