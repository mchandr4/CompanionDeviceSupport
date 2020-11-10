package com.google.android.connecteddevice.transport.spp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
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
      BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55");
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
  public void testRun_connectException_onConnectionAttemptFailed() throws IOException {
    connectTask.socket.close();

    connectTask.run();

    verify(mockCallback).onConnectionAttemptFailed();
  }

  @Test
  public void testCancel_runAfterCancel_doNotInformCallback() throws IOException {
    connectTask.cancel();

    connectTask.socket.close();
    connectTask.run();

    verify(mockCallback, never()).onConnectionAttemptFailed();
  }

  @Test
  public void testCancel_connectAfterCancel_doNotInformCallback() {
    connectTask.cancel();

    connectTask.run();

    verify(mockCallback, never()).onConnectionSuccess(connectTask.socket);
  }
}
