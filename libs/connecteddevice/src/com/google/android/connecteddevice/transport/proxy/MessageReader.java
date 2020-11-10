package com.google.android.connecteddevice.transport.proxy;

import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logi;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.BlePeripheralMessageParcel;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.BlePeripheralMessageParcel.PayloadType;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.Central;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.NotifyCentralDidWriteValueMessage;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.NotifyCentralSubscriptionMessage;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.NotifyPeripheralStateDidUpdateMessage;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.PlainTextMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically attempts to read message from input stream.
 *
 * <p>Messages are parsed into BLE proxy protos. Based on message type, this class creates
 * corresponding Android platform Bluetooth API class and notifies the registered callback.
 */
class MessageReader implements CentralConnectionStatus.Callback {
  private static final String TAG = "BlePeripheralManager";
  // Interval between each attempt to process data in the input stream.
  private static final int READ_INPUT_STREAM_INTERNAL_MS = 50;
  private static final BluetoothDevice FAKE_BLUETOOTH_DEVICE =
      BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:AA:BB");

  private final AtomicBoolean keepRunning = new AtomicBoolean(true);
  private final ScheduledExecutorService scheduler;

  private InputStream inputStream;
  private Callback callback;

  // Tracks the connected GATT central. For simplicity, assume there is only one connection.
  private CentralConnectionStatus connectionStatus;

  public MessageReader(
      @NonNull InputStream inputStream,
      @NonNull Callback callback,
      @NonNull ScheduledExecutorService scheduler) {
    // inputStream will be closed on invalidate().
    this.inputStream = inputStream;
    this.callback = callback;
    this.scheduler = scheduler;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  // Exception is handled by the Runnable, and notified via callback. Ignore the returned future.
  public void startProcessingInputStream() {
    scheduler.scheduleWithFixedDelay(
        () -> {
          if (keepRunning.get()) {
            try {
              if (inputStream.available() == 0) {
                return;
              }

              BlePeripheralMessageParcel parcel =
                  BlePeripheralMessageParcel.parser().parseDelimitedFrom(inputStream);

              processMessageParcel(parcel);
            } catch (IOException e) {
              loge(TAG, "Exception while processing input stream.", e);
              callback.onInputStreamFailure();
            }
          }
        },
        /* initialDelay= */ 0,
        READ_INPUT_STREAM_INTERNAL_MS,
        MILLISECONDS);
  }

  @VisibleForTesting
  void processMessageParcel(BlePeripheralMessageParcel parcel) throws IOException {
    PayloadType payloadType = parcel.getType();
    ByteString payload = parcel.getPayload();

    switch (payloadType) {
      case NOTIFY_STARTED_ADVERTISING:
        // Ignore the message.
        callback.onAdvertisingStarted();
        break;
      case NOTIFY_CENTRAL_SUBSCRIPTION_EVENT:
        handleNotifyCentralSubscriptionMessage(
            NotifyCentralSubscriptionMessage.parser().parseFrom(payload));
        break;
      case NOTIFY_CENTRAL_WROTE_VALUE:
        handleNotifyCentralDidWriteValueMessage(
            NotifyCentralDidWriteValueMessage.parser().parseFrom(payload));
        break;
      case NOTIFY_STATE_UPDATED:
        handleNotifyPeripheralStateDidUpdateMessage(
            NotifyPeripheralStateDidUpdateMessage.parser().parseFrom(payload));
        break;
      case PLAIN_TEXT:
        handlePlainTextMessage(PlainTextMessage.parser().parseFrom(payload));
        break;
      default:
        loge(TAG, "Received unrecognized payload type: " + payloadType.getNumber() + ". Ignored");
    }
  }

  private void handleNotifyCentralSubscriptionMessage(NotifyCentralSubscriptionMessage message) {
    if (connectionStatus == null) {
      connectionStatus = new CentralConnectionStatus(message.getCentral(), scheduler);
      connectionStatus.setCallback(this);
    }
    if (!connectionStatus.handleSubscriptionEvent(message)) {
      loge(TAG, "Central not handle subscription message. Ignored.");
    }
  }

  @Override
  public void onCentralConnected(Central central) {
    callback.onRemoteDeviceConnected(FAKE_BLUETOOTH_DEVICE);
  }

  @Override
  public void onCentralDisconnected(Central central) {
    if (connectionStatus != null) {
      connectionStatus.clearCallback();
      connectionStatus = null;
    }

    callback.onRemoteDeviceDisconnected(FAKE_BLUETOOTH_DEVICE);
  }

  private void handleNotifyCentralDidWriteValueMessage(NotifyCentralDidWriteValueMessage message) {
    BluetoothGattCharacteristic gattCharacteristic =
        ProtoConverter.toGattCharacteristic(message.getCharacteristic());
    logi(TAG, "Received value from central of " + gattCharacteristic.getValue().length + " bytes.");
    callback.onCharacteristicWrite(FAKE_BLUETOOTH_DEVICE, gattCharacteristic);
  }

  private static void handleNotifyPeripheralStateDidUpdateMessage(
      NotifyPeripheralStateDidUpdateMessage message) {
    logi(TAG, "Peripheral state updated. New state is " + message.getState().getNumber());
  }

  private static void handlePlainTextMessage(PlainTextMessage message) {
    logi(TAG, "Received plain text message: " + message.getBody());
  }

  /**
   * Cleans up this class.
   *
   * <p>Stops processing the input stream; also closes the input stream. Once invalidted, this
   * object cannot be re-used.
   */
  public void invalidate() {
    logi(TAG, "Invalidating MessageReader.");
    try {
      callback = null;
      keepRunning.set(false);
      inputStream.close();
      inputStream = null;
      logi(TAG, "MessageReader.invalidate:[closed stream]");
    } catch (IOException e) {
      loge(TAG, "Could not close input stream in MessageReader.", e);
    }
  }

  interface Callback {
    void onAdvertisingStarted();

    /** The connected device uses a fixed fake address. */
    void onRemoteDeviceConnected(@NonNull BluetoothDevice device);

    /** The connected device uses a fixed fake address. */
    void onRemoteDeviceDisconnected(@NonNull BluetoothDevice device);

    void onCharacteristicWrite(
        @NonNull BluetoothDevice device, @NonNull BluetoothGattCharacteristic characteristic);

    void onInputStreamFailure();
  }
}
