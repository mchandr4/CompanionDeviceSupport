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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
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
public class ConnectTaskTest {
  private static final UUID TEST_SERVICE_UUID = UUID.randomUUID();

  private final BluetoothDevice testBluetoothDevice =
      ApplicationProvider.getApplicationContext()
          .getSystemService(BluetoothManager.class)
          .getAdapter()
          .getRemoteDevice("00:11:22:33:44:55");
  private final Executor callbackExecutor = directExecutor();

  private ConnectTask connectTask;

  @Mock private ConnectTask.Callback mockCallback;

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Before
  public void setUp() {
    connectTask =
        new ConnectTask(
            testBluetoothDevice, true, TEST_SERVICE_UUID, mockCallback, callbackExecutor);
  }

  @Test
  public void testRun_connectionSucceed_informCallback() {
    connectTask.run();

    assertThat(connectTask.socket).isNotNull();
    verify(mockCallback).onConnectionSuccess(connectTask.socket);
  }

  @Test
  public void testCancel_closeSocket_socketDisconnected() {
    connectTask.run();
    assertThat(connectTask.socket.isConnected()).isTrue();

    connectTask.cancel();

    assertThat(connectTask.socket.isConnected()).isFalse();
  }

  @Test
  public void testRun_createSocketFailed_onConnectionAttemptFailed() {
    connectTask.socket = null;
    connectTask.run();

    verify(mockCallback).onConnectionAttemptFailed();
  }

  @Test
  public void testRun_connectAfterCancel_doNotInformCallback() {
    connectTask.cancel();

    connectTask.run();

    verify(mockCallback, never()).onConnectionSuccess(connectTask.socket);
  }
}
