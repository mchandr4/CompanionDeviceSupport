package com.google.android.connecteddevice.model;

import androidx.annotation.NonNull;
import com.google.gson.Gson;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Contains basic info of a log record file. */
public class LogRecordFile {
  @NonNull private final String deviceName;
  @NonNull private final String time;
  @NonNull private final List<LogRecord> logRecords;

  public LogRecordFile(@NonNull String deviceName, @NonNull List<LogRecord> logRecords) {
    this.deviceName = deviceName;
    time = Instant.now().toString();
    this.logRecords = logRecords;
  }

  /** Returns the device name of the log record file. */
  @NonNull
  public String getDeviceName() {
    return deviceName;
  }

  /** Returns the time when this file is generated. */
  @NonNull
  public String getTime() {
    return time;
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

  /** Return serialization of this log record file in byte array. */
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
    if (!(obj instanceof LogRecordFile)) {
      return false;
    }
    LogRecordFile logRecordFile = (LogRecordFile) obj;
    return Objects.equals(deviceName, logRecordFile.deviceName)
        && Objects.equals(time, logRecordFile.time)
        && Objects.equals(logRecords, logRecordFile.logRecords);
  }

  @Override
  public int hashCode() {
    return Objects.hash(deviceName, time, logRecords);
  }
}
