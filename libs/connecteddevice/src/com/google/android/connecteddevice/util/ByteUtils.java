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

package com.google.android.connecteddevice.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidParameterException;
import java.security.SecureRandom;
import java.util.UUID;

/** Utility classes for manipulating bytes. */
public final class ByteUtils {
  // https://developer.android.com/reference/java/util/UUID
  private static final int UUID_LENGTH = 16;

  private static final int LONG_BYTES = 8;

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private ByteUtils() {}

  /**
   * Returns a byte buffer corresponding to the passed long argument.
   *
   * @param primitive data to convert format.
   */
  public static byte[] longToBytes(long primitive) {
    ByteBuffer buffer = ByteBuffer.allocate(LONG_BYTES);
    buffer.putLong(primitive);
    return buffer.array();
  }

  /**
   * Returns a byte buffer corresponding to the passed long argument.
   *
   * @param array data to convert format.
   */
  public static long bytesToLong(byte[] array) {
    ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE / Byte.SIZE);
    buffer.put(array);
    buffer.flip();
    return buffer.getLong();
  }

  /**
   * Returns a String in Hex format that is formed from the bytes in the byte array Useful for
   * debugging
   *
   * @param array the byte array
   * @return the Hex string version of the input byte array
   */
  public static String byteArrayToHexString(byte[] array) {
    StringBuilder sb = new StringBuilder(array.length * 2);
    for (byte b : array) {
      sb.append(String.format("%02X", b));
    }
    return sb.toString();
  }

  /**
   * Returns a byte array which is formed from the hex string.
   *
   * @param hex the hex string ready to be converted
   * @return the byte array version of the hex string
   */
  public static byte[] hexStringToByteArray(String hex) {
    int len = hex.length();
    if (len % 2 != 0) {
      throw new InvalidParameterException(
          "Hex string must have a length that is evenly divisible by two.");
    }
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] =
          (byte)
              ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
    }
    return data;
  }

  /**
   * Convert UUID to Big Endian byte array
   *
   * @param uuid UUID to convert
   * @return the byte array representing the UUID
   */
  @NonNull
  public static byte[] uuidToBytes(@NonNull UUID uuid) {

    return ByteBuffer.allocate(UUID_LENGTH)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(uuid.getMostSignificantBits())
        .putLong(uuid.getLeastSignificantBits())
        .array();
  }

  /**
   * Convert Big Endian byte array to UUID
   *
   * @param bytes byte array to convert
   * @return the UUID representing the byte array, or null if not a valid UUID
   */
  @Nullable
  public static UUID bytesToUUID(@NonNull byte[] bytes) {
    if (bytes.length != UUID_LENGTH) {
      return null;
    }

    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    return new UUID(buffer.getLong(), buffer.getLong());
  }

  /**
   * Generate a {@link byte[]} with random bytes.
   *
   * @param size of array to generate.
   * @return generated {@link byte[]}.
   */
  @NonNull
  public static byte[] randomBytes(int size) {
    byte[] array = new byte[size];
    SECURE_RANDOM.nextBytes(array);
    return array;
  }

  /**
   * Concatentate the given 2 byte arrays
   *
   * @param a input array 1
   * @param b input array 2
   * @return concatenated array of arrays 1 and 2
   */
  @Nullable
  public static byte[] concatByteArrays(@Nullable byte[] a, @Nullable byte[] b) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      if (a != null) {
        outputStream.write(a);
      }
      if (b != null) {
        outputStream.write(b);
      }
    } catch (IOException e) {
      return null;
    }
    return outputStream.toByteArray();
  }
}
