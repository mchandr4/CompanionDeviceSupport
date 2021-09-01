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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.api.IOnLogRequestedListener;
import com.google.android.connecteddevice.logging.LoggingManager.LogFileCallback;
import com.google.android.connecteddevice.logging.LoggingManager.LoggingEventCallback;
import com.google.android.connecteddevice.logging.LoggingManager.OnLogRequestedListener;
import com.google.android.connecteddevice.logging.model.LogRecord;
import com.google.android.connecteddevice.logging.model.LogRecordFile;
import com.google.android.connecteddevice.logging.util.LoggingUtils;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.util.Logger;
import com.google.common.util.concurrent.MoreExecutors;
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
import org.mockito.invocation.InvocationOnMock;

@RunWith(AndroidJUnit4.class)
public class LoggingManagerTest {
  private static final int TEST_LOGGER_ID0 = 0;
  private static final int TEST_LOGGER_ID1 = 1;
  private static final String TEST_DEVICE_NAME = "test_device_name";

  private final Context context = ApplicationProvider.getApplicationContext();
  private final Executor callbackExecutor = MoreExecutors.directExecutor();
  private final FileHelper fileHelper = new FileHelper();

  @Mock private FileHelper mockFileHelper;
  @Mock private Logger mockLogger0;
  @Mock private OnLogRequestedListener mockListener0;
  @Mock private OnLogRequestedListener mockListener1;
  @Mock private IOnLogRequestedListener mockRemoteListener0;
  @Mock private IOnLogRequestedListener mockRemoteListener1;
  @Mock private IBinder mockAliveIBinder;
  @Mock private IBinder mockDeadIBinder;
  @Mock private LoggingEventCallback mockLoggingEventCallback;
  @Mock private LogFileCallback mockLogFileCallback;

