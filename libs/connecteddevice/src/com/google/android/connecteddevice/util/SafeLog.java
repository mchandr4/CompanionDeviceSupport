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

package com.google.android.connecteddevice.util;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Convenience logging methods that respect allow list tags. */
public class SafeLog {

  private static final boolean LOCAL_MODE = true;

  private SafeLog() {}

  /** Log message if tag is allow listed for {@code Log.VERBOSE}. */
  public static void logv(@NonNull String tag, @NonNull String message) {
    if (LOCAL_MODE || Log.isLoggable(tag, Log.VERBOSE)) {
      Log.v(tag, message);
    }
    Logger.getLogger().verbose(tag, message);
  }

  /** Log message if tag is allow listed for {@code Log.INFO}. */
  public static void logi(@NonNull String tag, @NonNull String message) {
    if (LOCAL_MODE || Log.isLoggable(tag, Log.INFO)) {
      Log.i(tag, message);
    }
    Logger.getLogger().info(tag, message);
  }

  /** Log message if tag is allow listed for {@code Log.DEBUG}. */
  public static void logd(@NonNull String tag, @NonNull String message) {
    if (LOCAL_MODE || Log.isLoggable(tag, Log.DEBUG)) {
      Log.d(tag, message);
    }
    Logger.getLogger().debug(tag, message);
  }

  /** Log message if tag is allow listed for {@code Log.WARN}. */
  public static void logw(@NonNull String tag, @NonNull String message) {
    if (LOCAL_MODE || Log.isLoggable(tag, Log.WARN)) {
      Log.w(tag, message);
    }
    Logger.getLogger().warn(tag, message);
  }

  /** Log message if tag is allow listed for {@code Log.ERROR}. */
  public static void loge(@NonNull String tag, @NonNull String message) {
    loge(tag, message, /* exception= */ null);
  }

  /** Log message and optional exception if tag is allow listed for {@code Log.ERROR}. */
  public static void loge(
      @NonNull String tag, @NonNull String message, @Nullable Exception exception) {
    if (LOCAL_MODE || Log.isLoggable(tag, Log.ERROR)) {
      Log.e(tag, message, exception);
    }
    Logger.getLogger().error(tag, message, exception);
  }
}
