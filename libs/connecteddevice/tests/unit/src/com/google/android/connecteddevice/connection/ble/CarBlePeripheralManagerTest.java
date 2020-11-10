package com.google.android.connecteddevice.connection.ble;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.os.Handler;
import android.os.ParcelUuid;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.connection.AssociationCallback;
import com.google.android.connecteddevice.connection.AssociationSecureChannel;
import com.google.android.connecteddevice.connection.SecureChannel;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.oob.OobConnectionManager;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage;
import com.google.android.connecteddevice.util.ByteUtils;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.shadows.ShadowLooper;

@RunWith(AndroidJUnit4.class)
public class CarBlePeripheralManagerTest {
  private static final UUID ASSOCIATION_SERVICE_UUID = UUID.randomUUID();
  private static final UUID RECONNECT_SERVICE_UUID = UUID.randomUUID();
  private static final UUID RECONNECT_DATA_UUID = UUID.randomUUID();
  private static final UUID ADVERTISE_DATA_CHARACTERISTIC_UUID = UUID.randomUUID();
  private static final UUID WRITE_UUID = UUID.randomUUID();
  private static final UUID READ_UUID = UUID.randomUUID();
  private static final int DEVICE_NAME_LENGTH_LIMIT = 8;
  private static final String TEST_REMOTE_DEVICE_ADDRESS = "00:11:22:33:AA:BB";
  private static final UUID TEST_REMOTE_DEVICE_ID = UUID.randomUUID();
  private static final String TEST_VERIFICATION_CODE = "000000";
  private static final String TEST_ENCRYPTED_VERIFICATION_CODE = "12345";
  private static final Duration RECONNECT_ADVERTISEMENT_DURATION = Duration.ofMillis(2);
  private static final int DEFAULT_MTU_SIZE = 23;
  private static final boolean COMPRESSION_ENABLED = true;

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private BlePeripheralManager mockBlePeripheralManager;
  @Mock private ConnectedDeviceStorage mockConnectedDeviceStorage;
  @Mock private OobConnectionManager mockOobConnectionManager;
  @Mock private AssociationCallback mockAssociationCallback;
  private CarBlePeripheralManager carBlePeripheralManager;

  @Before
  public void setUp() throws Exception {
    carBlePeripheralManager =
        new CarBlePeripheralManager(
            mockBlePeripheralManager,
            mockConnectedDeviceStorage,
            ASSOCIATION_SERVICE_UUID,
            RECONNECT_SERVICE_UUID,
            RECONNECT_DATA_UUID,
            ADVERTISE_DATA_CHARACTERISTIC_UUID,
            WRITE_UUID,
            READ_UUID,
            RECONNECT_ADVERTISEMENT_DURATION,
            DEFAULT_MTU_SIZE,
            COMPRESSION_ENABLED);

    when(mockOobConnectionManager.encryptVerificationCode(TEST_VERIFICATION_CODE.getBytes(UTF_8)))
        .thenReturn(TEST_ENCRYPTED_VERIFICATION_CODE.getBytes(UTF_8));
    when(mockOobConnectionManager.decryptVerificationCode(
            TEST_ENCRYPTED_VERIFICATION_CODE.getBytes(UTF_8)))
        .thenReturn(TEST_VERIFICATION_CODE.getBytes(UTF_8));

    carBlePeripheralManager.start();
  }

  @After
  public void tearDown() {
    if (carBlePeripheralManager != null) {
      carBlePeripheralManager.stop();
    }
  }

