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

package com.google.android.connecteddevice.transport.spp;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder.OnErrorListener;
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder.OnMessageReceivedListener;
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder.OnRemoteCallbackSetListener;
import com.google.android.connecteddevice.transport.spp.PendingConnection.OnConnectedListener;
import com.google.android.connecteddevice.transport.spp.PendingConnection.OnConnectionErrorListener;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoTestRule;

@RunWith(AndroidJUnit4.class)
public class ConnectedDeviceSppDelegateBinderTest {
  private static final UUID TEST_UUID = UUID.randomUUID();
  private static final BluetoothDevice TEST_BLUETOOTH_DEVICE =
      ApplicationProvider.getApplicationContext()
          .getSystemService(BluetoothManager.class)
          .getAdapter()
          .getRemoteDevice("00:11:22:33:44:55");
  private static final boolean TEST_IS_SECURE = true;
  private static final byte[] TEST_MESSAGE = "testMessage".getBytes(UTF_8);
  private static final PendingConnection TEST_PENDING_CONNECTION =
      new PendingConnection(TEST_UUID, null, TEST_IS_SECURE);
  private static final int TEST_PENDING_CONNECTION_ID = TEST_PENDING_CONNECTION.getId();

  @Rule public MockitoTestRule mockitoTestRule = MockitoJUnit.testRule(this);

  private final Connection testConnection =
      new Connection(
          new ParcelUuid(TEST_UUID),
          TEST_BLUETOOTH_DEVICE,
          TEST_IS_SECURE,
          TEST_BLUETOOTH_DEVICE.getName());
  private final PendingConnection testPendingConnection =
      new PendingConnection(TEST_UUID, TEST_IS_SECURE);
  private ConnectedDeviceSppDelegateBinder connectedDeviceSppDelegateBinder;

  @Mock private ISppCallback mockRemoteCallback;
  @Mock private ISppCallback mockOldRemoteCallback;
  @Mock private IBinder mockRemoteCallbackBinder;
  @Mock private OnErrorListener mockOnErrorListener;
  @Mock private OnMessageReceivedListener mockOnMessageReceivedListener;
  @Mock private OnConnectedListener mockOnConnectedListener;
  @Mock private OnConnectionErrorListener mockOnConnectionErrorListener;
  @Mock private OnRemoteCallbackSetListener mockOnRemoteCallbackSetListener;

  @Before
  public void setUp() throws RemoteException {
    when(mockRemoteCallback.asBinder()).thenReturn(mockRemoteCallbackBinder);
    connectedDeviceSppDelegateBinder =
        new ConnectedDeviceSppDelegateBinder(mockOnRemoteCallbackSetListener);

    connectedDeviceSppDelegateBinder.setCallback(mockRemoteCallback);
    verify(mockRemoteCallback).asBinder();
    verify(mockRemoteCallbackBinder).linkToDeath(any(), anyInt());
    verify(mockOnRemoteCallbackSetListener).onRemoteCallbackSet(true);

    connectedDeviceSppDelegateBinder.setOnMessageReceivedListener(
        testConnection, mockOnMessageReceivedListener);
    connectedDeviceSppDelegateBinder.registerConnectionCallback(TEST_UUID, mockOnErrorListener);
    testPendingConnection
        .setOnConnectionErrorListener(mockOnConnectionErrorListener)
        .setOnConnectedListener(mockOnConnectedListener);
    when(mockRemoteCallback.onStartConnectionAsServerRequested(
            new ParcelUuid(TEST_UUID), TEST_IS_SECURE))
        .thenReturn(true);
  }

  @Test
  public void connectAsServer() throws Exception {
    connectedDeviceSppDelegateBinder.connectAsServer(TEST_UUID, TEST_IS_SECURE);
    verify(mockRemoteCallback)
        .onStartConnectionAsServerRequested(new ParcelUuid(TEST_UUID), TEST_IS_SECURE);
  }

  @Test
  public void connectAsClient() throws Exception {
    connectedDeviceSppDelegateBinder.connectAsClient(
        TEST_UUID, TEST_BLUETOOTH_DEVICE, TEST_IS_SECURE);
    verify(mockRemoteCallback)
        .onStartConnectionAsClientRequested(
            new ParcelUuid(TEST_UUID), TEST_BLUETOOTH_DEVICE, TEST_IS_SECURE);
  }

  @Test
  public void disconnect() throws Exception {
    connectedDeviceSppDelegateBinder.disconnect(testConnection);
    verify(mockRemoteCallback).onDisconnectRequested(testConnection);
  }

  @Test
  public void cancelConnectionAttempt() throws Exception {
    connectedDeviceSppDelegateBinder.cancelConnectionAttempt(TEST_PENDING_CONNECTION);
    verify(mockRemoteCallback).onCancelConnectionAttemptRequested(TEST_PENDING_CONNECTION_ID);
  }

  @Test
  public void sendMessage() throws Exception {
    connectedDeviceSppDelegateBinder.sendMessage(testConnection, TEST_MESSAGE);
    verify(mockRemoteCallback).onSendMessageRequested(testConnection, TEST_MESSAGE);
  }

