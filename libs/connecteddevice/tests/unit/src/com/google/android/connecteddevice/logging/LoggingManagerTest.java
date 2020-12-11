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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.logging.LoggingManager.LogFileCallback;
import com.google.android.connecteddevice.logging.LoggingManager.LoggingEventCallback;
import com.google.android.connecteddevice.logging.LoggingManager.OnLogRequestedListener;
import com.google.android.connecteddevice.logging.model.LogRecord;
import com.google.android.connecteddevice.logging.model.LogRecordFile;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class LoggingManagerTest {
  private static final int TEST_LOGGER_ID1 = 1;
  private static final int TEST_LOGGER_ID2 = 2;
  private static final String TEST_DEVICE_NAME = "test_device_name";

  private final Context context = ApplicationProvider.getApplicationContext();
  private final Executor callbackExecutor = MoreExecutors.directExecutor();
  private final FileHelper fileHelper = new FileHelper();

  @Mock private FileHelper mockFileHelper;
  @Mock private OnLogRequestedListener mockListener1;
  @Mock private OnLogRequestedListener mockListener2;
  @Mock private LoggingEventCallback mockLoggingEventCallback;
  @Mock private LogFileCallback mockLogFileCallback;

  private LoggingManager loggingManager;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    loggingManager = new LoggingManager(context);
    loggingManager.setFileHelper(mockFileHelper);
  }

  @After
  public void tearDown() {
    if (loggingManager != null) {
      loggingManager.reset();
    }
  }

  @Test
  public void generateLogFile() {
    loggingManager.addOnLogRequestedListener(TEST_LOGGER_ID1, mockListener1, callbackExecutor);
    loggingManager.generateLogFile();
    verify(mockListener1).onLogRecordsRequested();
    assertThat(loggingManager.isCollectingLogRecordsToGenerateLogFile.get()).isTrue();
    assertThat(loggingManager.isCollectingLogRecordsToSendLocalLog.get()).isFalse();
  }

  @Test
  public void sendLogRequest() {
    loggingManager.registerLoggingEventCallback(mockLoggingEventCallback, callbackExecutor);
    loggingManager.sendLogRequest();
    verify(mockLoggingEventCallback).onRemoteLogRequested();
  }

  @Test
  public void startSendingLogRecords_collectLogRecords() {
    loggingManager.addOnLogRequestedListener(TEST_LOGGER_ID1, mockListener1, callbackExecutor);
    loggingManager.startSendingLogRecords();
    verify(mockListener1).onLogRecordsRequested();
    assertThat(loggingManager.isCollectingLogRecordsToSendLocalLog.get()).isTrue();
    assertThat(loggingManager.isCollectingLogRecordsToGenerateLogFile.get()).isFalse();
  }

  @Test
  public void prepareLocalLogsRecords_generateLogFile() throws IOException {
    loggingManager.addOnLogRequestedListener(TEST_LOGGER_ID1, mockListener1, callbackExecutor);
    loggingManager.addOnLogRequestedListener(TEST_LOGGER_ID2, mockListener2, callbackExecutor);
    loggingManager.registerLogFileCallback(mockLogFileCallback, callbackExecutor);

    byte[] logRecords1 = createRandomLogRecords(10);
    byte[] logRecords2 = createRandomLogRecords(10);
    List<byte[]> logRecordsList = Arrays.asList(logRecords1, logRecords2);
    LogRecordFile expectedLogRecordFile = fileHelper.mergeLogsIntoLogRecordFile(logRecordsList);
    when(mockFileHelper.mergeLogsIntoLogRecordFile(anyList())).thenReturn(expectedLogRecordFile);

    loggingManager.generateLogFile();
    loggingManager.prepareLocalLogRecords(TEST_LOGGER_ID1, logRecords1);
    loggingManager.prepareLocalLogRecords(TEST_LOGGER_ID2, logRecords2);
    verify(mockFileHelper)
        .writeToFile(eq(expectedLogRecordFile.toByteArray()), anyString(), anyString());
    verify(mockLogFileCallback).onLogFileGenerated(anyString());
  }

  @Test
  public void prepareLocalLogsRecords_startSendingLogRecords() {
    loggingManager.addOnLogRequestedListener(TEST_LOGGER_ID1, mockListener1, callbackExecutor);
    loggingManager.addOnLogRequestedListener(TEST_LOGGER_ID2, mockListener2, callbackExecutor);
    loggingManager.registerLoggingEventCallback(mockLoggingEventCallback, callbackExecutor);

    byte[] logRecords1 = createRandomLogRecords(10);
    byte[] logRecords2 = createRandomLogRecords(10);
    List<byte[]> logRecordsList = Arrays.asList(logRecords1, logRecords2);
    LogRecordFile expectedLogRecordFile = fileHelper.mergeLogsIntoLogRecordFile(logRecordsList);
    when(mockFileHelper.mergeLogsIntoLogRecordFile(anyList())).thenReturn(expectedLogRecordFile);

    loggingManager.startSendingLogRecords();
    loggingManager.prepareLocalLogRecords(TEST_LOGGER_ID1, logRecords1);
    loggingManager.prepareLocalLogRecords(TEST_LOGGER_ID2, logRecords2);
    verify(mockLoggingEventCallback).onLocalLogAvailable(eq(expectedLogRecordFile.toByteArray()));
  }

  @Test
  public void processRemoteLogRecords() throws IOException {
    loggingManager.registerLogFileCallback(mockLogFileCallback, callbackExecutor);

    ConnectedDevice remoteDevice = createConnectedDevice();
    byte[] remoteLogRecords = createRandomLogRecords(10);
    loggingManager.processRemoteLogRecords(remoteDevice, remoteLogRecords);
    verify(mockFileHelper).writeToFile(any(), anyString(), anyString());
    verify(mockLogFileCallback).onLogFileGenerated(anyString());
  }

  @Test
  public void registerOnLogRequestedListener_multipleListenersForOneLogger() {
    loggingManager.addOnLogRequestedListener(TEST_LOGGER_ID1, mockListener1, callbackExecutor);
    loggingManager.addOnLogRequestedListener(TEST_LOGGER_ID1, mockListener2, callbackExecutor);

    loggingManager.generateLogFile();
    verify(mockListener1).onLogRecordsRequested();
    verify(mockListener2, never()).onLogRecordsRequested();
  }

  @Test
  public void unregisterOnLogRequestedListener_multipleListenersForOneLogger() {
    loggingManager.addOnLogRequestedListener(TEST_LOGGER_ID1, mockListener1, callbackExecutor);
    loggingManager.addOnLogRequestedListener(TEST_LOGGER_ID1, mockListener2, callbackExecutor);
    loggingManager.removeOnLogRequestedListener(TEST_LOGGER_ID1, mockListener1);

    loggingManager.generateLogFile();
    verify(mockListener2).onLogRecordsRequested();
  }

  private static byte[] createRandomLogRecords(int size) {
    List<LogRecord> logRecords = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      logRecords.add(new LogRecord(LogRecord.Level.INFO, "TEST_TAG", "TEST_MESSAGE"));
    }
    Gson gson = new Gson();
    return gson.toJson(logRecords).getBytes(UTF_8);
  }

  private static ConnectedDevice createConnectedDevice() {
    String deviceId = UUID.randomUUID().toString();
    return new ConnectedDevice(
        deviceId, TEST_DEVICE_NAME, /* belongsToActiveUser= */ true, /* hasSecureChannel= */ true);
  }
}
