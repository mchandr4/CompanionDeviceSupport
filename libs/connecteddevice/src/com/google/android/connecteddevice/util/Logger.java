package com.google.android.connecteddevice.util;

import android.os.Process;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.model.LogRecord;
import com.google.android.connecteddevice.model.LogRecord.Level;
import com.google.common.collect.EvictingQueue;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

/** Singleton class that saves internal log records. */
public class Logger {
  private static final int MAX_LOG_SIZE = 500;

  private static final AtomicReference<Logger> loggerReference = new AtomicReference<>(null);
  private final Queue<LogRecord> logRecordQueue = EvictingQueue.create(MAX_LOG_SIZE);

  private Logger() {}

  /** Get the {@link Logger} instance. */
  @NonNull
  public static Logger getLogger() {
    loggerReference.compareAndSet(null, new Logger());
    return loggerReference.get();
  }

  /** Get the id of the {@link Logger}. */
  public int getLoggerId() {
    return Process.myPid();
  }

  /** Save log record message with log priority {@link Level#VERBOSE}. */
  public void verbose(@NonNull String tag, @NonNull String message) {
    addLogRecord(new LogRecord(Level.VERBOSE, tag, message));
  }

  /** Save log record message with log priority {@link Level#DEBUG}. */
  public void debug(@NonNull String tag, @NonNull String message) {
    addLogRecord(new LogRecord(Level.DEBUG, tag, message));
  }

  /** Save log record message with log priority {@link Level#INFO} */
  public void info(@NonNull String tag, @NonNull String message) {
    addLogRecord(new LogRecord(Level.INFO, tag, message));
  }

  /** Save log record message with log priority {@link Level#WARN} */
  public void warn(@NonNull String tag, @NonNull String message) {
    addLogRecord(new LogRecord(Level.WARN, tag, message));
  }

  /** Save log record message with log priority {@link Level#ERROR} */
  public void error(@NonNull String tag, @NonNull String message) {
    addLogRecord(new LogRecord(Level.ERROR, tag, message));
  }

  /** Save log record message and exception with log priority {@link Level#ERROR} */
  public void error(@NonNull String tag, @NonNull String message, @Nullable Exception exception) {
    addLogRecord(new LogRecord(Level.ERROR, tag, message, exception));
  }

  /** Get log records of this Logger in JSON format. */
  @NonNull
  public byte[] toByteArray() {
    List<LogRecord> currentRecords = new ArrayList<>(logRecordQueue);
    Gson gson = new Gson();
    return gson.toJson(currentRecords).getBytes();
  }

  private void addLogRecord(@NonNull LogRecord logRecord) {
    logRecordQueue.add(logRecord);
  }
}
