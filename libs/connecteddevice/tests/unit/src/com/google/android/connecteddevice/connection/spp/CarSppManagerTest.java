package com.google.android.connecteddevice.connection.spp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.RemoteException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.connection.AssociationCallback;
import com.google.android.connecteddevice.connection.AssociationSecureChannel;
import com.google.android.connecteddevice.connection.CarBluetoothManager;
import com.google.android.connecteddevice.connection.ReconnectSecureChannel;
import com.google.android.connecteddevice.connection.SecureChannel;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage;
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder;
import com.google.android.connecteddevice.transport.spp.PendingConnection;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

@RunWith(AndroidJUnit4.class)
public class CarSppManagerTest {
  private static final String TEST_REMOTE_DEVICE_ADDRESS = "00:11:22:33:AA:BB";
  private static final UUID TEST_REMOTE_DEVICE_ID = UUID.randomUUID();
  private static final UUID TEST_SERVICE_UUID_1 = UUID.randomUUID();
  private static final boolean IS_SECURE = true;
  private static final String TEST_VERIFICATION_CODE = "000000";
  private static final int MAX_PACKET_SIZE = 700;
  private static final boolean COMPRESSION_ENABLED = true;
  private static final BluetoothDevice TEST_BLUETOOTH_DEVICE =
      BluetoothAdapter.getDefaultAdapter().getRemoteDevice(TEST_REMOTE_DEVICE_ADDRESS);

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  private final Executor callbackExecutor = directExecutor();

  @Mock private CarBluetoothManager.Callback mockCallback;
  @Mock private AssociationCallback mockAssociationCallback;
  @Mock private ConnectedDeviceSppDelegateBinder mockSppBinder;
  @Mock private ConnectedDeviceStorage mockStorage;
  private CarSppManager carSppManager;
  private ConnectionResultCaptor connectionResultCaptor;

  @Before
  public void setUp() throws Exception {
    connectionResultCaptor = new ConnectionResultCaptor();
    doAnswer(connectionResultCaptor).when(mockSppBinder).connectAsServer(any(), eq(true));
    carSppManager =
        new CarSppManager(
            mockSppBinder, mockStorage, TEST_SERVICE_UUID_1, MAX_PACKET_SIZE, COMPRESSION_ENABLED);
    carSppManager.registerCallback(mockCallback, callbackExecutor);
  }

  @After
  public void tearDown() {
    if (carSppManager != null) {
      carSppManager.stop();
    }
  }

  @Test
  public void testStartAssociationSuccess() throws RemoteException {
    carSppManager.initiateConnectionToDevice(TEST_REMOTE_DEVICE_ID);

    carSppManager.startAssociation(/* nameForAssociation= */ null, mockAssociationCallback);

    verify(mockSppBinder).unregisterConnectionCallback(TEST_REMOTE_DEVICE_ID);
    verify(mockSppBinder).connectAsServer(TEST_SERVICE_UUID_1, IS_SECURE);
    verify(mockAssociationCallback).onAssociationStartSuccess(/* deviceName= */ null);
  }

  @Test
  public void testStartAssociationFailure() throws RemoteException {
    when(mockSppBinder.connectAsServer(TEST_SERVICE_UUID_1, IS_SECURE)).thenReturn(null);

    carSppManager.startAssociation(/* nameForAssociation= */ null, mockAssociationCallback);

    verify(mockAssociationCallback).onAssociationStartFailure();
  }

  @Test
  public void testShowVerificationCode() {
    AssociationSecureChannel channel = getChannelForAssociation(mockAssociationCallback);

    channel.getShowVerificationCodeListener().showVerificationCode(TEST_VERIFICATION_CODE);

    verify(mockAssociationCallback).onVerificationCodeAvailable(TEST_VERIFICATION_CODE);
  }

  @Test
  public void testAssociationSuccess() {
    SecureChannel channel = getChannelForAssociation(mockAssociationCallback);
    SecureChannel.Callback channelCallback = channel.getCallback();

    assertThat(channelCallback).isNotNull();

    channelCallback.onDeviceIdReceived(TEST_REMOTE_DEVICE_ID.toString());
    channelCallback.onSecureChannelEstablished();

    ArgumentCaptor<AssociatedDevice> deviceCaptor = ArgumentCaptor.forClass(AssociatedDevice.class);
    verify(mockStorage).addAssociatedDeviceForActiveUser(deviceCaptor.capture());
    AssociatedDevice device = deviceCaptor.getValue();

    assertThat(device.getDeviceId()).isEqualTo(TEST_REMOTE_DEVICE_ID.toString());

    verify(mockAssociationCallback).onAssociationCompleted(TEST_REMOTE_DEVICE_ID.toString());
  }

  @Test
  public void testInitiateConnectionToDevice() throws RemoteException {
    carSppManager.initiateConnectionToDevice(TEST_SERVICE_UUID_1);

    verify(mockSppBinder).registerConnectionCallback(eq(TEST_SERVICE_UUID_1), any());
    verify(mockSppBinder).connectAsServer(TEST_SERVICE_UUID_1, IS_SECURE);
  }

  @Test
  public void testReset() throws RemoteException {
    carSppManager.startAssociation(/* nameForAssociation= */ null, mockAssociationCallback);
    PendingConnection connection = connectionResultCaptor.getResult();
    connection.notifyConnected(TEST_BLUETOOTH_DEVICE, TEST_BLUETOOTH_DEVICE.getName());

    carSppManager.reset();

    verify(mockSppBinder).disconnect(connection.toConnection(TEST_BLUETOOTH_DEVICE));
  }