  @Test
  public void clearCallback() throws Exception {
    connectedDeviceSppDelegateBinder.clearCallback(mockOldRemoteCallback);
    assertThat(connectedDeviceSppDelegateBinder.callbackBinder).isNotNull();

    connectedDeviceSppDelegateBinder.clearCallback(mockRemoteCallback);
    verify(mockRemoteCallbackBinder).unlinkToDeath(any(), anyInt());
    assertThat(connectedDeviceSppDelegateBinder.callbackBinder).isNull();
    verify(mockOnRemoteCallbackSetListener).onRemoteCallbackSet(false);

    connectedDeviceSppDelegateBinder.connectAsServer(TEST_UUID, TEST_IS_SECURE);
    verify(mockRemoteCallback, never()).onStartConnectionAsServerRequested(any(), anyBoolean());

    connectedDeviceSppDelegateBinder.connectAsClient(
        TEST_UUID, TEST_BLUETOOTH_DEVICE, TEST_IS_SECURE);
    verify(mockRemoteCallback, never())
        .onStartConnectionAsClientRequested(any(), any(), anyBoolean());

    connectedDeviceSppDelegateBinder.sendMessage(testConnection, TEST_MESSAGE);
    verify(mockRemoteCallback, never()).onSendMessageRequested(any(), any());

    connectedDeviceSppDelegateBinder.disconnect(testConnection);
    verify(mockRemoteCallback, never()).onDisconnectRequested(any());

    connectedDeviceSppDelegateBinder.cancelConnectionAttempt(TEST_PENDING_CONNECTION);
    verify(mockRemoteCallback, never()).onCancelConnectionAttemptRequested(anyInt());
  }

  @Test
  public void notifyConnected() throws Exception {
    connectedDeviceSppDelegateBinder
        .connectAsServer(TEST_UUID, TEST_IS_SECURE)
        .setOnConnectedListener(mockOnConnectedListener);
    connectedDeviceSppDelegateBinder.notifyConnected(
        TEST_PENDING_CONNECTION_ID, TEST_BLUETOOTH_DEVICE, TEST_BLUETOOTH_DEVICE.getName());
    verify(mockOnConnectedListener)
        .onConnected(
            TEST_UUID, TEST_BLUETOOTH_DEVICE, TEST_IS_SECURE, TEST_BLUETOOTH_DEVICE.getName());
  }

  @Test
  public void notifyError() {
    connectedDeviceSppDelegateBinder.notifyError(testConnection);
    verify(mockOnErrorListener).onError(testConnection);
  }

  @Test
  public void notifyConnectAttemptFailed() throws Exception {
    connectedDeviceSppDelegateBinder
        .connectAsServer(TEST_UUID, TEST_IS_SECURE)
        .setOnConnectionErrorListener(mockOnConnectionErrorListener);
    connectedDeviceSppDelegateBinder.notifyConnectAttemptFailed(TEST_PENDING_CONNECTION_ID);
    verify(mockOnConnectionErrorListener).onConnectionError();
  }

  @Test
  public void unregisterConnectionCallback() {
    testPendingConnection.setOnConnectedListener(null);
    testPendingConnection.notifyConnected(TEST_BLUETOOTH_DEVICE, TEST_BLUETOOTH_DEVICE.getName());
    verifyZeroInteractions(mockOnConnectedListener);
  }

  @Test
  public void unregisterConnectionErrorCallback() {
    testPendingConnection.setOnConnectionErrorListener(null);
    testPendingConnection.notifyConnectionError();
    verifyZeroInteractions(mockOnConnectionErrorListener);
  }

  @Test
  public void notifyMessageReceived() {
    connectedDeviceSppDelegateBinder.notifyMessageReceived(testConnection, TEST_MESSAGE);
    verify(mockOnMessageReceivedListener).onMessageReceived(TEST_MESSAGE);
  }

  @Test
  public void clearOnMessageReceivedListener() {
    connectedDeviceSppDelegateBinder.clearOnMessageReceivedListener(testConnection);
    connectedDeviceSppDelegateBinder.notifyMessageReceived(testConnection, TEST_MESSAGE);
    verifyZeroInteractions(mockOnErrorListener);
  }

  @Test
  public void notifyMessageReceived_multipleConnections() {
    byte[] testMessage2 = "testMessage2".getBytes(UTF_8);
    Connection testConnection2 =
        new Connection(
            new ParcelUuid(UUID.randomUUID()),
            BluetoothAdapter.getDefaultAdapter().getRemoteDevice("55:44:33:22:11:00"),
            false,
            "testName");
    OnMessageReceivedListener mockOnMessageReceivedListener2 =
        mock(OnMessageReceivedListener.class);
    connectedDeviceSppDelegateBinder.setOnMessageReceivedListener(
        testConnection2, mockOnMessageReceivedListener2);

    connectedDeviceSppDelegateBinder.notifyMessageReceived(testConnection, TEST_MESSAGE);
    verify(mockOnMessageReceivedListener).onMessageReceived(TEST_MESSAGE);
    verifyZeroInteractions(mockOnMessageReceivedListener2);

    connectedDeviceSppDelegateBinder.notifyMessageReceived(testConnection2, testMessage2);
    verify(mockOnMessageReceivedListener2).onMessageReceived(testMessage2);
    verifyNoMoreInteractions(mockOnMessageReceivedListener);
  }
}
