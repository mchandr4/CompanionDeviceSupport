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

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.companionprotos.CapabilitiesExchangeProto.CapabilitiesExchange;
import com.google.android.companionprotos.CapabilitiesExchangeProto.CapabilitiesExchange.OobChannel;
import com.google.android.companionprotos.DeviceMessageProto.Message;
import com.google.android.companionprotos.OperationProto.OperationType;
import com.google.android.companionprotos.PacketProto.Packet;
import com.google.android.companionprotos.VersionExchangeProto.VersionExchange;
import com.google.android.connecteddevice.util.ByteUtils;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistryLite;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Abstract class which includes common logic of different types of {@link DeviceMessageStream}. */
public abstract class DeviceMessageStream {

  private static final String TAG = "DeviceMessageStream";

  public static final int MIN_SECURITY_VERSION_FOR_CAPABILITIES_EXCHANGE = 3;

  // Messaging and security versions are dictated by this device.
  @VisibleForTesting public static final int MESSAGING_VERSION = 3;
  @VisibleForTesting public static final int MIN_SECURITY_VERSION = 2;
  // TODO(b/175066810): Increment the security version to 3 only when capabilities exchange is fully
  // implemented on IHU and mobile.
  @VisibleForTesting public static final int MAX_SECURITY_VERSION = 2;

  private static final ImmutableList<OobChannel> SUPPORTED_OOB_CHANNELS = ImmutableList.of();

  private final ArrayDeque<Packet> packetQueue = new ArrayDeque<>();

  private final Map<Integer, ByteArrayOutputStream> pendingData = new HashMap<>();

  // messageId -> nextExpectedPacketNumber
  private final Map<Integer, Integer> pendingPacketNumber = new HashMap<>();

  private final MessageIdGenerator messageIdGenerator = new MessageIdGenerator();

  private enum MessageExchangeState {
    UNKNOWN,
    WAITING_FOR_VERSION_EXCHANGE,
    WAITING_FOR_CAPABILITIES_EXCHANGE,
    WAITING_FOR_MESSAGE_PACKETS
  }

  private MessageExchangeState messageExchangeState =
      MessageExchangeState.WAITING_FOR_VERSION_EXCHANGE;

  private final AtomicBoolean isSendingInProgress = new AtomicBoolean(false);

  private int maxWriteSize;

  /** Listener which will be notified when there is new {@link DeviceMessage} received. */
  private MessageReceivedListener messageReceivedListener;

  /** Listener which will be notified when there is error parsing the received message. */
  private MessageReceivedErrorListener messageReceivedErrorListener;

  /** Listener which will be notified when the version is exchanged. */
  private VersionExchangedListener versionExchangedListener;

  /** Listener which will be notified when the capabilities are exchanged. */
  private CapabilitiesExchangedListener capabilitiesExchangedListener;

  protected DeviceMessageStream(int defaultMaxWriteSize) {
    maxWriteSize = defaultMaxWriteSize;
  }

  /**
   * Send data to the connected device. Note: {@link #sendCompleted()} must be called when the bytes
   * have successfully been sent to indicate the stream is ready to send more data.
   */
  protected abstract void send(byte[] data);

  /**
   * Set the given listener to be notified when a new message was received from the client. If
   * listener is {@code null}, clear.
   */
  public final void setMessageReceivedListener(@Nullable MessageReceivedListener listener) {
    messageReceivedListener = listener;
  }

  /**
   * Set the given listener to be notified when there was an error during receiving message from the
   * client. If listener is {@code null}, clear.
   */
  public final void setMessageReceivedErrorListener(
      @Nullable MessageReceivedErrorListener listener) {
    messageReceivedErrorListener = listener;
  }

  /**
   * Set the given listener to be notified when the version was exchanged between the two devices.
   * If listener is {@code null}, clear.
   */
  public final void setVersionExchangedListener(@Nullable VersionExchangedListener listener) {
    versionExchangedListener = listener;
  }

  /**
   * Set the given listener to be notified when the capabilities were exchanged between the two
   * devices. If listener is {@code null}, clear.
   */
  public final void setCapabilitiesExchangedListener(
      @Nullable CapabilitiesExchangedListener listener) {
    logd(TAG, "Setting capabilities exchanged listener");
    capabilitiesExchangedListener = listener;
  }

