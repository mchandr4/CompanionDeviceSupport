package com.google.android.connecteddevice.trust;

import static com.google.android.connecteddevice.util.SafeLog.logd;

import android.app.Service;
import android.content.Intent;
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
    trustedDeviceManager = new TrustedDeviceManager(this);
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
}
