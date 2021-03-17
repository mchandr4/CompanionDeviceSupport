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

package com.google.android.connecteddevice.connection.spp;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.os.ParcelUuid;
import android.os.RemoteException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.connection.DeviceMessageStream.DataReceivedListener;
import com.google.android.connecteddevice.connection.DeviceMessageStream.MessageReceivedErrorListener;
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder;
import com.google.android.connecteddevice.transport.spp.Connection;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public class SppDeviceMessageStreamTest {
  private static final int MAX_WRITE_SIZE = 700;
  private static final byte[] TEST_DATA = "testData".getBytes(UTF_8);
  private static final UUID TEST_UUID = UUID.randomUUID();
  private static final boolean IS_SECURE = true;

  private static final Connection TEST_CONNECTION =
      new Connection(
          new ParcelUuid(TEST_UUID),
          BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55"),
          IS_SECURE,
          "testName");
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private ConnectedDeviceSppDelegateBinder mockSppBinder;
  @Mock private MessageReceivedErrorListener mockErrorListener;
  @Mock private DataReceivedListener mockListener;

  private SppDeviceMessageStream sppDeviceMessageStream;

  @Before
  public void setUp() {
    sppDeviceMessageStream =
        spy(new SppDeviceMessageStream(mockSppBinder, TEST_CONNECTION, MAX_WRITE_SIZE));
    sppDeviceMessageStream.setMessageReceivedErrorListener(mockErrorListener);
    sppDeviceMessageStream.setDataReceivedListener(mockListener);
  }

  @Test
  public void testSend() throws RemoteException {
    sppDeviceMessageStream.send(TEST_DATA);
    verify(mockSppBinder).sendMessage(TEST_CONNECTION, TEST_DATA);
    verify(sppDeviceMessageStream).sendCompleted();
  }

  @Test
  public void testOnMessageReceived_connectionIsNotResolved() {
    verify(mockSppBinder).setOnMessageReceivedListener(eq(TEST_CONNECTION), any());

    sppDeviceMessageStream.onMessageReceived(TEST_DATA);

    verify(mockListener).onDataReceived(TEST_DATA);
  }

  @Test
  public void testOnMessageReceived_connectionIsResolved() {
    sppDeviceMessageStream.setConnectionResolved(true);
    verify(mockSppBinder).setOnMessageReceivedListener(eq(TEST_CONNECTION), any());

    sppDeviceMessageStream.onMessageReceived(TEST_DATA);

    verify(mockErrorListener).onMessageReceivedError(any());
  }
}
