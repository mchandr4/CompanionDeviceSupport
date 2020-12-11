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

package com.google.android.connecteddevice.logging.model;

import android.os.Process;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/** Contains basic info of a log record. */
public final class LogRecord {

  private static final String ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSX";

  /** Priority level constant for logging. */
  public enum Level {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR
  }

  @NonNull private final String time;
  private final int processId;
  private final int threadId;
  @NonNull private final Level level;
  @Nullable private final String backTrace;
  @NonNull private final String tag;
  @NonNull private final String message;

  /**
   * Creates a log record.
   *
   * @param level Log priority level.
   * @param tag Log tag.
   * @param message Log message.
   */
  public LogRecord(@NonNull Level level, @NonNull String tag, @NonNull String message) {
    this(level, tag, message, /* exception= */ null);
  }

  /**
   * Creates a log record with an exception.
   *
   * @param level Log priority level.
   * @param tag Log tag.
   * @param message Log message.
   * @param exception Log exception.
   */
  public LogRecord(
      @NonNull Level level, @NonNull String tag, @NonNull String message, Exception exception) {
    time = currentIsoTime();
    processId = Process.myPid();
    threadId = Process.myTid();
    this.level = level;
    this.tag = tag;
    if (exception != null) {
      StringWriter stringWriter = new StringWriter();
      exception.printStackTrace(new PrintWriter(stringWriter));
      backTrace = stringWriter.toString();
    } else {
      backTrace = null;
    }
    this.message = message;
  }

  @NonNull
  public String getTime() {
    return time;
  }

  /** Returns the process id of this log record. */
  public int getProcessId() {
    return processId;
  }

  /** Returns the thread id of this log record. */
  public int getThreadId() {
    return threadId;
  }

  /** Returns the priority level of this log record. */
  @NonNull
  public Level getLevel() {
    return level;
  }

  /**
   * Returns the back trace of this log record, {@code null} if this log record is not for error
   * with an exception.
   */
  @Nullable
  public String getBackTrace() {
    return backTrace;
  }

  /** Return the tag of this log record. */
  @NonNull
  public String getTag() {
    return tag;
  }

  /** Return the message of this log record. */
  @NonNull
  public String getMessage() {
    return message;
  }

  /** Return serialization of log record in byte array. */
  @NonNull
  public byte[] toByteArray() {
    Gson gson = new Gson();
    return gson.toJson(this).getBytes();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof LogRecord)) {
      return false;
    }
    LogRecord logRecord = (LogRecord) obj;
    return Objects.equals(time, logRecord.time)
        && processId == logRecord.processId
        && threadId == logRecord.threadId
        && Objects.equals(level, logRecord.level)
        && Objects.equals(backTrace, logRecord.backTrace)
        && Objects.equals(tag, logRecord.tag)
        && Objects.equals(message, logRecord.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(time, processId, threadId, level, backTrace, tag, message);
  }

  // Date APIs are only used for log messages and must be Java 7 compatible for external
  // applications.
  @SuppressWarnings("JavaUtilDate")
  private static String currentIsoTime() {
    DateFormat dateFormat = new SimpleDateFormat(ISO_FORMAT, Locale.US);
    return dateFormat.format(new Date());
  }
}
