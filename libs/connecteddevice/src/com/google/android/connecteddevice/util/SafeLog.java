package com.google.android.connecteddevice.util;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Convenience logging methods that respect allow list tags. */
public class SafeLog {

  private SafeLog() {}

  /** Log message if tag is allow listed for {@code Log.VERBOSE}. */
  public static void logv(@NonNull String tag, @NonNull String message) {
    if (Log.isLoggable(tag, Log.VERBOSE)) {
      Log.v(tag, message);
    }
    Logger.getLogger().verbose(tag, message);
  }

  /** Log message if tag is allow listed for {@code Log.INFO}. */
  public static void logi(@NonNull String tag, @NonNull String message) {
    if (Log.isLoggable(tag, Log.INFO)) {
      Log.i(tag, message);
    }
    Logger.getLogger().info(tag, message);
  }

  /** Log message if tag is allow listed for {@code Log.DEBUG}. */
  public static void logd(@NonNull String tag, @NonNull String message) {
    if (Log.isLoggable(tag, Log.DEBUG)) {
      Log.d(tag, message);
    }
    Logger.getLogger().debug(tag, message);
  }

  /** Log message if tag is allow listed for {@code Log.WARN}. */
  public static void logw(@NonNull String tag, @NonNull String message) {
    if (Log.isLoggable(tag, Log.WARN)) {
      Log.w(tag, message);
    }
    Logger.getLogger().warn(tag, message);
  }

  /** Log message if tag is allow listed for {@code Log.ERROR}. */
  public static void loge(@NonNull String tag, @NonNull String message) {
    loge(tag, message, /* exception= */ null);
    Logger.getLogger().error(tag, message);
  }

  /** Log message and optional exception if tag is allow listed for {@code Log.ERROR}. */
  public static void loge(
      @NonNull String tag, @NonNull String message, @Nullable Exception exception) {
    if (Log.isLoggable(tag, Log.ERROR)) {
      Log.e(tag, message, exception);
    }
    Logger.getLogger().error(tag, message, exception);
  }
}
