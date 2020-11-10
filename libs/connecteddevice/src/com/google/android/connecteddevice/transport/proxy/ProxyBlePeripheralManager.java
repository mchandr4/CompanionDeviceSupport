package com.google.android.connecteddevice.transport.proxy;

import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logi;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import androidx.annotation.NonNull;
import com.google.android.connecteddevice.connection.ble.BlePeripheralManager;
import com.google.protobuf.MessageLite;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** An implementation that proxies BLE peripheral operations as network requests. */
public class ProxyBlePeripheralManager extends BlePeripheralManager {
  private static final String TAG = "ProxyBlePeripheralManager";

  private static final int MTU_SIZE = 20;
  private static final String ADVERTISED_NAME = "EmulatorBleProxy";
  private static final String REMOTE_DEVICE_NAME = "BleProxyCompanionDevice";

  // Pre-allocated address by Android emulator from
  // https://developer.android.com/studio/run/emulator-networking
  private static final byte[] HOST_LOOPBACK_ADDRESS = {10, 0, 2, 2};
  // Ensure the host running the proxy service listens to the same port.
  // LINT.IfChange
  private static final int PORT = 12120;
  // LINT.ThenChange(//depot/google3/third_party/swift/BleProxyService/bleperipheral/BlePeripheralService.swift)

  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

  private final MessageReader.Callback messageReaderCallback =
      new MessageReader.Callback() {
        @Override
        public void onAdvertisingStarted() {
          logi(TAG, "Proxy session did start advertising.");
          // Create fake value as the settings will be ignored.
          AdvertiseSettings settings = new AdvertiseSettings.Builder().build();
          advertiseCallback.onStartSuccess(settings);
        }

        @Override
        public void onRemoteDeviceConnected(BluetoothDevice device) {
          logi(TAG, "Remote device connected: " + device.getAddress());
          connectedDevice = device;
          for (BlePeripheralManager.Callback callback : callbacks) {
            callback.onRemoteDeviceConnected(device);
          }
        }

        @Override
        public void onRemoteDeviceDisconnected(BluetoothDevice device) {
          logi(TAG, "Remote device disconnected: " + device.getAddress());
          connectedDevice = null;
          for (BlePeripheralManager.Callback callback : callbacks) {
            callback.onRemoteDeviceDisconnected(device);
          }
        }

        @Override
        public void onCharacteristicWrite(
            BluetoothDevice device, BluetoothGattCharacteristic characteristic) {
          logi(TAG, "Remote device wrote to characteristic: " + characteristic.getUuid());
          for (OnCharacteristicWriteListener listener : writeListeners) {
            listener.onCharacteristicWrite(device, characteristic, characteristic.getValue());
          }
        }

        @Override
        public void onInputStreamFailure() {
          loge(TAG, "MessageReader input stream failed. Closing connection.");
          cleanup();
        }
      };

  private final MessageWriter.Callback messageWriterCallback =
      new MessageWriter.Callback() {
        @Override
        public void onMessageSent(MessageLite message) {
          logi(TAG, "Succeeded sending message.");
          for (OnCharacteristicReadListener listener : readListeners) {
            listener.onCharacteristicRead(connectedDevice);
          }
        }

        @Override
        public void onWriteMessageFailed() {
          loge(TAG, "MessageWriter output stream failed. Closing connection.");
          cleanup();
        }
      };

  // Socket (used by proxy) depends on active network, which might not be available when the
  // containing service is brought up on boot.
  // This value being counted down to zero indicates an active network is ready.
  private final CountDownLatch networkReadySignal = new CountDownLatch(1);
  private Socket socket;
  private MessageWriter messageWriter;
  private MessageReader messageReader;

  // The connected device via proxy. For simplicity, assume only one device is connected at a time.
  private BluetoothDevice connectedDevice;
  private AdvertiseCallback advertiseCallback;

  public ProxyBlePeripheralManager(Context context) {
    ensureNetworkIsReady(context.getSystemService(ConnectivityManager.class));
  }

