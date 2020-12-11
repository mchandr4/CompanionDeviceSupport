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

package com.google.android.connecteddevice.notificationmsg.common;

import static com.google.android.connecteddevice.notificationmsg.common.Utils.BMC_EXTRA_MESSAGE_HANDLE;
import static com.google.android.connecteddevice.notificationmsg.common.Utils.BMC_EXTRA_MESSAGE_READ_STATUS;
import static com.google.android.connecteddevice.notificationmsg.common.Utils.BMC_EXTRA_MESSAGE_TIMESTAMP;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.util.Log;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg.MessagingStyleMessage;

/**
 * Represents a SMS, MMS, and {@link NotificationMsg}. This object is based on {@link
 * NotificationMsg}.
 */
public class Message {
  private static final String TAG = "CMC.Message";

  private final String senderName;
  private final String deviceId;
  private final String messageText;
  private final long receivedTime;
  private final boolean isReadOnPhone;
  private boolean shouldExclude;
  private final String handle;
  private final MessageType messageType;
  private final SenderKey senderKey;

  /**
   * Note: MAP messages from iOS version 12 and earlier, as well as {@link MessagingStyleMessage},
   * don't provide these.
   */
  @Nullable final String senderContactUri;

  /**
   * Describes if the message was received through Bluetooth MAP or is a {@link NotificationMsg}.
   */
  public enum MessageType {
    BLUETOOTH_MAP_MESSAGE,
    NOTIFICATION_MESSAGE
  }

  /**
   * Creates a Message based on {@link MessagingStyleMessage}. Returns {@code null} if the {@link
   * MessagingStyleMessage} is missing required fields.
   *
   * @param deviceId of the phone that received this message.
   * @param updatedMessage containing the information to base this message object off of.
   * @param senderKey of the sender of the message. Not guaranteed to be unique for all senders if
   *     this message is part of a group conversation.
   */
  @Nullable
  public static Message parseFromMessage(
      String deviceId, MessagingStyleMessage updatedMessage, SenderKey senderKey) {

    if (!Utils.isValidMessagingStyleMessage(updatedMessage)) {
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        throw new IllegalArgumentException("MessagingStyleMessage is missing required fields");
      } else {
        logw(TAG, "MessagingStyleMessage is missing required fields");
        return null;
      }
    }

