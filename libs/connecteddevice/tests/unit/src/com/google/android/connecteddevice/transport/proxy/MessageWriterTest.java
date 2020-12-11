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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.AddServiceMessage;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.BlePeripheralMessageParcel;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.BlePeripheralMessageParcel.PayloadType;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.UpdateCharacteristicMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public final class MessageWriterTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private MessageWriter.Callback mockCallback;
  private MessageWriter messageWriter;
  private ByteArrayOutputStream outputStream;

  @Before
  public void setUp() {
    outputStream = new ByteArrayOutputStream();
    messageWriter = new MessageWriter(outputStream, mockCallback);
  }

  @Test
  public void startAdvertising() throws IOException {
    messageWriter.startAdvertising("name");

    verify(mockCallback).onMessageSent(any());
    assertThat(parseOutputStream().getType()).isEqualTo(PayloadType.START_ADVERTISING);
  }

  @Test
  public void stopAdvertising() throws IOException {
    messageWriter.stopAdvertising();

    verify(mockCallback).onMessageSent(any());
    assertThat(parseOutputStream().getType()).isEqualTo(PayloadType.STOP_ADVERTISING);
  }

  @Test
  public void addService() throws IOException {
    UUID characteristicUuid = UUID.randomUUID();
    BluetoothGattCharacteristic gattCharacteristic =
        new BluetoothGattCharacteristic(characteristicUuid, 0, 0);
    UUID serviceUuid = UUID.randomUUID();
    BluetoothGattService gattService =
        new BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY);
    gattService.addCharacteristic(gattCharacteristic);

    messageWriter.addService(gattService, null);

    verify(mockCallback).onMessageSent(any());

    BlePeripheralMessageParcel parcel = parseOutputStream();
    assertThat(parcel.getType()).isEqualTo(PayloadType.ADD_SERVICE);
    AddServiceMessage message = AddServiceMessage.parser().parseFrom(parcel.getPayload());
    assertThat(message.getService().getIdentifier()).isEqualTo(serviceUuid.toString());
    assertThat(message.getService().getCharacteristicsList()).hasSize(1);
    assertThat(message.getService().getCharacteristics(0).getIdentifier())
        .isEqualTo(characteristicUuid.toString());
  }

  @Test
  public void updateCharacteristic() throws IOException {
    byte[] value = new byte[] {(byte) 0xbe, (byte) 0xef};
    UUID characteristicUuid = UUID.randomUUID();
    BluetoothGattCharacteristic gattCharacteristic =
        new BluetoothGattCharacteristic(characteristicUuid, 0, 0);
    gattCharacteristic.setValue(value);

    messageWriter.updateCharacteristic(gattCharacteristic);

    verify(mockCallback).onMessageSent(any());

    BlePeripheralMessageParcel parcel = parseOutputStream();
    assertThat(parcel.getType()).isEqualTo(PayloadType.UPDATE_CHARACTERISTIC);
    UpdateCharacteristicMessage message =
        UpdateCharacteristicMessage.parser().parseFrom(parcel.getPayload());
    assertThat(message.getCharacteristic().getIdentifier())
        .isEqualTo(characteristicUuid.toString());
    assertThat(Arrays.equals(message.getCharacteristic().getValue().toByteArray(), value)).isTrue();
  }

  private BlePeripheralMessageParcel parseOutputStream() throws IOException {
    return BlePeripheralMessageParcel.parser()
        .parseDelimitedFrom(new ByteArrayInputStream(outputStream.toByteArray()));
  }
}
