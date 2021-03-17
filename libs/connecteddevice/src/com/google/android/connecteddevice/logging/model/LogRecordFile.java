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

import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.annotation.NonNull;
import com.google.android.connecteddevice.logging.util.LoggingUtils;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Contains basic info of a log record file. */
public class LogRecordFile {
  @NonNull private final String deviceName;
  @NonNull private final List<LogRecord> logRecords;

  public LogRecordFile(@NonNull String deviceName, @NonNull List<LogRecord> logRecords) {
    this.deviceName = deviceName;
    this.logRecords = logRecords;
  }

  /** Returns the device name of the log record file. */
  @NonNull
  public String getDeviceName() {
    return deviceName;
  }

  /** Returns log records in this file. */
  @NonNull
  public List<LogRecord> getLogRecords() {
    return logRecords;
  }

  /** Appends log records to this log records file. */
  public void appendLogRecords(@NonNull Collection<LogRecord> logRecords) {
    this.logRecords.addAll(logRecords);
  }

  /** Appends a log record to this log records file. */
  public void appendLogRecord(@NonNull LogRecord logRecord) {
    logRecords.add(logRecord);
  }

  /** Return json serialization of this log record file in byte array. */
  @NonNull
  public byte[] toJsonByteArray() {
    return LoggingUtils.objectToBytes(this);
  }

  /** Return serialization of this log record file in byte array. */
  @NonNull
  public byte[] toByteArray() {
    StringBuilder logRecordFileStringBuilder = new StringBuilder(deviceName);
    for (LogRecord logRecord : logRecords) {
      logRecordFileStringBuilder.append("\n").append(logRecord);
    }
    return logRecordFileStringBuilder.toString().getBytes(UTF_8);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof LogRecordFile)) {
      return false;
    }
    LogRecordFile logRecordFile = (LogRecordFile) obj;
    return Objects.equals(deviceName, logRecordFile.deviceName)
        && Objects.equals(logRecords, logRecordFile.logRecords);
  }

  @Override
  public int hashCode() {
    return Objects.hash(deviceName, logRecords);
  }
}
