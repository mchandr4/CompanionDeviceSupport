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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/** Early start service that provides the Calendar Sync feature to the foreground user. */
public final class CalendarSyncService extends Service {
  /**
   * A flag that controls whether the new bi-directional sync implementation is used. The value
   * should only by changed by tests before the service is created.
   */
  @VisibleForTesting static boolean enableBidirectionalSync = false;

  /**
   * The legacy implementation that handles only receiving a full calendar data push.
   *
   * <p>Only one of calendarSyncManager or calendarSyncFeature will be non-null.
   */
  @Nullable private CalendarSyncManager calendarSyncManager;

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
    if (enableBidirectionalSync) {
      calendarSyncFeature = new CalendarSyncFeature2(getApplicationContext());
      calendarSyncFeature.start();
    } else {
      calendarSyncManager = new CalendarSyncManager(getApplicationContext());
    }
  }

  @Override
  public void onDestroy() {
    if (enableBidirectionalSync) {
      calendarSyncFeature.stop();
    } else {
      calendarSyncManager.cleanup();
    }
    super.onDestroy();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    return START_STICKY;
  }
}
