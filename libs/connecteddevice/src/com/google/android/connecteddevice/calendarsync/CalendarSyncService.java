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

package com.google.android.connecteddevice.calendarsync;

import static com.google.android.connecteddevice.util.SafeLog.logi;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.StrictMode;
import android.util.Log;
import androidx.annotation.Nullable;

/** Early start service that provides the Calendar Sync feature to the foreground user. */
public final class CalendarSyncService extends Service {

  private static final String TAG = "CalendarSyncService";

  /**
   * The development implementation that handles sending and receiving calendar changes.
   *
   * <p>Only one of calendarSyncManager or calendarSyncFeature will be non-null.
   */
  @Nullable private CalendarSyncFeature2 calendarSyncFeature;

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    // Cannot bind to this service.
    return null;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    logi(TAG, "Creating CalendarSyncFeature2");
    calendarSyncFeature = new CalendarSyncFeature2(getApplicationContext());
    calendarSyncFeature.start();

    // TODO This setting is global and should be enabled in Application.onCreate().
    // Enable StrictMode globally when debug logging is enabled.
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      // Settings for the current (main) thread.
      StrictMode.setThreadPolicy(
          new StrictMode.ThreadPolicy.Builder().detectAll().penaltyDialog().build());

      // Settings for the entire application process. StrictMode.VmPolicy.Builder().penaltyDeath()
      // will block general companion testing.
      StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build());
    }
  }

  @Override
  public void onDestroy() {
    calendarSyncFeature.stop();
    super.onDestroy();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    return START_STICKY;
  }
}
