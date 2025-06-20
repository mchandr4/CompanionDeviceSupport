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

package com.google.android.connecteddevice.model;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Representation of a message between two devices. */
public class DeviceMessage implements Parcelable {

  /** Operation type for a message. */
  public enum OperationType {
    UNKNOWN(0),
    ENCRYPTION_HANDSHAKE(2),
    ACK(3),
    CLIENT_MESSAGE(4),
    QUERY(5),
    QUERY_RESPONSE(6),
    // DISCONNECT should be created at the protocol level. It is not necessary at DeviceMessage
    // level. This enum only exists to match the proto field.
    DISCONNECT(7);

    private final int value;

    OperationType(int value) {
      this.value = value;
    }

    /** Get the {@link Integer} representation of this type. */
    public int getValue() {
      return value;
    }

    /** Returns the type matching the value. */
    public static OperationType fromValue(int value) {
      for (OperationType type : OperationType.values()) {
        if (type.value == value) {
          return type;
        }
      }
      return UNKNOWN;
    }
  }

  private final UUID recipient;

  private final boolean isMessageEncrypted;

  private final OperationType operationType;

  private byte[] message;

  private int originalMessageSize;

  /** Create a new outgoing message. */
  public static DeviceMessage createOutgoingMessage(
      @Nullable UUID recipient,
      boolean isMessageEncrypted,
      OperationType operationType,
      @NonNull byte[] message) {
    // Original size is populated directly on the outgoing message proto based on compression
    // success and does not need to be provided by the caller.
    return new DeviceMessage(
        recipient, isMessageEncrypted, operationType, message, /* originalMessageSize= */ 0);
  }

  /** Create a new incoming message. */
  public static DeviceMessage createIncomingMessage(
      @Nullable UUID recipient,
      boolean isMessageEncrypted,
      OperationType operationType,
      @NonNull byte[] message,
      int originalMessageSize) {
    return new DeviceMessage(
        recipient, isMessageEncrypted, operationType, message, originalMessageSize);
  }

  private DeviceMessage(
      @Nullable UUID recipient,
      boolean isMessageEncrypted,
      OperationType operationType,
      @NonNull byte[] message,
      int originalMessageSize) {
    this.recipient = recipient;
    this.isMessageEncrypted = isMessageEncrypted;
    this.operationType = operationType;
    this.message = message;
    this.originalMessageSize = originalMessageSize;
  }

  private DeviceMessage(Parcel in) {
    this(
        UUID.fromString(in.readString()),
        in.readBoolean(),
        OperationType.fromValue(in.readInt()),
        in.createByteArray(),
        in.readInt());
  }

  /** Returns the recipient for this message. {@code null} if no recipient set. */
  @Nullable
  public UUID getRecipient() {
    return recipient;
  }

  /** Returns whether this message is encrypted. */
  public boolean isMessageEncrypted() {
    return isMessageEncrypted;
  }

  /** Returns the operation type of the message. */
  public OperationType getOperationType() {
    return operationType;
  }

  /** Returns the message payload. */
  @NonNull
  public byte[] getMessage() {
    return message;
  }

  /** Returns the number of bytes in the original message prior to compressing. */
  public int getOriginalMessageSize() {
    return originalMessageSize;
  }

  /** Set the message payload. */
  public void setMessage(@NonNull byte[] message) {
    this.message = message;
  }

  /** Set the original message size in bytes. */
  public void setOriginalMessageSize(int numBytes) {
    originalMessageSize = numBytes;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof DeviceMessage)) {
      return false;
    }
    DeviceMessage deviceMessage = (DeviceMessage) obj;
    return Objects.equals(recipient, deviceMessage.recipient)
        && isMessageEncrypted == deviceMessage.isMessageEncrypted
        && Arrays.equals(message, deviceMessage.message)
        && originalMessageSize == deviceMessage.originalMessageSize
        && operationType == deviceMessage.operationType;
  }

  @Override
  public int hashCode() {
    return 31 * Objects.hash(recipient, isMessageEncrypted, originalMessageSize, operationType)
        + Arrays.hashCode(message);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel parcel, int i) {
    parcel.writeString(recipient.toString());
    parcel.writeBoolean(isMessageEncrypted);
    parcel.writeInt(operationType.getValue());
    parcel.writeByteArray(message);
    parcel.writeInt(originalMessageSize);
  }

  public static final Parcelable.Creator<DeviceMessage> CREATOR =
      new Parcelable.Creator<DeviceMessage>() {

        @Override
        public DeviceMessage createFromParcel(Parcel source) {
          return new DeviceMessage(source);
        }

        @Override
        public DeviceMessage[] newArray(int size) {
          return new DeviceMessage[size];
        }
      };
}