  @Test
  public void testResetBeforeConnection() throws RemoteException {
    carSppManager.startAssociation(/* nameForAssociation= */ null, mockAssociationCallback);

    carSppManager.reset();

    verify(mockSppBinder).cancelConnectionAttempt(any());
  }

  @Test
  public void testAssociationCallbackOnErrorSucceed() {
    SecureChannel channel = getChannelForAssociation(mockAssociationCallback);
    SecureChannel.Callback channelCallback = channel.getCallback();
    channelCallback.onDeviceIdReceived(TEST_REMOTE_DEVICE_ID.toString());
    channelCallback.onSecureChannelEstablished();

    carSppManager.reconnectOnErrorListener.onError(
        connectionResultCaptor.getResult().toConnection(TEST_BLUETOOTH_DEVICE));

    verify(mockCallback).onDeviceDisconnected(TEST_REMOTE_DEVICE_ID.toString());
    assertThat(carSppManager.currentConnection).isNull();
  }

  @Test
  public void testAssociationCallbackOnErrorFailed() {
    SecureChannel channel = getChannelForAssociation(mockAssociationCallback);
    SecureChannel.Callback channelCallback = channel.getCallback();
    channelCallback.onDeviceIdReceived(TEST_REMOTE_DEVICE_ID.toString());
    channelCallback.onSecureChannelEstablished();

    connectionResultCaptor.getResult().notifyConnectionError();

    verify(mockCallback, never()).onDeviceDisconnected(TEST_REMOTE_DEVICE_ID.toString());
    assertThat(carSppManager.currentConnection).isNotNull();
  }

  @Test
  public void testAssociationCallbackOnConnected() {
    carSppManager.startAssociation(/* nameForAssociation= */ null, mockAssociationCallback);
    PendingConnection connection = connectionResultCaptor.getResult();
    connection.notifyConnected(TEST_BLUETOOTH_DEVICE, TEST_BLUETOOTH_DEVICE.getName());

    assertThat(carSppManager.currentConnection)
        .isEqualTo(connection.toConnection(TEST_BLUETOOTH_DEVICE));
  }

  @Test
  public void testAssociationCallbackOnConnectAttemptFailed() {
    carSppManager.startAssociation(/* nameForAssociation= */ null, mockAssociationCallback);
    connectionResultCaptor.getResult().notifyConnectionError();

    verify(mockCallback, never()).onDeviceDisconnected(any());
  }

  @Test
  public void testReconnectCallbackOnConnected() {
    carSppManager.startAssociation(/* nameForAssociation= */ null, mockAssociationCallback);
    PendingConnection connection = connectionResultCaptor.getResult();
    connection.notifyConnected(TEST_BLUETOOTH_DEVICE, TEST_BLUETOOTH_DEVICE.getName());

    assertThat(carSppManager.currentConnection)
        .isEqualTo(connection.toConnection(TEST_BLUETOOTH_DEVICE));
  }

  @Test
  public void testReconnectCallbackOnErrorSucceed() {
    SecureChannel channel = getChannelForReconnect();
    SecureChannel.Callback channelCallback = channel.getCallback();
    channelCallback.onSecureChannelEstablished();

    carSppManager.reconnectOnErrorListener.onError(
        connectionResultCaptor.getResult().toConnection(TEST_BLUETOOTH_DEVICE));

    verify(mockCallback).onDeviceDisconnected(TEST_REMOTE_DEVICE_ID.toString());
    assertThat(carSppManager.currentConnection).isNull();
  }

  @Test
  public void testReconnectCallbackOnErrorFailed() {
    SecureChannel channel = getChannelForReconnect();
    SecureChannel.Callback channelCallback = channel.getCallback();
    channelCallback.onSecureChannelEstablished();

    connectionResultCaptor.getResult().notifyConnectionError();

    verify(mockCallback, never()).onDeviceDisconnected(TEST_REMOTE_DEVICE_ID.toString());
    assertThat(carSppManager.currentConnection).isNotNull();
  }

  @Test
  public void testReconnectCallbackOnConnectAttemptFailed() {
    carSppManager.startAssociation(/* nameForAssociation= */ null, mockAssociationCallback);
    connectionResultCaptor.getResult().notifyConnectionError();

    verify(mockCallback, never()).onDeviceDisconnected(any());
  }

  private AssociationSecureChannel getChannelForAssociation(AssociationCallback callback) {
    carSppManager.startAssociation(/* nameForAssociation= */ null, callback);

    connectionResultCaptor
        .getResult()
        .notifyConnected(TEST_BLUETOOTH_DEVICE, TEST_BLUETOOTH_DEVICE.getName());

    return (AssociationSecureChannel) carSppManager.getConnectedDeviceChannel();
  }

  private ReconnectSecureChannel getChannelForReconnect() {
    carSppManager.initiateConnectionToDevice(TEST_REMOTE_DEVICE_ID);

    connectionResultCaptor
        .getResult()
        .notifyConnected(TEST_BLUETOOTH_DEVICE, TEST_BLUETOOTH_DEVICE.getName());

    return (ReconnectSecureChannel) carSppManager.getConnectedDeviceChannel();
  }

  private static class ConnectionResultCaptor implements Answer<PendingConnection> {
    private PendingConnection result;

    public PendingConnection getResult() {
      return result;
    }

    @Override
    public PendingConnection answer(InvocationOnMock invocationOnMock) {
      UUID uuid = invocationOnMock.getArgument(0);
      boolean isSecure = invocationOnMock.getArgument(1);
      result = new PendingConnection(uuid, isSecure);
      return result;
    }
  }
}
