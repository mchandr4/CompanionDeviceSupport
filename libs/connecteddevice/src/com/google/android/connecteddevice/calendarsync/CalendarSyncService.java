package com.google.android.connecteddevice.calendarsync;

import static com.google.android.connecteddevice.util.SafeLog.logd;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

/** Early start service that provides the Calendar Sync feature to the foreground user. */
public final class CalendarSyncService extends Service {
  private static final String TAG = "CalendarSyncService";

  class LocalBinder extends Binder {
    CalendarSyncService getService() {
      return CalendarSyncService.this;
    }
  }

  private final IBinder binder = new LocalBinder();

  private CalendarSyncManager calendarSyncManager;

  @Override
  public IBinder onBind(Intent intent) {
    logd(TAG, "onBind()");
    return binder;
  }

  @Override
  public void onCreate() {
    logd(TAG, "onCreate()");
    super.onCreate();
    calendarSyncManager = new CalendarSyncManager(this);
  }

  @Override
  public void onDestroy() {
    logd(TAG, "onDestroy()");
    calendarSyncManager.cleanup();
    super.onDestroy();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    return START_STICKY;
  }
}
