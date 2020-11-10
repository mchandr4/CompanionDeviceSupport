package com.google.android.connecteddevice.service;

import static com.google.android.connecteddevice.util.SafeLog.logd;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import androidx.annotation.Nullable;

/** Service responsible for starting services that must be run in the active foreground user. */
public final class ConnectedDeviceFgUserService extends TrunkService {

  private static final String TAG = "ConnectedDeviceFgUserService";

  /** {@code string-array} List of services to start after the user has unlocked. */
  private static final String META_UNLOCK_SERVICES =
      "com.google.android.connecteddevice.unlock_services";

  @Override
  public void onCreate() {
    super.onCreate();
    logd(TAG, "Starting service.");
    registerReceiver(userPresentReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
  }

  @Override
  public void onDestroy() {
    unregisterReceiver(userPresentReceiver);
    super.onDestroy();
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private final BroadcastReceiver userPresentReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          logd(TAG, "User has unlocked. Starting unlock branch services.");
          startBranchServices(META_UNLOCK_SERVICES);
        }
      };
}
