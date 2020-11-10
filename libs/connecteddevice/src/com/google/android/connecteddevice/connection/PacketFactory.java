package com.google.android.connecteddevice.connection;

import static com.google.android.connecteddevice.util.SafeLog.loge;
import static java.lang.Math.min;

import androidx.annotation.VisibleForTesting;
import com.google.android.companionprotos.PacketProto.Packet;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Factory for creating {@link Packet} protos. */
class PacketFactory {
  private static final String TAG = "PacketFactory";

  /** The size in bytes of a {@code fixed32} field in the proto. */
  private static final int FIXED_32_SIZE = 4;

  /**
   * The bytes needed to encode the field number in the proto.
   *
   * <p>Since the {@link Packet} only has 4 fields, it will only take 1 additional byte to encode.
   */
  private static final int FIELD_NUMBER_ENCODING_SIZE = 1;

  /** The size in bytes of field {@code packet_number}. The proto field is a {@code fixed32}. */
  private static final int PACKET_NUMBER_ENCODING_SIZE = FIXED_32_SIZE + FIELD_NUMBER_ENCODING_SIZE;

  /**
   * Split given data if necessary to fit within the given {@code maxSize}.
   *
   * @param payload The payload to potentially split across multiple {@link Packet}s.
   * @param messageId The unique id for identifying message.
   * @param maxSize The maximum size of each chunk.
   * @return A list of {@link Packet}s.
   * @throws PacketFactoryException if an error occurred during the splitting of data.
   */
  static List<Packet> makePackets(byte[] payload, int messageId, int maxSize)
      throws PacketFactoryException {
    List<Packet> blePackets = new ArrayList<>();
    int payloadSize = payload.length;
    int totalPackets = getTotalPacketNumber(messageId, payloadSize, maxSize);
    int maxPayloadSize =
        maxSize - getPacketHeaderSize(totalPackets, messageId, min(payloadSize, maxSize));

    int start = 0;
    int end = min(payloadSize, maxPayloadSize);
    for (int packetNum = 1; packetNum <= totalPackets; packetNum++) {
      blePackets.add(
          Packet.newBuilder()
              .setPacketNumber(packetNum)
              .setTotalPackets(totalPackets)
              .setMessageId(messageId)
              .setPayload(ByteString.copyFrom(Arrays.copyOfRange(payload, start, end)))
              .build());
      start = end;
      end = min(start + maxPayloadSize, payloadSize);
    }
    return blePackets;
  }

  /**
   * Compute the header size for the {@link Packet} proto in bytes. This method assumes that the
   * proto contains a payload.
   */
  @VisibleForTesting
  static int getPacketHeaderSize(int totalPackets, int messageId, int payloadSize) {
    return FIXED_32_SIZE
        + FIELD_NUMBER_ENCODING_SIZE
        + getEncodedSize(totalPackets)
        + FIELD_NUMBER_ENCODING_SIZE
        + getEncodedSize(messageId)
        + FIELD_NUMBER_ENCODING_SIZE
        + getEncodedSize(payloadSize)
        + FIELD_NUMBER_ENCODING_SIZE;
  }

  /** Compute the total packets required to encode a payload of the given size. */
  @VisibleForTesting
  static int getTotalPacketNumber(int messageId, int payloadSize, int maxSize)
      throws PacketFactoryException {
    int headerSizeWithoutTotalPackets =
        FIXED_32_SIZE
            + FIELD_NUMBER_ENCODING_SIZE
            + getEncodedSize(messageId)
            + FIELD_NUMBER_ENCODING_SIZE
            + getEncodedSize(min(payloadSize, maxSize))
            + FIELD_NUMBER_ENCODING_SIZE;

    for (int value = 1; value <= PACKET_NUMBER_ENCODING_SIZE; value++) {
      int packetHeaderSize = headerSizeWithoutTotalPackets + value + FIELD_NUMBER_ENCODING_SIZE;
      int maxPayloadSize = maxSize - packetHeaderSize;
      if (maxPayloadSize < 0) {
        throw new PacketFactoryException("Packet header size too large.");
      }
      int totalPackets = (int) Math.ceil(payloadSize / (double) maxPayloadSize);
      if (getEncodedSize(totalPackets) == value) {
        return totalPackets;
      }
    }

    loge(
        TAG,
        "Cannot get valid total packet number for message: messageId: "
            + messageId
            + ", payloadSize: "
            + payloadSize
            + ", maxSize: "
            + maxSize);
    throw new PacketFactoryException("No valid total packet number.");
  }

  /**
   * This method implements Protocol Buffers encoding algorithm.
   *
   * <p>Computes the number of bytes that would be needed to store a 32-bit variant.
   *
   * @param value the data that need to be encoded
   * @return the size of the encoded data
   * @see <a href="https://developers.google.com/protocol-buffers/docs/encoding#varints">Protocol
   *     Buffers Encoding</a>
   */
  private static int getEncodedSize(int value) {
    if (value < 0) {
      return 10;
    }
    if ((value & (~0 << 7)) == 0) {
      return 1;
    }
    if ((value & (~0 << 14)) == 0) {
      return 2;
    }
    if ((value & (~0 << 21)) == 0) {
      return 3;
    }
    if ((value & (~0 << 28)) == 0) {
      return 4;
    }
    return 5;
  }

  private PacketFactory() {}
}
