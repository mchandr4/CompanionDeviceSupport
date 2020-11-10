package com.google.android.connecteddevice.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.os.Build;
import androidx.annotation.NonNull;
import com.google.android.connecteddevice.model.LogRecord;
import com.google.android.connecteddevice.model.LogRecordFile;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Help methods for reading and writing log files. */
public class FileHelper {
  private static final String TAG = "FileHelper";

  public FileHelper() {}

  /** Writes values to a file with given file name in the given directory. */
  public void writeToFile(@NonNull byte[] values, @NonNull String dirPath, @NonNull String fileName)
      throws IOException {
    File fileDir = new File(dirPath);
    if (!fileDir.exists()) {
      if (!fileDir.mkdirs()) {
        SafeLog.logw(TAG, "Failed to create file directory " + dirPath + ".");
        return;
      }
    }
    String path = dirPath + File.separator + fileName;
    File file = new File(path);
    FileOutputStream stream = new FileOutputStream(file, /* append= */ true);
    OutputStreamWriter streamWriter = new OutputStreamWriter(stream, UTF_8);
    BufferedWriter writer = new BufferedWriter(streamWriter);
    writer.append(new String(values, UTF_8)).close();
  }

  /** Reads files in the given directory. */
  @NonNull
  public List<File> readFiles(@NonNull String dirPath) {
    File[] files = new File(dirPath).listFiles();
    return files == null ? new ArrayList<>() : Arrays.asList(files);
  }

  /** Merges multiple {@link LogRecord} lists into a {@link LogRecordFile} */
  @NonNull
  public LogRecordFile mergeLogsIntoLogRecordFile(@NonNull List<byte[]> logs) {
    LogRecordFile logRecordFile = new LogRecordFile(Build.MODEL, new ArrayList<>());
    Gson gson = new Gson();
    Type listType = TypeToken.getParameterized(ArrayList.class, LogRecord.class).getType();
    for (byte[] loggerLogRecords : logs) {
      List<LogRecord> logRecordList = gson.fromJson(new String(loggerLogRecords), listType);
      logRecordFile.appendLogRecords(logRecordList);
    }
    return logRecordFile;
  }
}
