package com.google.android.connecteddevice.logging;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.util.FileHelper;
import com.google.android.connecteddevice.util.Logger;
import com.google.android.connecteddevice.util.ThreadSafeCallbacks;
import java.io.File;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manager of internal logging.
 *
 * <p>If a process has logs that need to be collected, it has to implement and register an {@link
 * OnLogRequestedListener} and {@link LoggingManager#prepareLocalLogRecords(int, byte[])} has to be
 * called in the implementation.
 */
public class LoggingManager {
  private static final String TAG = "LoggingManager";
  private static final String FILE_NAME_SEPARATOR = "-";
  private static final String LOG_FILE_EXTENSION = ".aalog";
  private static final String FILE_NAME_TIME_FORMAT = "yyyyMMddHHmmss";
  private static final String UNKNOWN_DEVICE_NAME = "UNKNOWN";
  private static final int LOG_FILES_MAX_NUM = 10;

  private final ThreadSafeCallbacks<OnLogRequestedListener> logRequestedListeners =
      new ThreadSafeCallbacks<>();
  private final ThreadSafeCallbacks<LoggingEventCallback> loggingEventCallbacks =
      new ThreadSafeCallbacks<>();
  private final Map<Integer, ArrayDeque<OnLogRequestedListener>> registeredLoggers =
      new ConcurrentHashMap<>();
  private final Map<OnLogRequestedListener, Executor> backupLogRequestedListeners =
      new ConcurrentHashMap<>();
  private final ThreadSafeCallbacks<LogFileCallback> logFileCallbacks = new ThreadSafeCallbacks<>();

  private final Context context;

  @VisibleForTesting
  final AtomicBoolean isCollectingLogRecordsToGenerateLogFile = new AtomicBoolean(false);

  @VisibleForTesting
  final AtomicBoolean isCollectingLogRecordsToSendLocalLog = new AtomicBoolean(false);
  // logger id -> log records of the logger
  private final Map<Integer, byte[]> loggerLogRecords = new ConcurrentHashMap<>();
  private FileHelper fileHelper = new FileHelper();

  public LoggingManager(@NonNull Context context) {
    this.context = context;
  }

  /** Loads generated log files. */
  public List<File> loadLogFiles() {
    List<File> logFiles = new ArrayList<>();
    for (File file : fileHelper.readFiles(getFilesDir())) {
      if (file.isFile()) {
        logFiles.add(file);
      }
    }
    logFiles.sort(Comparator.comparingLong(File::lastModified));
    return logFiles;
  }

  /** Generates a car log file. */
  public void generateLogFile() {
    if (isCollectingLogRecords()) {
      logd(TAG, "Collecting log records in progress, ignoring.");
      return;
    }
    resetSession();
    isCollectingLogRecordsToGenerateLogFile.set(true);
    logRequestedListeners.invoke(OnLogRequestedListener::onLogRecordsRequested);
  }

  /** Sends request for log file to connected devices. */
  public void sendLogRequest() {
    loggingEventCallbacks.invoke(LoggingEventCallback::onRemoteLogRequested);
  }

  /** Retrieves log records from registered {@link Logger}s to send to a remote device. */
  public void startSendingLogRecords() {
    if (isCollectingLogRecords()) {
      logd(TAG, "Collecting log records in progress, ignoring.");
      return;
    }
    resetSession();
    isCollectingLogRecordsToSendLocalLog.set(true);
    logRequestedListeners.invoke(OnLogRequestedListener::onLogRecordsRequested);
  }

  /**
   * Process car log records in the {@link Logger} with given identifier. This should be called when
   * the log records of a {@link Logger} need to be output.
   *
   * @param loggerId The id of the {@link Logger}.
   * @param logRecordsInLogger The car logs in the {@link Logger}.
   */
  public void prepareLocalLogRecords(int loggerId, @NonNull byte[] logRecordsInLogger) {
    if (!isCollectingLogRecords()) {
      logw(TAG, "Aborted preparing local log records, not collecting log records.");
      return;
    }
    loggerLogRecords.put(loggerId, logRecordsInLogger);
    int processedLoggerNum = loggerLogRecords.size();
    int totalLoggerNum = logRequestedListeners.size();
    String progressLog =
        String.format(
            "[%d/%d]: Processed log records for logger %d.",
            processedLoggerNum, totalLoggerNum, loggerId);
    if (processedLoggerNum < totalLoggerNum) {
      logd(TAG, progressLog + " Waiting for more log records.");
      return;
    }
    logd(
        TAG,
        progressLog
            + " Log records in all registered loggers have been processed, continue processing.");
    List<byte[]> currentLoggerLogRecords = new ArrayList<>(loggerLogRecords.values());
    byte[] localLog = fileHelper.mergeLogsIntoLogRecordFile(currentLoggerLogRecords).toByteArray();

    // Write the logs into a file if the logs are collected to generate a log file.
    if (isCollectingLogRecordsToGenerateLogFile.get()) {
      if (!writeLogFile(getLogFileNameForDevice(Build.MODEL), localLog)) {
        logFileCallbacks.invoke(LogFileCallback::onLogFileError);
        resetSession();
        return;
      }
    }
    // Notify the callbacks that car logs are ready if the logs are collected to send to a
    // connected device.
    if (isCollectingLogRecordsToSendLocalLog.get()) {
      loggingEventCallbacks.invoke(callback -> callback.onLocalLogAvailable(localLog));
    }
    resetSession();
  }

  /**
   * Process the log records from a connected device.
   *
   * @param device The connected device.
   * @param logRecords The log records.
   */
  public void processRemoteLogRecords(@NonNull ConnectedDevice device, @NonNull byte[] logRecords) {
    String deviceName = device.getDeviceName();
    if (deviceName == null) {
      deviceName = UNKNOWN_DEVICE_NAME;
    }
    writeLogFile(deviceName, logRecords);
  }

  /**
   * Add a listener for the {@link Logger} with the given id. If a listener has already been added
   * for the logger with given id, subsequent listeners will be saved as backups.
   *
   * @param loggerId The id of the {@link Logger}.
   * @param listener The listener to register.
   * @param executor The executor that the callback will be executed on.
   */
  public void addOnLogRequestedListener(
      int loggerId, @NonNull OnLogRequestedListener listener, @NonNull Executor executor) {
    logd(TAG, "registerOnLogRequestedListener called for logger: " + loggerId);
    ArrayDeque<OnLogRequestedListener> listeners = registeredLoggers.get(loggerId);
    if (listeners == null) {
      listeners = new ArrayDeque<>();
    }
    if (listeners.contains(listener)) {
      logd(TAG, "Listener has already been registered. Ignoring.");
      return;
    }
    if (listeners.isEmpty()) {
      logRequestedListeners.add(listener, executor);
      logd(TAG, "Registered listener " + listener + " for Logger " + loggerId + ".");
    } else {
      logd(TAG, "Registered backup listener" + listener + " for Logger " + loggerId + ".");
      backupLogRequestedListeners.put(listener, executor);
    }
    listeners.add(listener);
    registeredLoggers.put(loggerId, listeners);
  }

  /**
   * Remove the listener for the {@link Logger} with given id. If there is a back up listener for
   * the logger with the given id, the first registered listener will be put into use.
   *
   * @param loggerId The id of the {@link Logger}.
   * @param listener The listener to unregister.
   */
  public void removeOnLogRequestedListener(int loggerId, @NonNull OnLogRequestedListener listener) {
    ArrayDeque<OnLogRequestedListener> listeners = registeredLoggers.get(loggerId);
    if (listeners == null || !listeners.contains(listener)) {
      logw(TAG, "Unable to unregister listener " + listener + ". It has not been registered.");
      return;
    }
    listeners.remove(listener);
    if (backupLogRequestedListeners.containsKey(listener)) {
      backupLogRequestedListeners.remove(listener);
      logd(TAG, "Unregistered backup listener " + listener + " for Logger " + loggerId + ".");
      return;
    }
    if (!logRequestedListeners.contains(listener)) {
      return;
    }
    logRequestedListeners.remove(listener);
    logd(TAG, "Unregistered listener " + listener + " for Logger " + loggerId + ".");
    if (listeners.isEmpty()) {
      registeredLoggers.remove(loggerId);
      return;
    }
    OnLogRequestedListener backupListener = listeners.peek();
    Executor executor = backupLogRequestedListeners.remove(backupListener);
    logRequestedListeners.add(backupListener, executor);
    logd(
        TAG, "Backup listener " + backupListener + " is now listening to Logger " + loggerId + ".");
  }

  /**
   * Register a callback for logging events.
   *
   * @param callback The callback to register.
   * @param executor The executor that the callback will be executed on.
   */
  public void registerLoggingEventCallback(
      @NonNull LoggingEventCallback callback, @NonNull Executor executor) {
    loggingEventCallbacks.add(callback, executor);
  }

  /**
   * Unregister a logging event callback.
   *
   * @param callback The callback to unregister.
   */
  public void unregisterLoggingEventCallback(@NonNull LoggingEventCallback callback) {
    loggingEventCallbacks.remove(callback);
  }

  /**
   * Register a callback for log file events.
   *
   * @param callback The callback to register.
   * @param executor The executor that the callback will be executed on.
   */
  public void registerLogFileCallback(
      @NonNull LogFileCallback callback, @NonNull Executor executor) {
    logFileCallbacks.add(callback, executor);
  }

  /** Reset internal processes. */
  public void reset() {
    resetSession();
    registeredLoggers.clear();
    logRequestedListeners.clear();
    backupLogRequestedListeners.clear();
    loggingEventCallbacks.clear();
  }

  @VisibleForTesting
  void setFileHelper(FileHelper fileHelper) {
    this.fileHelper = fileHelper;
  }

  private boolean isCollectingLogRecords() {
    return isCollectingLogRecordsToGenerateLogFile.get()
        || isCollectingLogRecordsToSendLocalLog.get();
  }

  private void resetSession() {
    isCollectingLogRecordsToGenerateLogFile.set(false);
    isCollectingLogRecordsToSendLocalLog.set(false);
    loggerLogRecords.clear();
  }

  private boolean writeLogFile(String fileName, byte[] logRecords) {
    String dir = getFilesDir();
    String path = dir + File.separator + fileName;
    logd(TAG, "Generating file: " + path);
    try {
      fileHelper.writeToFile(logRecords, dir, fileName);
      removeOldLogFiles();
      logFileCallbacks.invoke(callback -> callback.onLogFileGenerated(fileName));
      return true;
    } catch (IOException e) {
      loge(TAG, "Failed to generate log file " + path, e);
      return false;
    }
  }

  private void removeOldLogFiles() {
    List<File> files = loadLogFiles();
    for (int i = 0; i < files.size() - LOG_FILES_MAX_NUM; i++) {
      files.get(i).delete();
    }
  }

  @NonNull
  private String getFilesDir() {
    return context.getFilesDir().getPath();
  }

  @NonNull
  private static String getLogFileNameForDevice(String deviceName) {
    return deviceName + FILE_NAME_SEPARATOR + getCurrentTime() + LOG_FILE_EXTENSION;
  }

  // DateTimeFormatter was added in API level 26 and the project has a min SDK of 29.
  // https://developer.android.com/reference/java/time/format/DateTimeFormatter
  @SuppressWarnings("AndroidJdkLibsChecker")
  @NonNull
  private static String getCurrentTime() {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FILE_NAME_TIME_FORMAT);
    return ZonedDateTime.now(ZoneOffset.UTC).format(formatter);
  }

  /** Listener for the log request. */
  public interface OnLogRequestedListener {
    /** Triggered when a log request is received from a connected device. */
    void onLogRecordsRequested();
  }

  /** Callback for triggered logging events from {@link LoggingManager}. */
  public interface LoggingEventCallback {
    /** Triggered when the log of connected remote device is requested. */
    void onRemoteLogRequested();

    /** Triggered when the local log is available. */
    void onLocalLogAvailable(byte[] log);
  }

  /** Callback for triggered log file events from {@link LoggingManager}. */
  public interface LogFileCallback {
    /** Triggered when a new log file has generated. */
    void onLogFileGenerated(String fileName);

    /** Triggered when generating log file failed. */
    void onLogFileError();
  }
}
