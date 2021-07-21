/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.google.android.connecteddevice.transport.proxy;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.transport.ble.BlePeripheralManager;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.AddServiceMessage;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.BlePeripheralMessageParcel;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.BlePeripheralMessageParcel.PayloadType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public final class ProxyBlePeripheralManagerTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  // Sufficiently large buffer for proto messages.
  private static final int BUFFER_SIZE = 2000;

  private ProxyBlePeripheralManager manager;

  @Mock private BlePeripheralManager.Callback mockCallback;
  @Mock private AdvertiseCallback mockAdvertiseCallback;
  @Mock private Socket mockSocket;
  private ByteArrayInputStream inputStream;
  private ByteArrayOutputStream outputStream;

  @Before
  public void setUp() throws IOException {
    inputStream = new ByteArrayInputStream(new byte[BUFFER_SIZE]);
    outputStream = new ByteArrayOutputStream(BUFFER_SIZE);
    when(mockSocket.getInputStream()).thenReturn(inputStream);
    when(mockSocket.getOutputStream()).thenReturn(outputStream);

    manager =
        new ProxyBlePeripheralManager(
            new PassthroughSocketFactory(mockSocket), new DirectScheduledExecutorService());
    manager.registerCallback(mockCallback);
  }

  @Test
  public void startAdvertising_sendsMessagesOfPlainTextAddServiceStartAdvertising()
      throws InvalidProtocolBufferException {
    UUID serviceUuid = UUID.fromString("29383942-34e2-4886-994b-973702b15f9d");

    startAdvertising(serviceUuid, mockAdvertiseCallback);

    List<BlePeripheralMessageParcel> parcels = readMessageParcels(outputStream.toByteArray());
    assertThat(parcels.get(0).getType()).isEqualTo(PayloadType.PLAIN_TEXT);
    assertThat(parcels.get(1).getType()).isEqualTo(PayloadType.ADD_SERVICE);
    assertThat(parcels.get(2).getType()).isEqualTo(PayloadType.START_ADVERTISING);
  }

  @Test
  public void startAdvertising_addService() throws InvalidProtocolBufferException {
    UUID serviceUuid = UUID.fromString("29383942-34e2-4886-994b-973702b15f9d");

    startAdvertising(serviceUuid, mockAdvertiseCallback);

    List<BlePeripheralMessageParcel> parcels = readMessageParcels(outputStream.toByteArray());
    AddServiceMessage addServiceMessage =
        AddServiceMessage.parser().parseFrom(parcels.get(1).getPayload());
    assertThat(addServiceMessage.getService().getIdentifier()).isEqualTo(serviceUuid.toString());
  }

  @Test
  public void stopAdvertising() throws InvalidProtocolBufferException {
    // Start advertising so manager creates a message writer.
    startAdvertising(
        UUID.fromString("29383942-34e2-4886-994b-973702b15f9d"), mockAdvertiseCallback);

    manager.stopAdvertising(mockAdvertiseCallback);

    List<BlePeripheralMessageParcel> parcels = readMessageParcels(outputStream.toByteArray());
    // First 3 messages came from start advertising.
    assertThat(parcels.get(3).getType()).isEqualTo(PayloadType.STOP_ADVERTISING);
  }

  @Test
  public void cleanup_closesSocket() throws IOException {
    // Start advertising so manager creates a message writer.
    startAdvertising(
        UUID.fromString("29383942-34e2-4886-994b-973702b15f9d"), mockAdvertiseCallback);

    manager.cleanup();

    List<BlePeripheralMessageParcel> parcels = readMessageParcels(outputStream.toByteArray());
    // Clean up should stop advertising.
    assertThat(parcels.get(3).getType()).isEqualTo(PayloadType.STOP_ADVERTISING);
    verify(mockSocket).close();
  }

  @Test
  public void onRemoteDeviceConnected_notfiesCallback() {
    BluetoothDevice device =
        BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:AA:BB");
    manager.messageReaderCallback.onRemoteDeviceConnected(device);

    verify(mockCallback).onRemoteDeviceConnected(eq(device));
  }

  @Test
  public void notifyCharacteristicChanged_deviceDoesNotMatch_ignored()
      throws InvalidProtocolBufferException {
    // First connect to a device.
    BluetoothDevice connectedDevice =
        BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:AA:BB");
    BluetoothDevice unknownDevice =
        BluetoothAdapter.getDefaultAdapter().getRemoteDevice("AA:BB:00:11:22:33");
    manager.messageReaderCallback.onRemoteDeviceConnected(connectedDevice);

    BluetoothGattCharacteristic characteristic =
        new BluetoothGattCharacteristic(
            UUID.fromString("a2ee234c-fb4e-48ff-b95a-5eb6ec1e6533"),
            /* properties= */ 0,
            /* permissions= */ 0);
    manager.notifyCharacteristicChanged(unknownDevice, characteristic, /* confirm= */ true);

    List<BlePeripheralMessageParcel> parcels = readMessageParcels(outputStream.toByteArray());
    assertThat(parcels).isEmpty();
  }

  @Test
  public void notifyCharacteristicChanged_sendsMessage() throws InvalidProtocolBufferException {
    // Start advertising so manager creates a message writer.
    startAdvertising(
        UUID.fromString("29383942-34e2-4886-994b-973702b15f9d"), mockAdvertiseCallback);
    // Connect to a device.
    BluetoothDevice connectedDevice =
        BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:AA:BB");
    manager.messageReaderCallback.onRemoteDeviceConnected(connectedDevice);

    BluetoothGattCharacteristic characteristic =
        new BluetoothGattCharacteristic(
            UUID.fromString("a2ee234c-fb4e-48ff-b95a-5eb6ec1e6533"),
            /* properties= */ 0,
            /* permissions= */ 0);
    manager.notifyCharacteristicChanged(connectedDevice, characteristic, /* confirm= */ true);

    List<BlePeripheralMessageParcel> parcels = readMessageParcels(outputStream.toByteArray());
    // First 3 messages came from start advertising.
    assertThat(parcels.get(3).getType()).isEqualTo(PayloadType.UPDATE_CHARACTERISTIC);
  }

  private List<BlePeripheralMessageParcel> readMessageParcels(byte[] stream)
      throws InvalidProtocolBufferException {
    InputStream inputStream = new ByteArrayInputStream(stream);

    List<BlePeripheralMessageParcel> parcels = new ArrayList<>();
    while (true) {
      BlePeripheralMessageParcel parcel =
          BlePeripheralMessageParcel.parser().parseDelimitedFrom(inputStream);

      if (parcel != null) {
        parcels.add(parcel);
      } else {
        break;
      }
    }
    return parcels;
  }

  private void startAdvertising(UUID serviceUuid, AdvertiseCallback advertiseCallback) {
    manager.startAdvertising(
        new BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY),
        // AdvertiseData and ScanResponse are ignored.
        /* advertiseData= */ new AdvertiseData.Builder().build(),
        /* scanResponse= */ new AdvertiseData.Builder().build(),
        advertiseCallback);
  }

  static class PassthroughSocketFactory implements SocketFactory {
    private final Socket socket;

    PassthroughSocketFactory(Socket socket) {
      this.socket = socket;
    }

    @Override
    public Socket createSocket() {
      return socket;
    }
  }

  /** Immediately executes {@link submit}ted tasks. Ignores other tasks. */
  static class DirectScheduledExecutorService implements ScheduledExecutorService {

    @Override
    public Future<?> submit(Runnable task) {
      task.run();
      return null;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
      try {
        task.call();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
      throw new UnsupportedOperationException();
    }

    // Methods inherited from ScheduledExecutorService.

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable command, long initialDelay, long delay, TimeUnit unit) {
      // This method is used by field MessageReader. Ignore its tasks.
      return null;
    }

    @Override
    public <T> ScheduledFuture<T> schedule(Callable<T> callable, long delay, TimeUnit unit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
        Runnable command, long initialDelay, long period, TimeUnit unit) {
      throw new UnsupportedOperationException();
    }

    // Methods inherited from Executor.

    @Override
    public void execute(Runnable command) {
      throw new UnsupportedOperationException();
    }

    // Methods inherited from ExecutorService.

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<Future<T>> invokeAll(
        Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isShutdown() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isTerminated() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown() {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Runnable> shutdownNow() {
      throw new UnsupportedOperationException();
    }
  }
}
