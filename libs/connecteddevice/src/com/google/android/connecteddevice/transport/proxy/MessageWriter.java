package com.google.android.connecteddevice.transport.proxy;

import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logi;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.AdvertiseData;
import androidx.annotation.NonNull;
import com.google.protobuf.MessageLite;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.AddServiceMessage;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.BlePeripheralMessageParcel;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.BlePeripheralMessageParcel.PayloadType;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.PlainTextMessage;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.StartAdvertisingMessage;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.StopAdvertisingMessage;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.UpdateCharacteristicMessage;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes Android platform Bluetooth API objects into output stream as BLE proxy proto messages.
 *
 * <p>The sent out message will be notified via Callback#onMessageSent.
 */
class MessageWriter {
  private static final String TAG = "BlePeripheralManager";

  private static final int PARCEL_VERSION = 1;

  private OutputStream outputStream;
  private Callback callback;

  public MessageWriter(@NonNull OutputStream outputStream, @NonNull Callback callback) {
    // output stream will be closed on invalidate().
    this.outputStream = outputStream;
    this.callback = callback;
  }

  public void sendText(@NonNull String text) {
    logi(TAG, "sendText: " + text);
    PlainTextMessage message = PlainTextMessage.newBuilder().setBody(text).build();
    writePayload(message, PayloadType.PLAIN_TEXT);
  }

  public void addService(
      @NonNull BluetoothGattService gattService, @NonNull AdvertiseData advertiseData) {
    logi(TAG, "addService");
    AddServiceMessage message =
        AddServiceMessage.newBuilder()
            .setService(ProtoConverter.toService(gattService, advertiseData))
            .build();
    writePayload(message, PayloadType.ADD_SERVICE);
  }

  public void startAdvertising(@NonNull String name) {
    logi(TAG, "startAdvertising");
    StartAdvertisingMessage message = StartAdvertisingMessage.newBuilder().setName(name).build();
    writePayload(message, PayloadType.START_ADVERTISING);
  }

  public void stopAdvertising() {
    logi(TAG, "stopAdvertising");
    StopAdvertisingMessage message = StopAdvertisingMessage.getDefaultInstance();
    writePayload(message, PayloadType.STOP_ADVERTISING);
  }

  public void updateCharacteristic(@NonNull BluetoothGattCharacteristic characteristic) {
    logi(TAG, "updateCharacteristic");
    UpdateCharacteristicMessage message =
        UpdateCharacteristicMessage.newBuilder()
            .setCharacteristic(ProtoConverter.toCharacteristic(characteristic))
            .build();
    writePayload(message, PayloadType.UPDATE_CHARACTERISTIC);
  }

  private void writePayload(MessageLite payload, PayloadType type) {
    BlePeripheralMessageParcel parcel =
        BlePeripheralMessageParcel.newBuilder()
            .setVersion(PARCEL_VERSION)
            .setType(type)
            .setPayload(payload.toByteString())
            .build();

    logi(TAG, "MessageWriter: sending message of type: " + type.getNumber());
    try {
      parcel.writeDelimitedTo(outputStream);
      callback.onMessageSent(payload);
    } catch (IOException exception) {
      callback.onWriteMessageFailed();
    }
  }

  /**
   * Cleans up this class.
   *
   * <p>Closes the output stream. Once invalidted, this object cannot be re-used.
   */
  public void invalidate() {
    logi(TAG, "Invalidating MessageWriter.");
    try {
      callback = null;
      outputStream.close();
      outputStream = null;
      logi(TAG, "MessageWriter.invalidate:[closed stream]");
    } catch (IOException e) {
      loge(TAG, "Could not close output stream in MessageWriter.", e);
    }
  }

  interface Callback {
    void onMessageSent(@NonNull MessageLite message);

    void onWriteMessageFailed();
  }
}
