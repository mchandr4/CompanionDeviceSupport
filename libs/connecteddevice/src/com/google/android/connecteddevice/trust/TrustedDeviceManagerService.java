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

package com.google.android.connecteddevice.trust;

import static com.google.android.connecteddevice.util.SafeLog.logd;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/** Service that provides an instance of {@link TrustedDeviceManager}. */
public class TrustedDeviceManagerService extends Service {

  private static final String TAG = "TrustedDeviceManagerService";

  private TrustedDeviceManager trustedDeviceManager;

  @Override
  public void onCreate() {
    super.onCreate();
    logd(TAG, "Starting trusted device manager service.");
    TrustedDeviceEventLog.onTrustedDeviceServiceStarted();
    trustedDeviceManager = createTrustedDeviceManager();
  }

  @Override
  public void onDestroy() {
    trustedDeviceManager.cleanup();
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return trustedDeviceManager.asBinder();
  }

  private TrustedDeviceManager createTrustedDeviceManager() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      return new TrustedDeviceManagerApi33(this);
    }
    return new TrustedDeviceManager(this);
  }
}
