// This file is a copy of auto generated file to make this available externally, details in
// b/284378588.

package com.google.android.connecteddevice.metrics;

import android.util.StatsEvent;
import android.util.StatsLog;

/** Utility class for logging statistics events. */
public final class CompanionStatsLog {
  // Constants for atom codes.

  /**
   * CompanionStatusChanged companion_status_changed<br>
   * Usage: StatsLog.write(StatsLog.COMPANION_STATUS_CHANGED, int uid, int companion_status, long
   * time_used_millis);<br>
   */
  public static final int COMPANION_STATUS_CHANGED = 210401;

  // Constants for enum values.

  // Values for CompanionStatusChanged.companion_status
  public static final int COMPANION_STATUS_CHANGED__COMPANION_STATUS__COMPANION_STATUS_UNSPECIFIED =
      0;
  public static final int COMPANION_STATUS_CHANGED__COMPANION_STATUS__ASSOCIATION_STARTED = 1;
  public static final int COMPANION_STATUS_CHANGED__COMPANION_STATUS__DISCOVERY_STARTED = 2;
  public static final int COMPANION_STATUS_CHANGED__COMPANION_STATUS__CONNECTED = 3;
  public static final int COMPANION_STATUS_CHANGED__COMPANION_STATUS__SECURE_CHANNEL_ESTABLISHED =
      4;
  public static final int COMPANION_STATUS_CHANGED__COMPANION_STATUS__DISCONNECTED = 5;

  // Annotation constants.
  public static final byte ANNOTATION_ID_IS_UID = StatsLog.ANNOTATION_ID_IS_UID;
  public static final byte ANNOTATION_ID_TRUNCATE_TIMESTAMP =
      StatsLog.ANNOTATION_ID_TRUNCATE_TIMESTAMP;
  public static final byte ANNOTATION_ID_PRIMARY_FIELD = StatsLog.ANNOTATION_ID_PRIMARY_FIELD;
  public static final byte ANNOTATION_ID_EXCLUSIVE_STATE = StatsLog.ANNOTATION_ID_EXCLUSIVE_STATE;
  public static final byte ANNOTATION_ID_PRIMARY_FIELD_FIRST_UID =
      StatsLog.ANNOTATION_ID_PRIMARY_FIELD_FIRST_UID;
  public static final byte ANNOTATION_ID_DEFAULT_STATE = StatsLog.ANNOTATION_ID_DEFAULT_STATE;
  public static final byte ANNOTATION_ID_TRIGGER_STATE_RESET =
      StatsLog.ANNOTATION_ID_TRIGGER_STATE_RESET;
  public static final byte ANNOTATION_ID_STATE_NESTED = StatsLog.ANNOTATION_ID_STATE_NESTED;

  // Write methods
  public static void write(int code, int arg1, int arg2, long arg3) {
    final StatsEvent.Builder builder = StatsEvent.newBuilder();
    builder.setAtomId(code);
    builder.writeInt(arg1);
    builder.writeInt(arg2);
    builder.writeLong(arg3);

    builder.usePooledBuffer();
    StatsLog.write(builder.build());
  }
}
