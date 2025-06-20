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

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.content.Context;
import android.os.ParcelUuid;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.companionprotos.LoggingMessageProto.LoggingMessage;
import com.google.android.companionprotos.LoggingMessageProto.LoggingMessage.MessageType;
import com.google.android.connecteddevice.api.Connector;
import com.google.android.connecteddevice.api.RemoteFeature;
import com.google.android.connecteddevice.logging.LoggingManager.LoggingEventCallback;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;

/** Feature that sends and receives logging support message. */
public class LoggingFeature extends RemoteFeature {
  private static final String TAG = "LoggingFeature";
  private static final int LOGGING_MESSAGE_VERSION = 1;
  private static final ParcelUuid FEATURE_ID =
      ParcelUuid.fromString("675836fb-18ed-4c60-94cd-131352e8a5b7");

  private final LoggingManager loggingManager;
  private final Set<ConnectedDevice> connectedDevices = new CopyOnWriteArraySet<>();

  private final LoggingEventCallback loggingEventCallback =
      new LoggingEventCallback() {
        @Override
        public void onRemoteLogRequested() {
          for (ConnectedDevice device : connectedDevices) {
            sendMessageSecurely(device, createRemoteRequestMessage());
          }
        }

        @Override
        public void onLocalLogAvailable(byte[] log) {
          for (ConnectedDevice device : connectedDevices) {
            sendMessageSecurely(device, createLocalLogMessage(log));
          }
        }
      };

  public LoggingFeature(
      @NonNull Context context,
      @NonNull LoggingManager loggingManager,
      @NonNull Connector connector) {
    super(context, FEATURE_ID, connector);
    this.loggingManager = loggingManager;
    loggingManager.registerLoggingEventCallback(
        loggingEventCallback, Executors.newSingleThreadExecutor());
  }

  @Override
  protected void onSecureChannelEstablished(ConnectedDevice device) {
    connectedDevices.add(device);
  }

  @Override
  protected void onDeviceDisconnected(ConnectedDevice device) {
    connectedDevices.remove(device);
  }

  @Override
  protected void onMessageReceived(ConnectedDevice device, byte[] message) {
    LoggingMessage loggingMessage;
    try {
      loggingMessage =
          LoggingMessage.parseFrom(message, ExtensionRegistryLite.getEmptyRegistry());
    } catch (InvalidProtocolBufferException e) {
      loge(TAG, "Received message from client, but cannot parse.", e);
      return;
    }
    if (loggingMessage.getVersion() != LOGGING_MESSAGE_VERSION) {
      loge(
          TAG,
          "Received logging message from connected device "
              + device
              + ", but message version not supported.");
      return;
    }
    switch (loggingMessage.getType()) {
      case START_SENDING:
        logd(TAG, "Received start sending request.");
        loggingManager.startSendingLogRecords();
        break;
      case LOG:
        logd(TAG, "Received remote log from device: " + device.getDeviceName() + ".");
        loggingManager.processRemoteLogRecords(device, loggingMessage.getPayload().toByteArray());
        break;
      default:
        loge(
            TAG,
            "Received a message from the client with an invalid MessageType ("
                + loggingMessage.getTypeValue()
                + "). Ignoring.");
    }
  }

  /** Creates a log message with the given log. */
  @VisibleForTesting
  @NonNull
  static byte[] createLocalLogMessage(@NonNull byte[] log) {
    return LoggingMessage.newBuilder()
        .setVersion(LOGGING_MESSAGE_VERSION)
        .setType(MessageType.LOG)
        .setPayload(ByteString.copyFrom(log))
        .build()
        .toByteArray();
  }

  /** Creates a log request message. */
  @VisibleForTesting
  @NonNull
  static byte[] createRemoteRequestMessage() {
    return LoggingMessage.newBuilder()
        .setVersion(LOGGING_MESSAGE_VERSION)
        .setType(MessageType.START_SENDING)
        .build()
        .toByteArray();
  }
}