  @Test
  public void testStartAssociationAdvertisingSuccess() {
    String testDeviceName = getNameForAssociation();
    startAssociation(mockAssociationCallback, testDeviceName);
    ArgumentCaptor<AdvertiseData> advertisementDataCaptor =
        ArgumentCaptor.forClass(AdvertiseData.class);
    ArgumentCaptor<AdvertiseData> scanResponseDataCaptor =
        ArgumentCaptor.forClass(AdvertiseData.class);
    verify(mockBlePeripheralManager)
        .startAdvertising(
            any(), advertisementDataCaptor.capture(), scanResponseDataCaptor.capture(), any());
    AdvertiseData advertisementData = advertisementDataCaptor.getValue();
    ParcelUuid serviceUuid = new ParcelUuid(ASSOCIATION_SERVICE_UUID);
    assertThat(advertisementData.getServiceUuids()).contains(serviceUuid);
    AdvertiseData scanResponseData = scanResponseDataCaptor.getValue();
    assertThat(scanResponseData.getIncludeDeviceName()).isFalse();
    ParcelUuid dataUuid = new ParcelUuid(RECONNECT_DATA_UUID);
    assertThat(scanResponseData.getServiceData().get(dataUuid))
        .isEqualTo(testDeviceName.getBytes(UTF_8));
  }

  @Test
  public void testStartAssociationAdvertisingFailure() {
    startAssociation(mockAssociationCallback, getNameForAssociation());
    ArgumentCaptor<AdvertiseCallback> callbackCaptor =
        ArgumentCaptor.forClass(AdvertiseCallback.class);
    verify(mockBlePeripheralManager)
        .startAdvertising(any(), any(), any(), callbackCaptor.capture());
    AdvertiseCallback advertiseCallback = callbackCaptor.getValue();
    int testErrorCode = 2;
    advertiseCallback.onStartFailure(testErrorCode);
    verify(mockAssociationCallback).onAssociationStartFailure();
  }

  @Test
  public void testNotifyAssociationSuccess() {
    String testDeviceName = getNameForAssociation();
    startAssociation(mockAssociationCallback, testDeviceName);
    ArgumentCaptor<AdvertiseCallback> callbackCaptor =
        ArgumentCaptor.forClass(AdvertiseCallback.class);
    verify(mockBlePeripheralManager)
        .startAdvertising(any(), any(), any(), callbackCaptor.capture());
    AdvertiseCallback advertiseCallback = callbackCaptor.getValue();
    AdvertiseSettings settings = new AdvertiseSettings.Builder().build();
    advertiseCallback.onStartSuccess(settings);
    verify(mockAssociationCallback).onAssociationStartSuccess(eq(testDeviceName));
  }

  @Test
  public void testShowVerificationCode() {
    AssociationSecureChannel channel = getChannelForAssociation(mockAssociationCallback);
    channel.getShowVerificationCodeListener().showVerificationCode(TEST_VERIFICATION_CODE);
    verify(mockAssociationCallback).onVerificationCodeAvailable(eq(TEST_VERIFICATION_CODE));
  }

  @Test
  public void testAssociationSuccess() {
    SecureChannel channel = getChannelForAssociation(mockAssociationCallback);
    SecureChannel.Callback channelCallback = channel.getCallback();
    assertThat(channelCallback).isNotNull();
    channelCallback.onDeviceIdReceived(TEST_REMOTE_DEVICE_ID.toString());
    channelCallback.onSecureChannelEstablished();
    ArgumentCaptor<AssociatedDevice> deviceCaptor = ArgumentCaptor.forClass(AssociatedDevice.class);
    verify(mockConnectedDeviceStorage).addAssociatedDeviceForActiveUser(deviceCaptor.capture());
    AssociatedDevice device = deviceCaptor.getValue();
    assertThat(device.getDeviceId()).isEqualTo(TEST_REMOTE_DEVICE_ID.toString());
    verify(mockAssociationCallback).onAssociationCompleted(eq(TEST_REMOTE_DEVICE_ID.toString()));
  }

  @Test
  public void testAssociationFailure_channelError() {
    SecureChannel channel = getChannelForAssociation(mockAssociationCallback);
    SecureChannel.Callback channelCallback = channel.getCallback();
    int testErrorCode = 1;
    assertThat(channelCallback).isNotNull();
    channelCallback.onDeviceIdReceived(TEST_REMOTE_DEVICE_ID.toString());
    channelCallback.onEstablishSecureChannelFailure(testErrorCode);
    verify(mockAssociationCallback).onAssociationError(eq(testErrorCode));
  }

