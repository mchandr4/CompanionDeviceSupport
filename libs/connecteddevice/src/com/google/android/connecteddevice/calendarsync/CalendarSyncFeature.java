package com.google.android.connecteddevice.calendarsync;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.content.Context;
import android.os.ParcelUuid;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.api.RemoteFeature;
import com.google.android.connecteddevice.calendarsync.proto.Calendar;
import com.google.android.connecteddevice.calendarsync.proto.Calendars;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An implementation of {@link RemoteFeature} that registers the CalendarSync feature and handles
 * the import and deletion of calendar event data sent from trusted mobile devices.
 *
 * <p>The calendar data the feature is handling will be cleared every time an error or disconnect
 * happens, or the feature is stopped. As an additional safety measure calendar data is furthermore
 * cleared every time the feature is started.
 */
final class CalendarSyncFeature extends RemoteFeature {
  private static final String TAG = "CalendarSyncFeature";
  private static final ParcelUuid FEATURE_ID =
      ParcelUuid.fromString("5a1a16fd-1ebd-4dbe-bfa7-37e40de0fd80");

  private final CalendarImporter calendarImporter;
  private final CalendarCleaner calendarCleaner;

  // Holds the UUIDs for the synced calendars.
  private final Set<String> syncedCalendars = new HashSet<>();

  CalendarSyncFeature(@NonNull Context context) {
    this(context, new CalendarImporter(context), new CalendarCleaner(context));
  }

  @VisibleForTesting
  CalendarSyncFeature(
      @NonNull Context context,
      @NonNull CalendarImporter calendarImporter,
      @NonNull CalendarCleaner calendarCleaner) {
    super(context, FEATURE_ID);
    this.calendarImporter = calendarImporter;
    this.calendarCleaner = calendarCleaner;
  }

  @Override
  public void start() {
    // For safety in case something went wrong and the feature couldn't terminate correctly we
    // clear all locally synchronized calendars on start of the feature.
    calendarCleaner.eraseCalendars();
    super.start();
  }

  @Override
  public void stop() {
    // Erase all the locally synchronized calendars, so that no user data stays on the device
    // after the feature is stopped.
    calendarCleaner.eraseCalendars();
    super.stop();
  }

  @Override
  protected void onMessageReceived(ConnectedDevice device, byte[] message) {
    if (!device.hasSecureChannel()) {
      logd(TAG, device + ": skipped message from unsecure channel");
      return;
    }

    logd(TAG, device + ": received message over secure channel");
    try {
      Calendars calendars = Calendars.parseFrom(message,
          ExtensionRegistryLite.getEmptyRegistry());

      maybeEraseSynchronizedCalendars(calendars.getCalendarList());
      calendarImporter.importCalendars(calendars);
      storeSynchronizedCalendars(calendars.getCalendarList());
    } catch (InvalidProtocolBufferException e) {
      loge(TAG, device + ": error parsing calendar events protobuf", e);
    }
  }

  @Override
  protected void onDeviceError(ConnectedDevice device, int error) {
    loge(TAG, device + ": received device error " + error, null);
    calendarCleaner.eraseCalendars();
  }

  @Override
  protected void onDeviceDisconnected(ConnectedDevice device) {
    logw(TAG, device + ": disconnected");
    calendarCleaner.eraseCalendars();
  }

  private void storeSynchronizedCalendars(@NonNull List<Calendar> calendars) {
    syncedCalendars.addAll(
        calendars.stream().map(Calendar::getUuid).collect(Collectors.toSet()));
  }

  private void maybeEraseSynchronizedCalendars(@NonNull List<Calendar> calendars) {
    if (calendars.isEmpty()) {
      calendarCleaner.eraseCalendars();
      syncedCalendars.clear();
      return;
    }
    for (Calendar calendar : calendars) {
      if (!syncedCalendars.contains(calendar.getUuid())) {
        continue;
      }
      logw(TAG, String.format("remove calendar: %s", calendar.getUuid()));
      int calId = calendarImporter.findCalendar(calendar.getUuid());
      if (calId == CalendarImporter.INVALID_CALENDAR_ID) {
        loge(TAG, "Cannot find calendar to erase: " + calendar.getUuid(), null);
        continue;
      }
      calendarCleaner.eraseCalendar(String.valueOf(calId));
      syncedCalendars.remove(calendar.getUuid());
    }
  }
}
