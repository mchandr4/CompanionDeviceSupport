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

package com.google.android.connecteddevice.logging;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.os.Build;
import androidx.annotation.NonNull;
import com.google.android.connecteddevice.logging.model.LogRecord;
import com.google.android.connecteddevice.logging.model.LogRecordFile;
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