  @Test
  public void connectToDevice_embedAdvertiseDataInCharacteristic() {
    carBlePeripheralManager.setTimeoutHandler(new Handler());
    when(mockConnectedDeviceStorage.hashWithChallengeSecret(any(), any()))
        .thenReturn(ByteUtils.randomBytes(32));
    carBlePeripheralManager.connectToDevice(UUID.randomUUID());

    ArgumentCaptor<AdvertiseData> advertiseDataCaptor =
        ArgumentCaptor.forClass(AdvertiseData.class);
    ArgumentCaptor<BluetoothGattService> gattServiceCaptor =
        ArgumentCaptor.forClass(BluetoothGattService.class);
    verify(mockBlePeripheralManager)
        .startAdvertising(gattServiceCaptor.capture(), advertiseDataCaptor.capture(), any(), any());
    assertThat(
            Arrays.equals(
                gattServiceCaptor
                    .getValue()
                    .getCharacteristic(ADVERTISE_DATA_CHARACTERISTIC_UUID)
                    .getValue(),
                advertiseDataCaptor.getValue().getServiceData().values().iterator().next()))
        .isTrue();
  }

  @Test
  public void connectToDevice_stopsAdvertisingAfterTimeout() {
    carBlePeripheralManager.setTimeoutHandler(new Handler());
    when(mockConnectedDeviceStorage.hashWithChallengeSecret(any(), any()))
        .thenReturn(ByteUtils.randomBytes(32));
    carBlePeripheralManager.connectToDevice(UUID.randomUUID());
    ArgumentCaptor<AdvertiseCallback> callbackCaptor =
        ArgumentCaptor.forClass(AdvertiseCallback.class);
    verify(mockBlePeripheralManager)
        .startAdvertising(any(), any(), any(), callbackCaptor.capture());
    callbackCaptor.getValue().onStartSuccess(null);
    // Simulate the timeout.
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    verify(mockBlePeripheralManager).stopAdvertising(any(AdvertiseCallback.class));
  }

  @Test
  public void disconnectDevice_stopsAdvertisingForPendingReconnect() {
    when(mockConnectedDeviceStorage.hashWithChallengeSecret(any(), any()))
        .thenReturn(ByteUtils.randomBytes(32));
    UUID deviceId = UUID.randomUUID();
    carBlePeripheralManager.connectToDevice(deviceId);
    reset(mockBlePeripheralManager);
    carBlePeripheralManager.disconnectDevice(deviceId.toString());
    verify(mockBlePeripheralManager).cleanup();
  }

  private AssociationSecureChannel getChannelForAssociation(AssociationCallback callback) {
    BlePeripheralManager.Callback bleManagerCallback =
        startAssociation(callback, getNameForAssociation());
    BluetoothDevice bluetoothDevice =
        BluetoothAdapter.getDefaultAdapter().getRemoteDevice(TEST_REMOTE_DEVICE_ADDRESS);
    bleManagerCallback.onRemoteDeviceConnected(bluetoothDevice);
    return (AssociationSecureChannel) carBlePeripheralManager.getConnectedDeviceChannel();
  }

  private BlePeripheralManager.Callback startAssociation(
      AssociationCallback callback, String deviceName) {
    ArgumentCaptor<BlePeripheralManager.Callback> callbackCaptor =
        ArgumentCaptor.forClass(BlePeripheralManager.Callback.class);
    carBlePeripheralManager.startAssociation(deviceName, callback);
    verify(mockBlePeripheralManager).registerCallback(callbackCaptor.capture());
    return callbackCaptor.getValue();
  }

  private static String getNameForAssociation() {
    return ByteUtils.generateRandomNumberString(DEVICE_NAME_LENGTH_LIMIT);
  }
}