    return new Message(
        updatedMessage.getSender().getName(),
        deviceId,
        updatedMessage.getTextMessage(),
        updatedMessage.getTimestamp(),
        updatedMessage.getIsRead(),
        Utils.createMessageHandle(updatedMessage),
        MessageType.NOTIFICATION_MESSAGE,
        /* senderContactUri */ null,
        senderKey);
  }

  /**
   * Creates a Message based on BluetoothMapClient intent. Returns {@code null} if the intent is
   * missing required fields.
   */
  public static Message parseFromIntent(Intent intent) {
    if (!Utils.isValidMapClientIntent(intent)) {
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        throw new IllegalArgumentException("BluetoothMapClient intent is missing required fields");
      } else {
        logw(TAG, "BluetoothMapClient intent is missing required fields");
        return null;
      }
    }
    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    String senderUri = Utils.getSenderUri(intent);

    return new Message(
        Utils.getSenderName(intent),
        device.getAddress(),
        intent.getStringExtra(android.content.Intent.EXTRA_TEXT),
        intent.getLongExtra(BMC_EXTRA_MESSAGE_TIMESTAMP, System.currentTimeMillis()),
        intent.getBooleanExtra(BMC_EXTRA_MESSAGE_READ_STATUS, false),
        intent.getStringExtra(BMC_EXTRA_MESSAGE_HANDLE),
        MessageType.BLUETOOTH_MAP_MESSAGE,
        senderUri,
        SenderKey.createSenderKey(intent));
  }

  private Message(
      String senderName,
      String deviceId,
      String messageText,
      long receivedTime,
      boolean isReadOnPhone,
      String handle,
      MessageType messageType,
      @Nullable String senderContactUri,
      SenderKey senderKey) {
    boolean missingSenderName = (senderName == null);
    boolean missingDeviceId = (deviceId == null);
    boolean missingText = (messageText == null);
    boolean missingHandle = (handle == null);
    boolean missingType = (messageType == null);
    if (missingSenderName || missingDeviceId || missingText || missingHandle || missingType) {
      StringBuilder builder = new StringBuilder("Missing required fields:");
      if (missingSenderName) {
        builder.append(" senderName");
      }
      if (missingDeviceId) {
        builder.append(" deviceId");
      }
      if (missingText) {
        builder.append(" messageText");
      }
      if (missingHandle) {
        builder.append(" handle");
      }
      if (missingType) {
        builder.append(" type");
      }
      throw new IllegalArgumentException(builder.toString());
    }
    this.senderName = senderName;
    this.deviceId = deviceId;
    this.messageText = messageText;
    this.receivedTime = receivedTime;
    this.isReadOnPhone = isReadOnPhone;
    this.shouldExclude = false;
    this.handle = handle;
    this.messageType = messageType;
    this.senderContactUri = senderContactUri;
    this.senderKey = senderKey;
  }

  /**
   * Returns the contact name as obtained from the device. If contact is in the device's
   * address-book, this is typically the contact name. Otherwise it will be the phone number.
   */
  public String getSenderName() {
    return senderName;
  }

  /** Returns the id of the device from which this message was received. */
  public String getDeviceId() {
    return deviceId;
  }

  /** Returns the actual content of the message. */
  public String getMessageText() {
    return messageText;
  }

  /**
   * Returns the milliseconds since epoch at which this message notification was received on the
   * head-unit.
   */
  public long getReceivedTime() {
    return receivedTime;
  }

  /**
   * Whether message should be included in the notification. Messages that have been read aloud on
   * the car, or that have been dismissed by the user should be excluded from the notification if/
   * when the notification gets updated. Note: this state will not be propagated to the phone.
   */
  public void excludeFromNotification() {
    shouldExclude = true;
  }

  /** Returns {@code true} if message was read on the phone before it was received on the car. */
  public boolean isReadOnPhone() {
    return isReadOnPhone;
  }

  /**
   * Returns {@code true} if message should not be included in the notification. Messages that have
   * been read aloud on the car, or that have been dismissed by the user should be excluded from the
   * notification if/when the notification gets updated.
   */
  public boolean shouldExcludeFromNotification() {
    return shouldExclude;
  }

  /**
   * Returns a unique handle/key for this message. This is used as this Message's {@link
   * MessageKey#getSubKey()} Note: this handle might only be unique for the lifetime of a device
   * connection session.
   */
  public String getHandle() {
    return handle;
  }

  /**
   * If the message came from BluetoothMapClient, this retrieves a key that is unique for each
   * contact per device. If the message came from {@link NotificationMsg}, this retrieves a key that
   * is only guaranteed to be unique per sender in a 1-1 conversation. If this message is part of a
   * group conversation, the senderKey will not be unique if more than one participant in the
   * conversation share the same name.
   */
  public SenderKey getSenderKey() {
    return senderKey;
  }

  /** Returns whether the message is a SMS/MMS or a {@link NotificationMsg}. */
  public MessageType getMessageType() {
    return messageType;
  }

  /**
   * Returns the sender's phone number available as a URI string. Note: MAP messages from iOS
   * version 12 and earlier, as well as {@link MessagingStyleMessage}, don't provide these.
   */
  @Nullable
  public String getSenderContactUri() {
    return senderContactUri;
  }

  @Override
  public String toString() {
    return "Message{"
        + " mSenderName='"
        + senderName
        + '\''
        + ", mMessageText='"
        + messageText
        + '\''
        + ", mSenderContactUri='"
        + senderContactUri
        + '\''
        + ", mReceiveTime="
        + receivedTime
        + '\''
        + ", mIsReadOnPhone= "
        + isReadOnPhone
        + '\''
        + ", mShouldExclude= "
        + shouldExclude
        + '\''
        + ", mHandle='"
        + handle
        + '\''
        + ", mSenderKey='"
        + senderKey
        + "}";
  }
}
