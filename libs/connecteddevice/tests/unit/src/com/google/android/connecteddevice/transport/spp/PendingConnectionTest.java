package com.google.android.connecteddevice.transport.spp;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
      BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55");
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
