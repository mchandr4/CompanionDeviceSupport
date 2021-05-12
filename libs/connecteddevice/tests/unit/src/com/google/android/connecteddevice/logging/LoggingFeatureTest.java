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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.companionprotos.LoggingMessageProto.LoggingMessage;
import com.google.android.companionprotos.LoggingMessageProto.LoggingMessage.MessageType;
import com.google.android.connecteddevice.api.IConnectedDeviceManager;
import com.google.android.connecteddevice.logging.LoggingManager.LoggingEventCallback;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.DeviceMessage;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.concurrent.Executor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class LoggingFeatureTest {

  private final Context context = ApplicationProvider.getApplicationContext();

  @Mock private LoggingManager mockLoggingManager;
  @Mock private IConnectedDeviceManager mockConnectedDeviceManager;

  private LoggingFeature loggingFeature;
  private LoggingEventCallback loggingEventCallback;

  @Before
  public void createLoggingFeature() {
    MockitoAnnotations.initMocks(this);

    ArgumentCaptor<LoggingEventCallback> callbackCaptor =
        ArgumentCaptor.forClass(LoggingEventCallback.class);
    loggingFeature = new LoggingFeature(context, mockConnectedDeviceManager, mockLoggingManager);
    verify(mockLoggingManager)
        .registerLoggingEventCallback(callbackCaptor.capture(), any(Executor.class));
    loggingEventCallback = callbackCaptor.getValue();
  }

  @Test
  public void onLogMessageReceived_sendRequest() {
    ConnectedDevice connectedDevice = createConnectedDevice();

    loggingFeature.onMessageReceived(connectedDevice, LoggingFeature.createRemoteRequestMessage());

    verify(mockLoggingManager).startSendingLogRecords();
  }

  @Test
  public void onLogMessageReceived_log() {
    byte[] payload = "LOG_MESSAGE_CONTENT".getBytes(UTF_8);
    ConnectedDevice connectedDevice = createConnectedDevice();

    loggingFeature.onMessageReceived(
        connectedDevice, LoggingFeature.createLocalLogMessage(payload));

    verify(mockLoggingManager).processRemoteLogRecords(connectedDevice, payload);
  }

  @Test
  public void onLogMessageReceived_unparseableMessage() {
    byte[] payload = "UNPARSEABLE_MESSAGE".getBytes(UTF_8);
    ConnectedDevice connectedDevice = createConnectedDevice();

    loggingFeature.onMessageReceived(connectedDevice, payload);

    verify(mockLoggingManager, never()).startSendingLogRecords();
    verify(mockLoggingManager, never())
        .processRemoteLogRecords(any(ConnectedDevice.class), any(byte[].class));
  }

  @Test
  public void loggingEventCallback_onRemoteLogRequested()
      throws InvalidProtocolBufferException, RemoteException {
    ArgumentCaptor<DeviceMessage> messageCaptor = ArgumentCaptor.forClass(DeviceMessage.class);
    ConnectedDevice connectedDevice = createConnectedDevice();

    loggingFeature.onSecureChannelEstablished(connectedDevice);
    loggingEventCallback.onRemoteLogRequested();

    verify(mockConnectedDeviceManager)
        .sendMessage(eq(connectedDevice), messageCaptor.capture());
    LoggingMessage message =
        LoggingMessage.parseFrom(
            messageCaptor.getValue().getMessage(),
            ExtensionRegistryLite.getEmptyRegistry());
    assertThat(message.getType()).isEqualTo(MessageType.START_SENDING);
  }

  @Test
  public void loggingEventCallback_onLocalLogAvailable()
      throws InvalidProtocolBufferException, RemoteException {
    ArgumentCaptor<DeviceMessage> messageCaptor = ArgumentCaptor.forClass(DeviceMessage.class);
    byte[] testLogs = "LOG_MESSAGE_CONTENT".getBytes(UTF_8);
    ConnectedDevice connectedDevice = createConnectedDevice();

    loggingFeature.onSecureChannelEstablished(connectedDevice);
    loggingEventCallback.onLocalLogAvailable(testLogs);

    verify(mockConnectedDeviceManager)
        .sendMessage(eq(connectedDevice), messageCaptor.capture());
    LoggingMessage message =
        LoggingMessage.parseFrom(
            messageCaptor.getValue().getMessage(),
            ExtensionRegistryLite.getEmptyRegistry());
    assertThat(message.getType()).isEqualTo(MessageType.LOG);
  }

  @NonNull
  private static ConnectedDevice createConnectedDevice() {
    return new ConnectedDevice(
        /* deviceId = */ "TEST_ID",
        /* deviceName = */ "TEST_NAME",
        /* belongsToActiveUser = */ true,
        /* hasSecureChannel = */ true);
  }
}