  /**
   * Notify the {@code mMessageReceivedListener} about the message received if it is not {@code
   * null}.
   *
   * @param deviceMessage The message received.
   * @param operationType The operation type of the message.
   */
  protected final void notifyMessageReceivedListener(
      @NonNull DeviceMessage deviceMessage, OperationType operationType) {
    if (messageReceivedListener != null) {
      messageReceivedListener.onMessageReceived(deviceMessage, operationType);
    }
  }

  /**
   * Notify the {@code mMessageReceivedErrorListener} about the message received if it is not {@code
   * null}.
   *
   * @param e The exception happened when parsing the received message.
   */
  protected final void notifyMessageReceivedErrorListener(Exception e) {
    if (messageReceivedErrorListener != null) {
      messageReceivedErrorListener.onMessageReceivedError(e);
    }
  }

  @VisibleForTesting
  public final void notifyVersionExchanged(int messagingVersion, int securityVersion) {
    if (versionExchangedListener != null) {
      versionExchangedListener.onVersionExchanged(messagingVersion, securityVersion);
    }
  }

  private final void notifyOobCapabilitiesExchanged(@NonNull List<OobChannel> oobChannels) {
    logd(
        TAG,
        "Capabilitied exchanged listener is " + capabilitiesExchangedListener == null
            ? "null"
            : "not null");
    if (capabilitiesExchangedListener != null) {
      capabilitiesExchangedListener.onOobCapabilitiesExchanged(oobChannels);
    }
  }

  /**
   * Writes the given message to the write characteristic of this stream with operation type {@code
   * CLIENT_MESSAGE}.
   *
   * <p>This method will handle the chunking of messages based on the max write size.
   *
   * @param deviceMessage The data object contains recipient, isPayloadEncrypted and message.
   */
  public void writeMessage(@NonNull DeviceMessage deviceMessage) {
    writeMessage(deviceMessage, OperationType.CLIENT_MESSAGE);
  }

  /**
   * Send {@link DeviceMessage} to remote connected devices.
   *
   * @param deviceMessage The message which need to be sent
   * @param operationType The operation type of current message
   */
  public void writeMessage(@NonNull DeviceMessage deviceMessage, OperationType operationType) {
    Message.Builder builder =
        Message.newBuilder()
            .setOperation(operationType)
            .setIsPayloadEncrypted(deviceMessage.isMessageEncrypted())
            .setPayload(ByteString.copyFrom(deviceMessage.getMessage()))
            .setOriginalSize(deviceMessage.getOriginalMessageSize());

    UUID recipient = deviceMessage.getRecipient();
    if (recipient != null) {
      builder.setRecipient(ByteString.copyFrom(ByteUtils.uuidToBytes(recipient)));
    }

    Message message = builder.build();
    byte[] rawBytes = message.toByteArray();
    List<Packet> packets;
    try {
      packets = PacketFactory.makePackets(rawBytes, messageIdGenerator.next(), maxWriteSize);
    } catch (PacketFactoryException e) {
      loge(TAG, "Error while creating message packets.", e);
      return;
    }
    packetQueue.addAll(packets);
    writeNextMessageInQueue();
  }

  private void writeNextMessageInQueue() {
    if (packetQueue.isEmpty()) {
      logd(TAG, "No more packets to send.");
      return;
    }
    boolean isLockAcquired = isSendingInProgress.compareAndSet(false, true);
    if (!isLockAcquired) {
      logd(TAG, "Unable to send packet at this time.");
      return;
    }

    Packet packet = packetQueue.remove();
    logd(
        TAG,
        "Writing packet "
            + packet.getPacketNumber()
            + " of "
            + packet.getTotalPackets()
            + " for "
            + packet.getMessageId()
            + ".");
    send(packet.toByteArray());
  }

  /** Process incoming data from stream. */
  protected final synchronized void onDataReceived(byte[] data) {
    switch (messageExchangeState) {
      case WAITING_FOR_VERSION_EXCHANGE:
        processVersionExchange(data);
        break;
      case WAITING_FOR_CAPABILITIES_EXCHANGE:
        processCapabilitiesExchange(data);
        break;
      case WAITING_FOR_MESSAGE_PACKETS:
        Packet packet;
        try {
          packet = Packet.parseFrom(data, ExtensionRegistryLite.getEmptyRegistry());
        } catch (IOException e) {
          loge(TAG, "Can not parse packet from client.", e);
          notifyMessageReceivedErrorListener(e);
          return;
        }
        processPacket(packet);
        break;
      case UNKNOWN:
        loge(TAG, "Unknown state, don't know what to do with this message.");
    }
  }

