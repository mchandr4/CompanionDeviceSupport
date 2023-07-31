package com.google.android.connecteddevice.transport.ble;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest.permission;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.transport.ble.testable.BluetoothGattServerHandler;
import com.google.android.connecteddevice.transport.ble.testable.BluetoothManagerHandler;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
public class OnDeviceBlePeripheralManagerTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  private Application application = ApplicationProvider.getApplicationContext();
  private BluetoothAdapter adapter;
  @Mock private AdvertiseCallback mockAdvertiseCallback;
  @Mock private BlePeripheralManager.Callback mockCallback;
  @Mock private BluetoothManagerHandler mockBluetoothManager;
  @Mock private BluetoothGattServerHandler mockGattServer;
  @Mock private BlePeripheralManager.OnCharacteristicReadListener mockOnCharacteristicReadListener;
  private OnDeviceBlePeripheralManager blePeripheralManager;
  private BluetoothDevice testBluetoothDevice;
  private BluetoothDevice unknownBluetoothDevice;

  @Before
  public void setUp() {
    Context context = ApplicationProvider.getApplicationContext();
    PackageManager packageManager = context.getPackageManager();
    adapter = ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
    blePeripheralManager = new OnDeviceBlePeripheralManager(context, mockBluetoothManager);
    shadowOf(adapter).setIsMultipleAdvertisementSupported(true);
    shadowOf(packageManager).setSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE, true);
    blePeripheralManager.registerCallback(mockCallback);
    testBluetoothDevice = adapter.getRemoteDevice("00:11:22:33:AA:BB");
    unknownBluetoothDevice = adapter.getRemoteDevice("99:11:22:33:AA:BB");
    when(mockBluetoothManager.getAdapter()).thenReturn(adapter);
    when(mockBluetoothManager.openGattServer(any())).thenReturn(mockGattServer);
    blePeripheralManager.addOnCharacteristicReadListener(mockOnCharacteristicReadListener);
  }

  @Test
  @Config(sdk = Build.VERSION_CODES.S)
  public void startAdvertising_androidS_doNotAdvertiseWithoutAdvertisePermission() {
    shadowOf(application).grantPermissions(permission.BLUETOOTH_CONNECT);
    UUID uuid = UUID.randomUUID();
    AdvertiseData advertiseData =
        new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(uuid)).build();
    AdvertiseData scanResponse =
        new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(uuid)).build();
    BluetoothGattService service =
        new BluetoothGattService(uuid, BluetoothGattService.SERVICE_TYPE_PRIMARY);
    blePeripheralManager.startAdvertising(
        service, advertiseData, scanResponse, mockAdvertiseCallback);
    verify(mockBluetoothManager, never()).openGattServer(any());
  }

  @Test
  @Config(sdk = Build.VERSION_CODES.R)
  public void startAdvertising_belowAndroidS_advertiseWithoutNearbyDevicePermission() {
    UUID uuid = UUID.randomUUID();
    AdvertiseData advertiseData =
        new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(uuid)).build();
    AdvertiseData scanResponse =
        new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(uuid)).build();
    BluetoothGattService service =
        new BluetoothGattService(uuid, BluetoothGattService.SERVICE_TYPE_PRIMARY);
    blePeripheralManager.startAdvertising(
        service, advertiseData, scanResponse, mockAdvertiseCallback);
    verify(mockBluetoothManager).openGattServer(any());
  }

  @Test
  public void onConnectionStateChange_invokeConnectedCallback() {
    BluetoothGattServerCallback callback = setupGattServer();

    callback.onConnectionStateChange(
        testBluetoothDevice, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);

    verify(mockCallback).onRemoteDeviceConnected(testBluetoothDevice);
  }

  @Test
  public void onConnectionStateChange_invokeDisconnectedCallback() {
    BluetoothGattServerCallback callback = setupGattServer();

    callback.onConnectionStateChange(
        testBluetoothDevice, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);
    callback.onConnectionStateChange(
        testBluetoothDevice, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED);

    verify(mockCallback).onRemoteDeviceDisconnected(testBluetoothDevice);
  }

  @Test
  public void onConnectionStateChange_unknownDevice_doNotInvokeDisconnectedCallback() {
    BluetoothGattServerCallback callback = setupGattServer();

    callback.onConnectionStateChange(
        testBluetoothDevice, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);
    callback.onConnectionStateChange(
        unknownBluetoothDevice, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED);

    verify(mockCallback, never()).onRemoteDeviceDisconnected(testBluetoothDevice);
  }

  @Test
  public void onConnectionStateChange_doNotCloseGattServer() {
    BluetoothGattServerCallback callback = setupGattServer();

    callback.onConnectionStateChange(
        testBluetoothDevice, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);
    callback.onConnectionStateChange(
        testBluetoothDevice, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED);

    verify(mockGattServer, never()).close();
  }

  @Test
  public void onConnectionStateChange_multipleCallbacks_doNotRepeatCleanup() {
    BluetoothGattServerCallback callback = setupGattServer();

    callback.onConnectionStateChange(
        testBluetoothDevice, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);

    verify(mockGattServer).clearServices();

    callback.onConnectionStateChange(
        testBluetoothDevice, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);

    verify(mockGattServer).clearServices();
  }

  @Test
  public void disconnect_cancelingConnectionOnTheDevice() {
    BluetoothGattServerCallback callback = setupGattServer();

    callback.onConnectionStateChange(
        testBluetoothDevice, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);

    blePeripheralManager.disconnect();

    verify(mockGattServer).cancelConnection(testBluetoothDevice);
    verify(mockCallback, never()).onRemoteDeviceDisconnected(testBluetoothDevice);
    verify(mockGattServer, never()).close();
  }

  @Test
  public void cleanup_closeGattServer() {
    setupGattServer();

    blePeripheralManager.cleanup();

    verify(mockGattServer).close();
  }

  @Test
  public void cleanup_disconnectDevice() {
    BluetoothGattServerCallback callback = setupGattServer();

    callback.onConnectionStateChange(
        testBluetoothDevice, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);

    blePeripheralManager.cleanup();

    verify(mockGattServer).cancelConnection(testBluetoothDevice);
  }

  @Test
  public void onNotificationSent_invokeListeners() {
    BluetoothGattServerCallback callback = setupGattServer();

    callback.onConnectionStateChange(
        testBluetoothDevice, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);
    callback.onNotificationSent(testBluetoothDevice, BluetoothGatt.GATT_SUCCESS);

    verify(mockOnCharacteristicReadListener).onCharacteristicRead(testBluetoothDevice);
  }

  @Test
  public void onConnectionStateChange_disconnect_clearListeners() {
    BluetoothGattServerCallback callback = setupGattServer();

    callback.onConnectionStateChange(
        testBluetoothDevice, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);
    callback.onConnectionStateChange(
        testBluetoothDevice, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED);
    callback.onNotificationSent(testBluetoothDevice, BluetoothGatt.GATT_SUCCESS);

    verify(mockOnCharacteristicReadListener, never()).onCharacteristicRead(testBluetoothDevice);
  }

  @CanIgnoreReturnValue
  private BluetoothGattServerCallback setupGattServer() {
    shadowOf(application)
        .grantPermissions(permission.BLUETOOTH_CONNECT, permission.BLUETOOTH_ADVERTISE);
    UUID uuid = UUID.randomUUID();
    AdvertiseData advertiseData =
        new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(uuid)).build();
    AdvertiseData scanResponse =
        new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(uuid)).build();
    BluetoothGattService service =
        new BluetoothGattService(uuid, BluetoothGattService.SERVICE_TYPE_PRIMARY);
    blePeripheralManager.startAdvertising(
        service, advertiseData, scanResponse, mockAdvertiseCallback);
    ArgumentCaptor<BluetoothGattServerCallback> captor =
        ArgumentCaptor.forClass(BluetoothGattServerCallback.class);
    verify(mockBluetoothManager).openGattServer(captor.capture());
    return captor.getValue();
  }
}
