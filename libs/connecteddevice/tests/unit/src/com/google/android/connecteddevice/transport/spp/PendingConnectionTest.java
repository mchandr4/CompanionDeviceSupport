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
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.transport.spp.PendingConnection.OnConnectedListener;
import com.google.android.connecteddevice.transport.spp.PendingConnection.OnConnectionErrorListener;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public class PendingConnectionTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private final UUID testServiceUuid = UUID.randomUUID();
  private final BluetoothDevice testRemoteDevice =
      ApplicationProvider.getApplicationContext()
          .getSystemService(BluetoothManager.class)
          .getAdapter()
          .getRemoteDevice("00:11:22:33:44:55");
  private static final boolean TEST_IS_SECURE = true;

  @Mock private OnConnectedListener mockOnConnectedListener;
  @Mock private OnConnectionErrorListener mockOnConnectionErrorListener;

  @Test
  public void testNotifyConnected() {
    PendingConnection pendingConnection =
        new PendingConnection(testServiceUuid, testRemoteDevice, TEST_IS_SECURE);
    pendingConnection.setOnConnectedListener(mockOnConnectedListener);
    pendingConnection.notifyConnected(testRemoteDevice, testRemoteDevice.getName());

    verify(mockOnConnectedListener)
        .onConnected(testServiceUuid, testRemoteDevice, true, testRemoteDevice.getName());
  }

  @Test
  public void testNotifyConnectionError() {
    PendingConnection pendingConnection =
        new PendingConnection(testServiceUuid, testRemoteDevice, TEST_IS_SECURE);
    pendingConnection.setOnConnectionErrorListener(mockOnConnectionErrorListener);
    pendingConnection.notifyConnectionError();

    verify(mockOnConnectionErrorListener).onConnectionError();
  }

  @Test
  public void testGetId() {
    PendingConnection pendingConnection =
        new PendingConnection(testServiceUuid, testRemoteDevice, TEST_IS_SECURE);
    PendingConnection pendingConnectionSame =
        new PendingConnection(testServiceUuid, testRemoteDevice, TEST_IS_SECURE);

    assertThat(pendingConnection.getId()).isEqualTo(pendingConnectionSame.getId());

    PendingConnection pendingConnectionDifferent =
        new PendingConnection(
            UUID.randomUUID(),
            BluetoothAdapter.getDefaultAdapter().getRemoteDevice("55:44:33:22:11:00"),
            false);
    assertThat(pendingConnection.getId()).isNotEqualTo(pendingConnectionDifferent.getId());
  }

  @Test
  public void testGetId_nullBluetoothDevice() {
    PendingConnection pendingClientConnection =
        new PendingConnection(testServiceUuid, testRemoteDevice, TEST_IS_SECURE);
    PendingConnection pendingServerConnection =
        new PendingConnection(testServiceUuid, TEST_IS_SECURE);

    assertThat(pendingClientConnection.getId()).isNotEqualTo(pendingServerConnection.getId());
  }
}
