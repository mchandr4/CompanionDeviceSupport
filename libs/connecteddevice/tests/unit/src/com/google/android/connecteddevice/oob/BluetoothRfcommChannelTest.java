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

package com.google.android.connecteddevice.oob;

import static com.google.android.connecteddevice.model.OobEligibleDevice.OOB_TYPE_BLUETOOTH;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.RemoteException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.model.OobEligibleDevice;
import com.google.android.connecteddevice.transport.BluetoothDeviceProvider;
import com.google.android.connecteddevice.transport.ConnectionProtocol;
import com.google.android.connecteddevice.transport.ProtocolDevice;
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder;
import com.google.android.connecteddevice.transport.spp.PendingConnection;
import com.google.android.connecteddevice.transport.spp.PendingSentMessage;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Bytes;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.shadows.ShadowLooper;

@RunWith(AndroidJUnit4.class)
public class BluetoothRfcommChannelTest {
  private static final OobEligibleDevice OOB_ELIGIBLE_DEVICE =
      new OobEligibleDevice("00:11:22:33:AA:BB", OOB_TYPE_BLUETOOTH);
  private static final BluetoothAdapter BLUETOOTH_ADAPTER = BluetoothAdapter.getDefaultAdapter();
  private static final byte[] TEST_MESSAGE = "someData".getBytes(UTF_8);
  private static final BluetoothDevice TEST_BLUETOOTH_DEVICE =
      BLUETOOTH_ADAPTER.getRemoteDevice(OOB_ELIGIBLE_DEVICE.getDeviceAddress());

