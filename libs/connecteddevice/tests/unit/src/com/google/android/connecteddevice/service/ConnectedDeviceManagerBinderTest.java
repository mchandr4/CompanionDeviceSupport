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

package com.google.android.connecteddevice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.os.ParcelUuid;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.ConnectedDeviceManager;
import com.google.android.connecteddevice.ConnectedDeviceManager.ConnectionCallback;
import com.google.android.connecteddevice.ConnectedDeviceManager.DeviceAssociationCallback;
import com.google.android.connecteddevice.ConnectedDeviceManager.DeviceCallback;
import com.google.android.connecteddevice.api.IConnectionCallback;
import com.google.android.connecteddevice.api.IDeviceAssociationCallback;
import com.google.android.connecteddevice.api.IDeviceCallback;
import com.google.android.connecteddevice.api.IOnLogRequestedListener;
import com.google.android.connecteddevice.logging.LoggingManager;
import com.google.android.connecteddevice.logging.LoggingManager.OnLogRequestedListener;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.DeviceMessage;
import com.google.android.connecteddevice.model.DeviceMessage.OperationType;
import com.google.android.connecteddevice.util.ByteUtils;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public class ConnectedDeviceManagerBinderTest {

  private final ParcelUuid recipientId = new ParcelUuid(UUID.randomUUID());

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Mock private ConnectedDeviceManager mockConnectedDeviceManager;
  @Mock private LoggingManager mockLoggingManager;

  private ConnectedDeviceManagerBinder binder;

  @Before
  public void setUp() {
    binder =
        new ConnectedDeviceManagerBinder(
            mockConnectedDeviceManager, mockLoggingManager);
  }

  @Test
  public void registerActiveUserConnectionCallback_mirrorsConnectedDeviceManager() {
    binder.registerActiveUserConnectionCallback(createConnectionCallback());
    verify(mockConnectedDeviceManager)
        .registerActiveUserConnectionCallback(any(ConnectionCallback.class), any(Executor.class));
  }

  @Test
  public void unregisterConnectionCallback_mirrorsConnectedDeviceManager() {
    IConnectionCallback callback = createConnectionCallback();
    binder.registerActiveUserConnectionCallback(callback);
    binder.unregisterConnectionCallback(callback);
    verify(mockConnectedDeviceManager).unregisterConnectionCallback(any(ConnectionCallback.class));
  }

  @Test
  public void registerDeviceCallback_mirrorsConnectedDeviceManager() {
    IDeviceCallback deviceCallback = createDeviceCallback();
    ConnectedDevice connectedDevice =
        new ConnectedDevice(
            UUID.randomUUID().toString(),
            /* deviceName = */ null,
            /* belongsToActiveUser = */ false,
            /* hasSecureChannel = */ false);
    binder.registerDeviceCallback(connectedDevice, recipientId, deviceCallback);
    verify(mockConnectedDeviceManager)
        .registerDeviceCallback(
            refEq(connectedDevice), refEq(recipientId.getUuid()),
            any(DeviceCallback.class), any(Executor.class));
  }

  @Test
  public void unregisterDeviceCallback_mirrorsConnectedDeviceManager() {
    IDeviceCallback deviceCallback = createDeviceCallback();
    ConnectedDevice connectedDevice =
        new ConnectedDevice(
            UUID.randomUUID().toString(),
            /* deviceName = */ null,
            /* belongsToActiveUser = */ false,
            /* hasSecureChannel = */ false);
    binder.registerDeviceCallback(connectedDevice, recipientId, deviceCallback);
    binder.unregisterDeviceCallback(connectedDevice, recipientId, deviceCallback);
    verify(mockConnectedDeviceManager)
        .unregisterDeviceCallback(
            refEq(connectedDevice),
            refEq(recipientId.getUuid()),
            any(DeviceCallback.class));
  }

  @Test
  public void registerOnLogRequestedListener_mirrorsLoggingManager() {
    IOnLogRequestedListener listener = createOnLogRequestedListener();
    int testLoggerId = 1;
    binder.registerOnLogRequestedListener(testLoggerId, listener);
    verify(mockLoggingManager)
        .registerLogRequestedListener(
            eq(testLoggerId), any(OnLogRequestedListener.class), any(Executor.class));
  }

  @Test
  public void unregisterOnLogRequestedListener_mirrorsLoggingManager() {
    IOnLogRequestedListener listener = createOnLogRequestedListener();
    int testLoggerId = 1;
    binder.registerOnLogRequestedListener(testLoggerId, listener);
    binder.unregisterOnLogRequestedListener(testLoggerId, listener);
    verify(mockLoggingManager)
        .unregisterLogRequestedListener(eq(testLoggerId), any(OnLogRequestedListener.class));
  }

  @Test
  public void sendMessage_mirrorsConnectedDeviceManager() {
    ConnectedDevice connectedDevice =
        new ConnectedDevice(
            UUID.randomUUID().toString(),
            /* deviceName = */ null,
            /* belongsToActiveUser = */ false,
            /* hasSecureChannel = */ true);
    DeviceMessage message =
        DeviceMessage.createOutgoingMessage(
            recipientId.getUuid(),
            /* isMessageEncrypted= */ true,
            OperationType.CLIENT_MESSAGE,
            ByteUtils.randomBytes(10));
    binder.sendMessage(connectedDevice, message);
    verify(mockConnectedDeviceManager).sendMessage(connectedDevice, message);
  }


  @Test
  public void registerDeviceAssociationCallback_mirrorsConnectedDeviceManager() {
    IDeviceAssociationCallback associationCallback =
        createDeviceAssociationCallback();
    binder.registerDeviceAssociationCallback(associationCallback);
    verify(mockConnectedDeviceManager)
        .registerDeviceAssociationCallback(
            any(DeviceAssociationCallback.class), any(Executor.class));
  }

  @Test
  public void unregisterDeviceAssociationCallback_mirrorsConnectedDeviceManager() {
    IDeviceAssociationCallback associationCallback =
        createDeviceAssociationCallback();
    binder.registerDeviceAssociationCallback(associationCallback);
    binder.unregisterDeviceAssociationCallback(associationCallback);
    verify(mockConnectedDeviceManager)
        .unregisterDeviceAssociationCallback(any(DeviceAssociationCallback.class));
  }

  @NonNull
  private static IConnectionCallback createConnectionCallback() {
    return spy(
        new IConnectionCallback.Stub() {
          @Override
          public void onDeviceConnected(ConnectedDevice connectedDevice) {
          }

          @Override
          public void onDeviceDisconnected(ConnectedDevice connectedDevice) {
          }
        });
  }

  @NonNull
  private static IDeviceCallback createDeviceCallback() {
    return spy(
        new IDeviceCallback.Stub() {
          @Override
          public void onSecureChannelEstablished(ConnectedDevice connectedDevice) {
          }

          @Override
          public void onMessageReceived(ConnectedDevice connectedDevice, DeviceMessage message) {
          }

          @Override
          public void onDeviceError(ConnectedDevice connectedDevice, int error) {
          }
        });
  }

  @NonNull
  private static IDeviceAssociationCallback createDeviceAssociationCallback() {
    return spy(
        new IDeviceAssociationCallback.Stub() {
          @Override
          public void onAssociatedDeviceAdded(AssociatedDevice device) {
          }

          @Override
          public void onAssociatedDeviceRemoved(AssociatedDevice device) {
          }

          @Override
          public void onAssociatedDeviceUpdated(AssociatedDevice device) {
          }
        });
  }

  @NonNull
  private static IOnLogRequestedListener createOnLogRequestedListener() {
    return spy(
        new IOnLogRequestedListener.Stub() {
          @Override
          public void onLogRecordsRequested() {}
        });
  }
}
