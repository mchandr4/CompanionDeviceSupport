package com.google.android.connecteddevice.calendarsync;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logi;
import static com.google.android.connecteddevice.util.SafeLog.logw;
import static java.lang.String.format;

import android.content.Context;
import android.os.ParcelUuid;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.api.RemoteFeature;
import com.google.android.connecteddevice.calendarsync.android.CalendarSyncAccess;
import com.google.android.connecteddevice.calendarsync.common.Logger;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.util.SafeLog;

/** A {@link RemoteFeature} that handles synchronizing calendar data with another device. */
final class CalendarSyncFeature2 extends RemoteFeature {

  private static final String TAG = "CalendarSyncFeature2";
  private static final ParcelUuid FEATURE_ID =
      ParcelUuid.fromString("5a1a16fd-1ebd-4dbe-bfa7-37e40de0fd80");
  private final CalendarSyncAccess calendarSyncAccess;

  /**
   * Creates a {@link CalendarSyncFeature2} and creates all dependencies. This takes the role of
   * injecting dependencies to the constructor below.
   */
  CalendarSyncFeature2(Context context) {
    this(context, new CalendarSyncAccess(new ConnectedDeviceLoggerFactory()));
  }

  /**
   * Creates a {@link CalendarSyncFeature2} and accepts all dependencies. This allows tests to
   * replace dependencies with mocks.
   */
  @VisibleForTesting
  CalendarSyncFeature2(Context context, CalendarSyncAccess calendarSyncAccess) {
    super(context, FEATURE_ID);
    this.calendarSyncAccess = calendarSyncAccess;
  }

  @Override
  public void start() {
    super.start();
    logd(TAG, "Starting");
    calendarSyncAccess.start();
  }

  @Override
  public void stop() {
    super.stop();
    logd(TAG, "Stopping");
    calendarSyncAccess.stop();
  }

  @Override
  protected void onMessageReceived(ConnectedDevice device, byte[] message) {
    if (!device.hasSecureChannel()) {
      logw(TAG, "Ignoring insecure message from " + device.getDeviceName());
      return;
    }
    calendarSyncAccess.access(sync -> sync.receive(device.getDeviceId(), message));
  }

  @Override
  protected void onDeviceError(ConnectedDevice device, int error) {
    loge(TAG, format("Received error %s from device %s", error, device.getDeviceName()));
  }

  @Override
  protected void onDeviceDisconnected(ConnectedDevice device) {
    calendarSyncAccess.access((sync) -> sync.clear(device.getDeviceId()));
  }

  /** A factory for {@link Logger}s that uses the connecteddevice {@link SafeLog}. */
  private static class ConnectedDeviceLoggerFactory implements Logger.Factory {
    @Override
    public Logger create(String tag) {
      return new Logger() {
        @Override
        public void debug(String message) {
          logd(tag, message);
        }

        @Override
        public void info(String message) {
          logi(tag, message);
        }

        @Override
        public void warn(String message) {
          logw(tag, message);
        }

        @Override
        public void error(String message) {
          loge(tag, message);
        }

        @Override
        public void error(String message, Exception e) {
          loge(tag, message, e);
        }
      };
    }
  }
}
