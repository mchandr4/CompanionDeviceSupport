package com.google.android.connecteddevice.calendarsync;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logi;
import static com.google.android.connecteddevice.util.SafeLog.logw;
import static java.lang.String.format;

import android.content.Context;
import android.os.ParcelUuid;
import com.google.android.connecteddevice.api.RemoteFeature;
import com.google.android.connecteddevice.model.ConnectedDevice;

/** A {@link RemoteFeature} that handles synchronizing calendar data with another device. */
final class CalendarSyncFeature2 extends RemoteFeature {

  private static final String TAG = "CalendarSyncFeature2";
  private static final ParcelUuid FEATURE_ID =
      ParcelUuid.fromString("5a1a16fd-1ebd-4dbe-bfa7-37e40de0fd80");

  CalendarSyncFeature2(Context context) {
    super(context, FEATURE_ID);
  }

  @Override
  public void start() {
    logd(TAG, "Starting");
    super.start();
  }

  @Override
  public void stop() {
    logd(TAG, "Stopping");
    super.stop();
  }

  @Override
  protected void onMessageReceived(ConnectedDevice device, byte[] message) {
    if (!device.hasSecureChannel()) {
      logw(TAG, "Ignoring insecure message from " + device.getDeviceName());
      return;
    }
    logi(TAG, "Received message from " + device.getDeviceName());
  }

  @Override
  protected void onDeviceError(ConnectedDevice device, int error) {
    loge(TAG, format("Received error %s from device %s", error, device.getDeviceName()));
  }

  @Override
  protected void onDeviceDisconnected(ConnectedDevice device) {
    logi(TAG, format("Device %s disconnected", device.getDeviceName()));
  }
}