  private LoggingManager loggingManager;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(mockLogger0.getLoggerId()).thenReturn(TEST_LOGGER_ID0);
    when(mockRemoteListener0.asBinder()).thenReturn(mockAliveIBinder);
    when(mockRemoteListener1.asBinder()).thenReturn(mockAliveIBinder);
    when(mockAliveIBinder.isBinderAlive()).thenReturn(true);
    when(mockDeadIBinder.isBinderAlive()).thenReturn(false);
    loggingManager = new LoggingManager(context);
    loggingManager.start(mockLogger0, mockListener0, callbackExecutor);
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
    loggingManager.registerLogRequestedListener(TEST_LOGGER_ID1, mockListener1, callbackExecutor);
    loggingManager.generateLogFile();
    verify(mockListener0).onLogRecordsRequested();
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
    loggingManager.registerLogRequestedListener(TEST_LOGGER_ID1, mockListener1, callbackExecutor);
    loggingManager.startSendingLogRecords();
    verify(mockListener0).onLogRecordsRequested();
    verify(mockListener1).onLogRecordsRequested();
    assertThat(loggingManager.isCollectingLogRecordsToSendLocalLog.get()).isTrue();
    assertThat(loggingManager.isCollectingLogRecordsToGenerateLogFile.get()).isFalse();
  }

  @Test
  public void prepareLocalLogsRecords_generateLogFile() throws IOException {
    loggingManager.registerLogRequestedListener(TEST_LOGGER_ID1, mockListener1, callbackExecutor);
    loggingManager.registerLogFileCallback(mockLogFileCallback, callbackExecutor);

    byte[] logRecords0 = createRandomLogRecords(10);
    byte[] logRecords1 = createRandomLogRecords(10);
    List<byte[]> logRecordsList = Arrays.asList(logRecords0, logRecords1);
    LogRecordFile expectedLogRecordFile = fileHelper.mergeLogsIntoLogRecordFile(logRecordsList);
    when(mockFileHelper.mergeLogsIntoLogRecordFile(anyList())).thenReturn(expectedLogRecordFile);

    loggingManager.generateLogFile();
    loggingManager.prepareLocalLogRecords(TEST_LOGGER_ID0, logRecords0);
    loggingManager.prepareLocalLogRecords(TEST_LOGGER_ID1, logRecords1);
    verify(mockFileHelper)
        .writeToFile(eq(expectedLogRecordFile.toByteArray()), anyString(), anyString());
    verify(mockLogFileCallback).onLogFileGenerated(anyString());
  }

  @Test
  public void prepareLocalLogsRecords_startSendingLogRecords() {
    loggingManager.registerLogRequestedListener(TEST_LOGGER_ID1, mockListener1, callbackExecutor);
    loggingManager.registerLoggingEventCallback(mockLoggingEventCallback, callbackExecutor);

    byte[] logRecords0 = createRandomLogRecords(10);
    byte[] logRecords1 = createRandomLogRecords(10);
    List<byte[]> logRecordsList = Arrays.asList(logRecords0, logRecords1);
    LogRecordFile expectedLogRecordFile = fileHelper.mergeLogsIntoLogRecordFile(logRecordsList);
    when(mockFileHelper.mergeLogsIntoLogRecordFile(anyList())).thenReturn(expectedLogRecordFile);

    loggingManager.startSendingLogRecords();
    loggingManager.prepareLocalLogRecords(TEST_LOGGER_ID0, logRecords0);
    loggingManager.prepareLocalLogRecords(TEST_LOGGER_ID1, logRecords1);
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
  public void registerLogRequestedListener_listenerRegistered() throws RemoteException {
    loggingManager.registerLogRequestedListener(
        TEST_LOGGER_ID1, mockRemoteListener0, callbackExecutor);

    loggingManager.generateLogFile();

    verify(mockListener0).onLogRecordsRequested();
    verify(mockRemoteListener0).onLogRecordsRequested();
  }

  @Test
  public void registerLogRequestedListener_multipleListenersForOneLogger_oneListenerCanBeInvoked() {
    loggingManager.registerLogRequestedListener(TEST_LOGGER_ID0, mockListener1, callbackExecutor);
    loggingManager.registerLogRequestedListener(
        TEST_LOGGER_ID0, mockRemoteListener0, callbackExecutor);
    loggingManager.registerLogRequestedListener(
        TEST_LOGGER_ID0, mockRemoteListener1, callbackExecutor);

    loggingManager.generateLogFile();

    long invocationSize =
        getOnLogRecordsRequestedInvocationSize(mockListener0)
            + getOnLogRecordsRequestedInvocationSize(mockListener1)
            + getOnLogRecordsRequestedInvocationSize(mockRemoteListener0)
            + getOnLogRecordsRequestedInvocationSize(mockRemoteListener1);
    assertThat(invocationSize).isEqualTo(1L);
  }

  @Test
  public void unregisterOnLogRequestedListener_listenerUnregistered() throws RemoteException {
    loggingManager.registerLogRequestedListener(
        TEST_LOGGER_ID1, mockRemoteListener0, callbackExecutor);
    loggingManager.unregisterLogRequestedListener(TEST_LOGGER_ID0, mockListener0);
    loggingManager.unregisterLogRequestedListener(TEST_LOGGER_ID1, mockRemoteListener0);

    loggingManager.generateLogFile();

    verify(mockListener0, never()).onLogRecordsRequested();
    verify(mockRemoteListener0, never()).onLogRecordsRequested();
  }

  @Test
  public void
      unregisterLogRequestedListener_multipleListenersForOneLogger_remainedListenerCanBeInvoked() {
    loggingManager.registerLogRequestedListener(TEST_LOGGER_ID0, mockListener1, callbackExecutor);
    loggingManager.registerLogRequestedListener(
        TEST_LOGGER_ID0, mockRemoteListener0, callbackExecutor);

    loggingManager.unregisterLogRequestedListener(TEST_LOGGER_ID0, mockListener0);

    loggingManager.generateLogFile();

    long invocationSize =
        getOnLogRecordsRequestedInvocationSize(mockListener1)
            + getOnLogRecordsRequestedInvocationSize(mockRemoteListener0);
    assertThat(invocationSize).isEqualTo(1L);
  }

  @Test
  public void onLogRequested_multipleListenersForOneLogger_invokeAliveListener()
      throws RemoteException {
    when(mockRemoteListener0.asBinder()).thenReturn(mockDeadIBinder);
    loggingManager.registerLogRequestedListener(
        TEST_LOGGER_ID1, mockRemoteListener0, callbackExecutor);
    loggingManager.registerLogRequestedListener(
        TEST_LOGGER_ID1, mockRemoteListener1, callbackExecutor);

    loggingManager.generateLogFile();

    verify(mockRemoteListener0, never()).onLogRecordsRequested();
    verify(mockRemoteListener1).onLogRecordsRequested();
  }

  private static <T> long getOnLogRecordsRequestedInvocationSize(T mockListener) {
    return mockingDetails(mockListener).getInvocations().stream()
        .map(InvocationOnMock::getMethod)
        .filter(s -> s.getName().equals("onLogRecordsRequested"))
        .count();
  }

  private static byte[] createRandomLogRecords(int size) {
    List<LogRecord> logRecords = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      logRecords.add(new LogRecord(LogRecord.Level.INFO, "TEST_TAG", "TEST_MESSAGE"));
    }
    return LoggingUtils.objectToBytes(logRecords);
  }

  private static ConnectedDevice createConnectedDevice() {
    String deviceId = UUID.randomUUID().toString();
    return new ConnectedDevice(
        deviceId, TEST_DEVICE_NAME, /* belongsToActiveUser= */ true, /* hasSecureChannel= */ true);
  }
}
