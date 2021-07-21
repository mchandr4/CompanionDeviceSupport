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

package com.google.android.connecteddevice.transport.proxy;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothGattCharacteristic;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.BlePeripheralMessageParcel;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.BlePeripheralMessageParcel.PayloadType;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.Central;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.Characteristic;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.NotifyCentralDidWriteValueMessage;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.NotifyCentralSubscriptionMessage;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.NotifyCentralSubscriptionMessage.Event;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.NotifyCharacteristicUpdatedMessage;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.NotifyStartedAdvertisingMessage;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.UpdateCharacteristicMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public final class MessageReaderTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private MessageReader messageReader;

  @Mock private MessageReader.Callback mockCallback;

  @Before
  public void setUp() {
    messageReader =
        new MessageReader(
            new ByteArrayInputStream(new byte[] {}),
            mockCallback,
            Executors.newSingleThreadScheduledExecutor());
    messageReader.startProcessingInputStream();
  }

  @Test
  public void processMessageParcel_startedAdvertising() throws IOException {
    NotifyStartedAdvertisingMessage message = NotifyStartedAdvertisingMessage.getDefaultInstance();

    messageReader.processMessageParcel(
        createParcel(message, PayloadType.NOTIFY_STARTED_ADVERTISING));

    verify(mockCallback).onAdvertisingStarted();
  }

  @Test
  public void processMessageParcel_centralSubscription() throws IOException {
    Central central = Central.newBuilder().setIdentifier(UUID.randomUUID().toString()).build();
    NotifyCentralSubscriptionMessage message =
        NotifyCentralSubscriptionMessage.newBuilder()
            .setCentral(central)
            .setEvent(Event.SUBSCRIBED)
            .build();

    messageReader.processMessageParcel(
        createParcel(message, PayloadType.NOTIFY_CENTRAL_SUBSCRIPTION_EVENT));

    verify(mockCallback).onRemoteDeviceConnected(any());
  }

  @Test
  public void processMessageParcel_centralWroteValue() throws IOException {
    byte[] value = new byte[] {(byte) 0xbe, (byte) 0xef};
    UUID characteristicUuid = UUID.randomUUID();
    Central central = Central.newBuilder().setIdentifier(UUID.randomUUID().toString()).build();
    Characteristic characteristic =
        Characteristic.newBuilder()
            .setIdentifier(characteristicUuid.toString())
            .setValue(ByteString.copyFrom(value))
            .build();
    NotifyCentralDidWriteValueMessage message =
        NotifyCentralDidWriteValueMessage.newBuilder()
            .setCentral(central)
            .setCharacteristic(characteristic)
            .build();

    messageReader.processMessageParcel(
        createParcel(message, PayloadType.NOTIFY_CENTRAL_WROTE_VALUE));

    ArgumentCaptor<BluetoothGattCharacteristic> characteristicCaptor =
        ArgumentCaptor.forClass(BluetoothGattCharacteristic.class);
    verify(mockCallback).onCharacteristicWrite(any(), characteristicCaptor.capture());

    BluetoothGattCharacteristic gattCharacteristic = characteristicCaptor.getValue();
    assertThat(Arrays.equals(gattCharacteristic.getValue(), value)).isTrue();
    assertThat(gattCharacteristic.getUuid()).isEqualTo(characteristicUuid);
  }

  @Test
  public void processMessageParcel_characteristicUpdated() throws IOException {
    NotifyCharacteristicUpdatedMessage message =
        NotifyCharacteristicUpdatedMessage.getDefaultInstance();

    messageReader.processMessageParcel(
        createParcel(message, PayloadType.NOTIFY_CHARACTERISTIC_UPDATED));

    verify(mockCallback).onCharacteristicUpdated(any());
  }

  @Test
  public void processMessageParcel_throwsException() {
    // Create a message that should only be sent from IHU to proxy.
    UpdateCharacteristicMessage message = UpdateCharacteristicMessage.getDefaultInstance();

    assertThrows(
        UnsupportedOperationException.class,
        () ->
            messageReader.processMessageParcel(
                createParcel(message, PayloadType.UPDATE_CHARACTERISTIC)));
  }

  private static BlePeripheralMessageParcel createParcel(MessageLite message, PayloadType type) {
    return BlePeripheralMessageParcel.newBuilder()
        .setPayload(message.toByteString())
        .setType(type)
        .build();
  }
}