  protected final void processPacket(@NonNull Packet packet) {
    int messageId = packet.getMessageId();
    int packetNumber = packet.getPacketNumber();
    int expectedPacket = pendingPacketNumber.getOrDefault(messageId, 1);
    if (packetNumber == expectedPacket - 1) {
      logw(
          TAG,
          "Received duplicate packet "
              + packet.getPacketNumber()
              + " for message "
              + messageId
              + ". Ignoring.");
      return;
    }
    if (packetNumber != expectedPacket) {
      loge(TAG, "Received unexpected packet " + packetNumber + " for message " + messageId + ".");
      notifyMessageReceivedErrorListener(
          new IllegalStateException("Packet received out of order."));
      return;
    }
    pendingPacketNumber.put(messageId, packetNumber + 1);

    ByteArrayOutputStream currentPayloadStream =
        pendingData.getOrDefault(messageId, new ByteArrayOutputStream());
    pendingData.putIfAbsent(messageId, currentPayloadStream);

    byte[] payload = packet.getPayload().toByteArray();
    try {
      currentPayloadStream.write(payload);
    } catch (IOException e) {
      loge(TAG, "Error writing packet to stream.", e);
      notifyMessageReceivedErrorListener(e);
      return;
    }
    logd(
        TAG,
        "Parsed packet "
            + packet.getPacketNumber()
            + " of "
            + packet.getTotalPackets()
            + " for message "
            + messageId
            + ". Writing "
            + payload.length
            + ".");

    if (packet.getPacketNumber() != packet.getTotalPackets()) {
      return;
    }

    byte[] messageBytes = currentPayloadStream.toByteArray();
    pendingData.remove(messageId);

    logd(
        TAG,
        "Received complete device message " + messageId + " of " + messageBytes.length + " bytes.");
    Message message;
    try {
      message = Message.parseFrom(messageBytes, ExtensionRegistryLite.getEmptyRegistry());
    } catch (IOException e) {
      loge(TAG, "Cannot parse device message from client.", e);
      notifyMessageReceivedErrorListener(e);
      return;
    }

    DeviceMessage deviceMessage = new DeviceMessage(message);
    notifyMessageReceivedListener(deviceMessage, message.getOperation());
  }

  /** The maximum amount of bytes that can be written in a single packet. */
  public final void setMaxWriteSize(@IntRange(from = 1) int maxWriteSize) {
    if (maxWriteSize <= 0) {
      return;
    }
    this.maxWriteSize = maxWriteSize;
  }

  /** Indicate current send operation has completed. */
  public void sendCompleted() {
    isSendingInProgress.set(false);
    writeNextMessageInQueue();
  }

  private void processVersionExchange(@NonNull byte[] value) {
    VersionExchange versionExchange;
    try {
      versionExchange =
          VersionExchange.parseFrom(value, ExtensionRegistryLite.getEmptyRegistry());
    } catch (IOException e) {
      loge(TAG, "Could not parse version exchange message", e);
      notifyMessageReceivedErrorListener(e);

      return;
    }
    int minMessagingVersion = versionExchange.getMinSupportedMessagingVersion();
    int maxMessagingVersion = versionExchange.getMaxSupportedMessagingVersion();
    int minSecurityVersion = versionExchange.getMinSupportedSecurityVersion();
    int maxSecurityVersion = versionExchange.getMaxSupportedSecurityVersion();
    if (minMessagingVersion > MESSAGING_VERSION
        || maxMessagingVersion < MESSAGING_VERSION
        || minSecurityVersion > MAX_SECURITY_VERSION
        || maxSecurityVersion < MIN_SECURITY_VERSION) {
      loge(
          TAG,
          "Unsupported message version for min "
              + minMessagingVersion
              + " and max "
              + maxMessagingVersion
              + " or security version for "
              + minSecurityVersion
              + " and max "
              + maxSecurityVersion
              + ".");
      notifyMessageReceivedErrorListener(new IllegalStateException("Unsupported version."));
      return;
    }

    VersionExchange headunitVersion =
        VersionExchange.newBuilder()
            .setMinSupportedMessagingVersion(MESSAGING_VERSION)
            .setMaxSupportedMessagingVersion(MESSAGING_VERSION)
            .setMinSupportedSecurityVersion(MIN_SECURITY_VERSION)
            .setMaxSupportedSecurityVersion(MAX_SECURITY_VERSION)
            .build();
    send(headunitVersion.toByteArray());
    logd(TAG, "Sent supported version to the phone.");

    int maxSharedSecurityVersion = Math.min(maxSecurityVersion, MAX_SECURITY_VERSION);
    notifyVersionExchanged(MESSAGING_VERSION, maxSharedSecurityVersion);
    messageExchangeState =
        maxSharedSecurityVersion >= MIN_SECURITY_VERSION_FOR_CAPABILITIES_EXCHANGE
            ? MessageExchangeState.WAITING_FOR_CAPABILITIES_EXCHANGE
            : MessageExchangeState.WAITING_FOR_MESSAGE_PACKETS;
  }

