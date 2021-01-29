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
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.transport.spp.SppManager.ConnectionState;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.shadow.api.Shadow;

@RunWith(AndroidJUnit4.class)
public class SppManagerTest {
  private static final UUID TEST_SERVICE_UUID = UUID.randomUUID();
  private static final boolean IS_SECURE_RFCOMM_CHANNEL = true;
  private final byte[] testData = "testData".getBytes(UTF_8);
  private final BluetoothDevice testBluetoothDevice =
      BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55");
  private SppManager sppManager;
  private final Executor callbackExecutor = directExecutor();
  private final BluetoothSocket shadowBluetoothSocket = Shadow.newInstanceOf(BluetoothSocket.class);

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private SppManager.ConnectionCallback mockConnectionCallback;
  @Mock private SppManager.OnMessageReceivedListener mockListener;

  @Before
  public void setUp() {
    sppManager = new SppManager(IS_SECURE_RFCOMM_CHANNEL, callbackExecutor);
    sppManager.connectedSocket = shadowBluetoothSocket;
    sppManager.registerCallback(mockConnectionCallback, callbackExecutor);
    sppManager.addOnMessageReceivedListener(mockListener, callbackExecutor);
  }

  @After
  public void cleanUp() {
    sppManager.cleanup();
  }

  @Test
  public void testStartListen_initiateAcceptTask() {
    sppManager.startListening(TEST_SERVICE_UUID);
    assertThat(sppManager.acceptTask).isNotNull();
  }

  @Test
  public void testWrite_writeToOutputStream() throws IOException {
    shadowBluetoothSocket.connect();
    sppManager.state = ConnectionState.CONNECTED;

    sppManager.write(testData, new PendingSentMessage());

    byte[] byteArray = new byte[1024];
    int len = shadowOf(shadowBluetoothSocket).getOutputStreamSink().read(byteArray);
    assertThat(Arrays.copyOf(byteArray, len)).isEqualTo(SppManager.wrapWithArrayLength(testData));
  }

  @Test
  public void testReadMessageTaskCallback_onMessageReceived_callOnMessageReceivedListener()
      throws InterruptedException {
    sppManager.readMessageTaskCallback.onMessageReceived(testData);
    verify(mockListener).onMessageReceived(any(), eq(testData));
  }

  @Test
  public void testReadMessageTaskCallback_onMessageReadError_disconnectRemoteDevice()
      throws InterruptedException {
    sppManager.readMessageTaskCallback.onMessageReadError();
    verify(mockConnectionCallback).onRemoteDeviceDisconnected(any());
  }

  @Test
  public void testConnect_initConnectTask() {
    sppManager.connect(testBluetoothDevice, TEST_SERVICE_UUID);

    assertThat(sppManager.connectTask).isNotNull();
  }

  @Test
  public void testConnectTaskCallback_onConnectionSuccess_informConnectionCallback() {
    sppManager.connectTaskCallback.onConnectionSuccess(shadowBluetoothSocket);

    verify(mockConnectionCallback).onRemoteDeviceConnected(any());
  }

  @Test
  public void testConnectTaskCallback_onConnectionAttemptFailed_clearConnectTask() {
    sppManager.connectTaskCallback.onConnectionAttemptFailed();

    assertThat(sppManager.connectTask).isNull();
  }

  @Test
  public void testCleanUp_disconnectRemoteDevice() throws IOException {
    shadowBluetoothSocket.connect();
    assertThat(shadowBluetoothSocket.isConnected()).isTrue();

    sppManager.cleanup();

    assertThat(shadowBluetoothSocket.isConnected()).isFalse();
    verify(mockConnectionCallback).onRemoteDeviceDisconnected(any());
  }

  @Test
  public void testCleanup_clearConnectTask() {
    sppManager.connect(testBluetoothDevice, TEST_SERVICE_UUID);

    sppManager.cleanup();

    assertThat(sppManager.connectTask).isNull();
  }

  @Test
  public void testCleanup_clearAcceptTask() {
    sppManager.startListening(TEST_SERVICE_UUID);

    sppManager.cleanup();

    assertThat(sppManager.acceptTask).isNull();
  }
}
