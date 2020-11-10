package com.google.android.connecteddevice.calendarsync;

import android.content.Context;
import androidx.annotation.NonNull;

/** A manager class handling the {@link CalendarSyncFeature}. */
final class CalendarSyncManager {
  private final CalendarSyncFeature feature;

  CalendarSyncManager(@NonNull Context context) {
    feature = new CalendarSyncFeature(context);
    feature.start();
  }

  public void cleanup() {
    feature.stop();
  }
}