  // TODO(b/178045908): Move capabilities exchange logic out of DeviceMessageStream
  private void processCapabilitiesExchange(@NonNull byte[] value) {
    CapabilitiesExchange capabilitiesExchange;
    try {
      capabilitiesExchange =
          CapabilitiesExchange.parseFrom(value, ExtensionRegistryLite.getEmptyRegistry());
    } catch (IOException e) {
      loge(TAG, "Could not parse capabilities exchange message", e);
      notifyMessageReceivedErrorListener(e);
      return;
    }

    CapabilitiesExchange headunitCapabilities =
        CapabilitiesExchange.newBuilder()
            .addAllSupportedOobChannels(SUPPORTED_OOB_CHANNELS)
            .build();
    send(headunitCapabilities.toByteArray());
    logd(TAG, "Sent supported capabilities to the phone.");

    List<OobChannel> sharedSupportedOobChanels = capabilitiesExchange.getSupportedOobChannelsList();
    if (sharedSupportedOobChanels == null) {
      sharedSupportedOobChanels = new ArrayList<>();
    }
    sharedSupportedOobChanels.retainAll(SUPPORTED_OOB_CHANNELS);
    notifyOobCapabilitiesExchanged(sharedSupportedOobChanels);

    messageExchangeState = MessageExchangeState.WAITING_FOR_MESSAGE_PACKETS;
  }

  /** A generator of unique IDs for messages. */
  private static class MessageIdGenerator {
    private final AtomicInteger messageId = new AtomicInteger(0);

    int next() {
      int current = messageId.getAndIncrement();
      messageId.compareAndSet(Integer.MAX_VALUE, 0);
      return current;
    }
  }

  /** Listener to be invoked when a complete message is received from the client. */
  public interface MessageReceivedListener {

    /**
     * Called when a complete message is received from the client.
     *
     * @param deviceMessage The message received from the client.
     * @param operationType The {@link OperationType} of the received message.
     */
    void onMessageReceived(@NonNull DeviceMessage deviceMessage, OperationType operationType);
  }

  /** Listener to be invoked when there was an error during receiving message from the client. */
  public interface MessageReceivedErrorListener {
    /**
     * Called when there was an error during receiving message from the client.
     *
     * @param exception The error.
     */
    void onMessageReceivedError(@NonNull Exception exception);
  }

  /** Listener to be invoked when the version exchange has been completed on the IHU. */
  public interface VersionExchangedListener {
    /**
     * Called when version exchange has been completed on the IHU.
     *
     * @param messagingVersion The supported messaging version agreed upon by both devices
     * @param securityVersion The supported security version agreed upon by both devices
     */
    void onVersionExchanged(int messagingVersion, int securityVersion);
  }

  /** Listener to be invoked when the capabilities exchange has been completed on the IHU. */
  public interface CapabilitiesExchangedListener {
    /**
     * Called when the capabilities exchange has been completed on the IHU.
     *
     * @param oobChannels The list of out of band channels supported by both devices
     */
    void onOobCapabilitiesExchanged(@NonNull List<OobChannel> oobChannels);
  }
}
