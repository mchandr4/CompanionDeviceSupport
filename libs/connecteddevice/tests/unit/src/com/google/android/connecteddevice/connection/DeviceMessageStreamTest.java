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

package com.google.android.connecteddevice.connection;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.companionprotos.DeviceMessageProto.Message;
import com.google.android.companionprotos.OperationProto.OperationType;
import com.google.android.companionprotos.PacketProto.Packet;
import com.google.android.connecteddevice.connection.DeviceMessageStream.MessageReceivedErrorListener;
import com.google.android.connecteddevice.connection.DeviceMessageStream.MessageReceivedListener;
import com.google.android.connecteddevice.model.DeviceMessage;
import com.google.android.connecteddevice.util.ByteUtils;
import com.google.common.collect.Iterables;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class DeviceMessageStreamTest {

  private static final int WRITE_SIZE = 500;

  private DeviceMessageStream stream;

  @Mock private MessageReceivedListener mockMessageReceivedListener;
  @Mock private MessageReceivedErrorListener mockErrorListener;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    stream =
        spy(
            new DeviceMessageStream(WRITE_SIZE) {
              @Override
              protected void send(byte[] data) {}
            });
  }

  @Test
  public void processPacket_notifiesWithEntireMessageForSinglePacketMessage() {
    stream.setMessageReceivedListener(mockMessageReceivedListener);
    byte[] data = ByteUtils.randomBytes(5);
    processMessage(data);
    ArgumentCaptor<DeviceMessage> messageCaptor = ArgumentCaptor.forClass(DeviceMessage.class);
    verify(mockMessageReceivedListener).onMessageReceived(messageCaptor.capture());
    assertThat(messageCaptor.getValue().getMessage()).isEqualTo(data);
  }

  @Test
  public void processPacket_notifiesWithEntireMessageForMultiPacketMessage() {
    stream.setMessageReceivedListener(mockMessageReceivedListener);
    byte[] data = ByteUtils.randomBytes(750);
    processMessage(data);
    ArgumentCaptor<DeviceMessage> messageCaptor = ArgumentCaptor.forClass(DeviceMessage.class);
    verify(mockMessageReceivedListener).onMessageReceived(messageCaptor.capture());
    assertThat(Arrays.equals(data, messageCaptor.getValue().getMessage())).isTrue();
  }

  @Test
  public void processPacket_receivingMultipleMessagesInParallelParsesSuccessfully() {
    stream.setMessageReceivedListener(mockMessageReceivedListener);
    byte[] data = ByteUtils.randomBytes((int) (WRITE_SIZE * 1.5));
    List<Packet> packets1 = createPackets(data);
    List<Packet> packets2 = createPackets(data);

    for (int i = 0; i < packets1.size(); i++) {
      stream.processPacket(packets1.get(i));
      if (i == packets1.size() - 1) {
        break;
      }
      stream.processPacket(packets2.get(i));
    }
    ArgumentCaptor<DeviceMessage> messageCaptor = ArgumentCaptor.forClass(DeviceMessage.class);
    verify(mockMessageReceivedListener).onMessageReceived(messageCaptor.capture());
    assertThat(Arrays.equals(data, messageCaptor.getValue().getMessage())).isTrue();

    stream.setMessageReceivedListener(mockMessageReceivedListener);
    stream.processPacket(Iterables.getLast(packets2));
    verify(mockMessageReceivedListener, times(2)).onMessageReceived(messageCaptor.capture());
    assertThat(Arrays.equals(data, messageCaptor.getValue().getMessage())).isTrue();
  }

  @Test
  public void processPacket_doesNotNotifyOfNewMessageIfNotAllPacketsReceived()
      throws InterruptedException {
    stream.setMessageReceivedListener(mockMessageReceivedListener);
    stream.setMessageReceivedErrorListener(mockErrorListener);
    byte[] data = ByteUtils.randomBytes((int) (WRITE_SIZE * 1.5));
    List<Packet> packets = createPackets(data);
    for (int i = 0; i < packets.size() - 1; i++) {
      stream.processPacket(packets.get(i));
    }
    verify(mockMessageReceivedListener, never()).onMessageReceived(any());
    verify(mockErrorListener, never()).onMessageReceivedError(any());
  }

  @Test
  public void processPacket_ignoresDuplicatePacket() {
    byte[] data = ByteUtils.randomBytes((int) (WRITE_SIZE * 2.5));
    stream.setMessageReceivedListener(mockMessageReceivedListener);
    ArgumentCaptor<DeviceMessage> messageCaptor = ArgumentCaptor.forClass(
        DeviceMessage.class);
    List<Packet> packets = createPackets(data);
    for (int i = 0; i < packets.size(); i++) {
      stream.processPacket(packets.get(i));
      stream.processPacket(packets.get(i)); // Process each packet twice.
    }
    verify(mockMessageReceivedListener).onMessageReceived(messageCaptor.capture());
    assertThat(Arrays.equals(data, messageCaptor.getValue().getMessage())).isTrue();
  }

  @Test
  public void processPacket_packetBeforeExpectedRangeNotifiesMessageError()
      throws InterruptedException {
    stream.setMessageReceivedErrorListener(mockErrorListener);
    List<Packet> packets = createPackets(ByteUtils.randomBytes((int) (WRITE_SIZE * 2.5)));
    stream.processPacket(packets.get(0));
    stream.processPacket(packets.get(1));
    stream.processPacket(packets.get(0));
    verify(mockErrorListener).onMessageReceivedError(any(IllegalStateException.class));
  }

  @Test
  public void processPacket_packetAfterExpectedNotifiesMessageError() throws InterruptedException {
    stream.setMessageReceivedErrorListener(mockErrorListener);
    List<Packet> packets = createPackets(ByteUtils.randomBytes((int) (WRITE_SIZE * 1.5)));
    stream.processPacket(packets.get(1));
    verify(mockErrorListener).onMessageReceivedError(any(IllegalStateException.class));
  }


  @NonNull
  private static List<Packet> createPackets(byte[] data) {
    try {
      Message message =
          Message.newBuilder()
              .setPayload(ByteString.copyFrom(data))
              .setOperation(OperationType.CLIENT_MESSAGE)
              .build();
      return PacketFactory.makePackets(
          message.toByteArray(), ThreadLocalRandom.current().nextInt(), WRITE_SIZE);
    } catch (Exception e) {
      assertWithMessage("Uncaught exception while making packets.").fail();
      return new ArrayList<>();
    }
  }

  private void processMessage(byte[] data) {
    List<Packet> packets = createPackets(data);
    for (Packet packet : packets) {
      stream.processPacket(packet);
    }
  }
}
