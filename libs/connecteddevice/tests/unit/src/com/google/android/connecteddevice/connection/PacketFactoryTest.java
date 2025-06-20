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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.companionprotos.PacketProto.Packet;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PacketFactoryTest {
  @Test
  public void testGetHeaderSize() {
    // 1 byte to encode the ID, 1 byte for the field number.
    int messageId = 1;
    int messageIdEncodingSize = 2;

    // 1 byte for the payload size, 1 byte for the field number.
    int payloadSize = 2;
    int payloadSizeEncodingSize = 2;

    // 1 byte for total packets, 1 byte for field number.
    int totalPackets = 5;
    int totalPacketsEncodingSize = 2;

    // Packet number if a fixed32, so 4 bytes + 1 byte for field number.
    int packetNumberEncodingSize = 5;

    int expectedHeaderSize =
        messageIdEncodingSize
            + payloadSizeEncodingSize
            + totalPacketsEncodingSize
            + packetNumberEncodingSize;

    assertThat(PacketFactory.getPacketHeaderSize(totalPackets, messageId, payloadSize))
        .isEqualTo(expectedHeaderSize);
  }

  @Test
  public void testGetTotalPackets_withVarintSize1_returnsCorrectPackets()
      throws PacketFactoryException {
    int messageId = 1;
    int maxSize = 49;
    int payloadSize = 100;

    // This leaves us 40 bytes to use for the payload and its encoding size. Assuming a varint
    // of size 1 means it takes 2 bytes to encode its value. This leaves 38 bytes for the
    // payload. ceil(payloadSize/38) gives the total packets.
    int expectedTotalPackets = 3;

    assertThat(PacketFactory.getTotalPacketNumber(messageId, payloadSize, maxSize))
        .isEqualTo(expectedTotalPackets);
  }

  @Test
  public void testGetTotalPackets_withVarintSize2_returnsCorrectPackets()
      throws PacketFactoryException {
    int messageId = 1;
    int maxSize = 49;
    int payloadSize = 6000;

    // This leaves us 40 bytes to use for the payload and its encoding size. Assuming a varint
    // of size 2 means it takes 3 bytes to encode its value. This leaves 37 bytes for the
    // payload. ceil(payloadSize/37) gives the total packets.
    int expectedTotalPackets = 163;

    assertThat(PacketFactory.getTotalPacketNumber(messageId, payloadSize, maxSize))
        .isEqualTo(expectedTotalPackets);
  }

  @Test
  public void testGetTotalPackets_withVarintSize3_returnsCorrectPackets()
      throws PacketFactoryException {
    int messageId = 1;
    int maxSize = 49;
    int payloadSize = 1000000;

    // This leaves us 40 bytes to use for the payload and its encoding size. Assuming a varint
    // of size 3 means it takes 4 bytes to encode its value. This leaves 36 bytes for the
    // payload. ceil(payloadSize/36) gives the total packets.
    int expectedTotalPackets = 27778;

    assertThat(PacketFactory.getTotalPacketNumber(messageId, payloadSize, maxSize))
        .isEqualTo(expectedTotalPackets);
  }

  @Test
  public void testGetTotalPackets_withVarintSize4_returnsCorrectPackets()
      throws PacketFactoryException {
    int messageId = 1;
    int maxSize = 49;
    int payloadSize = 178400320;

    // This leaves us 40 bytes to use for the payload and its encoding size. Assuming a varint
    // of size 4 means it takes 5 bytes to encode its value. This leaves 35 bytes for the
    // payload. ceil(payloadSize/35) gives the total packets.
    int expectedTotalPackets = 5097152;

    assertThat(PacketFactory.getTotalPacketNumber(messageId, payloadSize, maxSize))
        .isEqualTo(expectedTotalPackets);
  }

  @Test
  public void testMakePackets_correctlyChunksPayload() throws Exception {
    // Payload of size 100, but maxSize of 1000 to ensure it fits.
    byte[] payload = makePayload(/* length= */ 100);
    int maxSize = 1000;

    List<Packet> packets = PacketFactory.makePackets(payload, /* messageId= */ 1, maxSize);

    assertThat(packets).hasSize(1);

    ByteArrayOutputStream reconstructedPayload = new ByteArrayOutputStream();

    // Combine together all the payloads within the BlePackets.
    for (Packet packet : packets) {
      packet.getPayload().writeTo(reconstructedPayload);
    }

    assertThat(reconstructedPayload.toByteArray()).isEqualTo(payload);
  }

  @Test
  public void testMakePackets_correctlyChunksSplitPayload() throws Exception {
    // Payload size of 10000 but max size of 50 to ensure the payload is split.
    byte[] payload = makePayload(/* length= */ 10000);
    int maxSize = 50;

    List<Packet> packets = PacketFactory.makePackets(payload, /* messageId= */ 1, maxSize);

    assertThat(packets.size()).isGreaterThan(1);

    ByteArrayOutputStream reconstructedPayload = new ByteArrayOutputStream();

    // Combine together all the payloads within the BlePackets.
    for (Packet packet : packets) {
      packet.getPayload().writeTo(reconstructedPayload);
    }

    assertThat(reconstructedPayload.toByteArray()).isEqualTo(payload);
  }

  /** Creates a byte array of the given length, populated with random bytes. */
  private static byte[] makePayload(int length) {
    byte[] payload = new byte[length];
    new Random().nextBytes(payload);
    return payload;
  }
}