  private BluetoothRfcommChannel bluetoothRfcommChannel;
  @Mock private OobChannel.Callback mockCallback;
  @Mock private TestConnectionProtocol mockValidProtocol;
  @Mock private ConnectionProtocol mockInvalidProtocol;
  @Mock private ConnectedDeviceSppDelegateBinder mockSppDelegateBinder;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    bluetoothRfcommChannel = new BluetoothRfcommChannel(mockSppDelegateBinder);
  }

  @Test
  public void completeOobExchange_success() throws Exception {
    PendingSentMessage pendingSentMessage = new PendingSentMessage();
    when(mockSppDelegateBinder.sendMessage(any(), any())).thenReturn(pendingSentMessage);
    PendingConnection connection = establishConnection();

    OobConnectionManager oobConnectionManager = new OobConnectionManager();
    oobConnectionManager.startOobExchange(bluetoothRfcommChannel);

    ArgumentCaptor<byte[]> messageArgumentCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(mockSppDelegateBinder).sendMessage(any(), messageArgumentCaptor.capture());
    byte[] oobData = messageArgumentCaptor.getValue();

    assertThat(oobData)
        .isEqualTo(
            Bytes.concat(
                oobConnectionManager.decryptionIv,
                oobConnectionManager.encryptionIv,
                oobConnectionManager.encryptionKey.getEncoded()));

    pendingSentMessage.notifyMessageSent();
    verify(mockSppDelegateBinder).disconnect(connection.toConnection(TEST_BLUETOOTH_DEVICE));
  }

  @Test
  public void completeOobExchange_pendingSentMessageIsNull_callOnFailed() throws Exception {
    establishConnection();

    new OobConnectionManager().startOobExchange(bluetoothRfcommChannel);

    verify(mockSppDelegateBinder).sendMessage(any(), any());
    verify(mockCallback).onOobExchangeFailure();
  }

  @Test
  public void completeOobExchange_createRfcommSocketFails_callOnFailed() throws Exception {
    doThrow(RemoteException.class)
        .when(mockSppDelegateBinder)
        .connectAsClient(any(), any(), anyBoolean());

    bluetoothRfcommChannel.completeOobDataExchange(
        OOB_ELIGIBLE_DEVICE, mockCallback, () -> ImmutableSet.of(TEST_BLUETOOTH_DEVICE));
    verify(mockCallback).onOobExchangeFailure();
  }

  @Test
  public void completeOobExchange_noBondedDevices_callOnFailed() throws Exception {
    bluetoothRfcommChannel.completeOobDataExchange(
        OOB_ELIGIBLE_DEVICE, mockCallback, ImmutableSet::of);

    verify(mockCallback).onOobExchangeFailure();
  }

  @Test
  public void completeOobExchange_bondedToTheWrongDevice_callOnFailed() throws Exception {
    BluetoothDevice otherBtDevice = BLUETOOTH_ADAPTER.getRemoteDevice("BB:AA:33:22:11:00");
    bluetoothRfcommChannel.completeOobDataExchange(
        OOB_ELIGIBLE_DEVICE, mockCallback, () -> ImmutableSet.of(otherBtDevice));

    verify(mockCallback).onOobExchangeFailure();
  }

  @Test
  public void completeOobExchange_timeout_cancelsConnectionAndCallsOnFailed() throws Exception {
    PendingConnection pendingConnection = requestConnection();

    // Simulate the timeout
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

    verify(mockSppDelegateBinder).cancelConnectionAttempt(pendingConnection);
    verify(mockCallback).onOobExchangeFailure();
  }

  @Test
  public void sendOobData_nullBluetoothDevice_callOnFailed() {
    bluetoothRfcommChannel.callback = mockCallback;

    bluetoothRfcommChannel.sendOobData(TEST_MESSAGE);
    verify(mockCallback).onOobExchangeFailure();
  }

  @Test
  public void sendOobData_sendMessageFails_callOnFailed() throws Exception {
    doThrow(RemoteException.class).when(mockSppDelegateBinder).sendMessage(any(), any());
    establishConnection();

    bluetoothRfcommChannel.sendOobData(TEST_MESSAGE);
    verify(mockCallback).onOobExchangeFailure();
  }

  @Test
  public void interrupt_disconnects() throws Exception {
    PendingConnection connection = establishConnection();
    bluetoothRfcommChannel.interrupt();
    verify(mockSppDelegateBinder).disconnect(connection.toConnection(TEST_BLUETOOTH_DEVICE));
  }

  @Test
  public void interrupt_preventsCallbacks() throws Exception {
    doAnswer(
            invocation -> {
              bluetoothRfcommChannel.interrupt();
              return invocation.callRealMethod();
            })
        .when(mockSppDelegateBinder)
        .connectAsClient(any(), any(), anyBoolean());

    bluetoothRfcommChannel.completeOobDataExchange(
        OOB_ELIGIBLE_DEVICE, mockCallback, () -> ImmutableSet.of(TEST_BLUETOOTH_DEVICE));

    verify(mockCallback, never()).onOobExchangeSuccess();
    verify(mockCallback, never()).onOobExchangeFailure();
  }

  @Test
  public void interrupt_cancelConnectionAttempt() throws Exception {
    PendingConnection connection = establishConnection();
    bluetoothRfcommChannel.interrupt();
    verify(mockSppDelegateBinder).disconnect(connection.toConnection(TEST_BLUETOOTH_DEVICE));
  }

  @Test
  public void completeOobDataExchange_supportedProtocol_returnTrue() {
    String protocolId = "testProtocolId";
    when(mockValidProtocol.getBluetoothDeviceById(protocolId)).thenReturn(TEST_BLUETOOTH_DEVICE);

    assertThat(
            bluetoothRfcommChannel.completeOobDataExchange(
                new ProtocolDevice(mockValidProtocol, protocolId), mockCallback))
        .isTrue();
  }

  @Test
  public void completeOobDataExchange_unsupportedProtocol_returnFalse() {
    String protocolId = "testProtocolId";

    assertThat(
            bluetoothRfcommChannel.completeOobDataExchange(
                new ProtocolDevice(mockInvalidProtocol, protocolId), mockCallback))
        .isFalse();
  }

  private PendingConnection establishConnection() throws Exception {
    PendingConnection connection = requestConnection();
    connection.notifyConnected(TEST_BLUETOOTH_DEVICE, TEST_BLUETOOTH_DEVICE.getName());
    verify(mockCallback).onOobExchangeSuccess();

    return connection;
  }

  private PendingConnection requestConnection() throws Exception {
    ConnectionResultCaptor connectionCaptor = new ConnectionResultCaptor();
    doAnswer(connectionCaptor)
        .when(mockSppDelegateBinder)
        .connectAsClient(any(), any(), anyBoolean());

    bluetoothRfcommChannel.completeOobDataExchange(
        OOB_ELIGIBLE_DEVICE, mockCallback, () -> ImmutableSet.of(TEST_BLUETOOTH_DEVICE));
    verify(mockSppDelegateBinder).connectAsClient(any(), any(), anyBoolean());

    return connectionCaptor.getResult();
  }

  private static class ConnectionResultCaptor implements Answer<PendingConnection> {
    private PendingConnection result = null;

    public PendingConnection getResult() {
      return result;
    }

    @Override
    public PendingConnection answer(InvocationOnMock invocationOnMock) {
      UUID uuid = invocationOnMock.getArgument(0);
      boolean isSecure = invocationOnMock.getArgument(2);
      result = new PendingConnection(uuid, isSecure);
      return result;
    }
  }

  private static class TestConnectionProtocol extends ConnectionProtocol
      implements BluetoothDeviceProvider {

    @Override
    public BluetoothDevice getBluetoothDeviceById(String protocolId) {
      return null;
    }

    @Override
    public boolean isDeviceVerificationRequired() {
      return false;
    }

    @Override
    public void startAssociationDiscovery(String name, DiscoveryCallback callback) {}

    @Override
    public void startConnectionDiscovery(
        UUID id, ConnectChallenge challenge, DiscoveryCallback callback) {}

    @Override
    public void stopAssociationDiscovery() {}

    @Override
    public void stopConnectionDiscovery(UUID id) {}

    @Override
    public void sendData(String protocolId, byte[] data, DataSendCallback callback) {}

    @Override
    public void disconnectDevice(String protocolId) {}

    @Override
    public int getMaxWriteSize(String protocolId) {
      return 0;
    }
  }
}
