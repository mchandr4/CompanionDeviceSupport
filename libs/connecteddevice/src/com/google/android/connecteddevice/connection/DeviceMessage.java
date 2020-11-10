package com.google.android.connecteddevice.connection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.companionprotos.DeviceMessageProto.Message;
import com.google.android.connecteddevice.util.ByteUtils;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Holds the needed data from a {@link Message}. */
public class DeviceMessage {

  private static final String TAG = "DeviceMessage";

  private final UUID recipient;

  private final boolean isMessageEncrypted;

  private byte[] message;

  private int originalMessageSize;

  public DeviceMessage(
      @Nullable UUID recipient, boolean isMessageEncrypted, @NonNull byte[] message) {
    this.recipient = recipient;
    this.isMessageEncrypted = isMessageEncrypted;
    this.message = message;
  }

  public DeviceMessage(@NonNull Message message) {
    recipient = ByteUtils.bytesToUUID(message.getRecipient().toByteArray());
    isMessageEncrypted = message.getIsPayloadEncrypted();
    this.message = message.getPayload().toByteArray();
    originalMessageSize = message.getOriginalSize();
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

  /** Returns the message payload. */
  @Nullable
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
        && originalMessageSize == deviceMessage.originalMessageSize;
  }

  @Override
  public int hashCode() {
    return 31 * Objects.hash(recipient, isMessageEncrypted, originalMessageSize)
        + Arrays.hashCode(message);
  }
}