  /** Ensures networkReadySignal will be counted down to zero. */
  private void ensureNetworkIsReady(ConnectivityManager connectivityManager) {
    if (connectivityManager.getActiveNetwork() != null) {
      logi(TAG, "Active network is already available.");
      networkReadySignal.countDown();
      return;
    }

    logi(TAG, "Waiting for active network.");
    connectivityManager.registerDefaultNetworkCallback(
        new ConnectivityManager.NetworkCallback() {
          @Override
          public void onAvailable(Network network) {
            logi(TAG, "Received callback for active network:" + network);
            networkReadySignal.countDown();
            connectivityManager.unregisterNetworkCallback(this);
          }
        });
  }

  @Override
  protected int getMtuSize() {
    return MTU_SIZE;
  }

  /**
   * Requests to start advertising.
   *
   * <p>Advertising is assumed to be entry point of a connection session, so we will perform the
   * following in order to start advertising:
   *
   * <ol>
   *   <li>Creating a socket - potentially blocking the thread;
   *   <li>Requesting to add GATT service to proxy server;
   *   <li>Requesting to start adverting with a fixed name;
   * </ol>
   */
  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  protected void startAdvertising(
      BluetoothGattService service,
      AdvertiseData advertiseData,
      AdvertiseData scanResponse,
      AdvertiseCallback advertiseCallback) {
    logi(TAG, "Request to start advertising with service " + service.getUuid() + ".");

    this.advertiseCallback = advertiseCallback;

    if (socket == null) {
      createSocket();
    }

    executor.submit(
        () -> {
          messageWriter.sendText("Hello from the Emulator ProxyBlePeripheralManager.");
          messageWriter.addService(service, advertiseData);
          messageWriter.startAdvertising(ADVERTISED_NAME);
        });
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void createSocket() {
    executor.submit(
        () -> {
          logi(TAG, "Start waiting for active network.");
          try {
            networkReadySignal.await();
          } catch (InterruptedException e) {
            loge(TAG, "Thread interrupted waiting for active network. ", e);
            return;
          }

          logi(TAG, "Setting up socket for proxy.");
          try {
            InetAddress address = InetAddress.getByAddress(HOST_LOOPBACK_ADDRESS);
            socket = new Socket(address, PORT);
            socket.setKeepAlive(true);
          } catch (IOException e) {
            loge(TAG, "Could not create new socket.", e);
            return;
          }

          try {
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();
            messageReader = new MessageReader(inputStream, messageReaderCallback, executor);
            messageReader.startProcessingInputStream();
            messageWriter = new MessageWriter(outputStream, messageWriterCallback);
          } catch (IOException e) {
            loge(TAG, "Could not retrieve input/output stream from socket.", e);
            return;
          }
          logi(TAG, "New socket created.");
        });
  }

  /**
   * Requests to stop advertising.
   *
   * <p>This method ignores the parameter advertiseCallback, i.e. it will always request to stop.
   */
  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  protected void stopAdvertising(AdvertiseCallback advertiseCallback) {
    logi(TAG, "Request to stop advertising.");
    advertiseCallback = null;
    executor.submit(
        () -> {
          messageWriter.stopAdvertising();
        });
  }

  /** Requests to update a characteristic. */
  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  protected void notifyCharacteristicChanged(
      @NonNull BluetoothDevice device,
      @NonNull BluetoothGattCharacteristic characteristic,
      boolean confirm) {
    if (!device.getAddress().equals(connectedDevice.getAddress())) {
      loge(TAG, "Requested to write unknown device. Ignored.");
      return;
    }

    executor.submit(
        () -> {
          messageWriter.updateCharacteristic(characteristic);
        });
  }

  /** Invokes callbacks with a fixed name for remote device. */
  @Override
  protected void retrieveDeviceName(BluetoothDevice device) {
    for (Callback callback : callbacks) {
      callback.onDeviceNameRetrieved(REMOTE_DEVICE_NAME);
    }
  }

  /** Closes the proxy socket and its dependent components. */
  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  protected void cleanup() {
    super.cleanup();

    logi(TAG, "Cleaning up manager.");
    if (socket == null) {
      loge(TAG, "Attempted to invalidate without a socket. Ignored.");
      return;
    }
    stopAdvertising(advertiseCallback);
    // Submit a task because stopAdvertising still needs the socket to send request.
    executor.submit(
        () -> {
          messageReader.invalidate();
          messageReader = null;
          messageWriter.invalidate();
          messageWriter = null;

          try {
            socket.close();
          } catch (IOException e) {
            loge(TAG, "Could not close socket.", e);
            return;
          }
          socket = null;
        });
  }
}
