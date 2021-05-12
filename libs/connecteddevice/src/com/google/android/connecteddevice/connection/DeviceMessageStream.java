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
import com.google.android.companionprotos.DeviceMessageProto.Message;
import com.google.android.companionprotos.OperationProto.OperationType;
import com.google.android.companionprotos.PacketProto.Packet;
import com.google.android.connecteddevice.model.DeviceMessage;
import com.google.android.connecteddevice.util.ByteUtils;
import com.google.android.connecteddevice.util.EventLog;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistryLite;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Abstract class which includes common logic of different types of {@link DeviceMessageStream}. */
public abstract class DeviceMessageStream {

  private static final String TAG = "DeviceMessageStream";

  private final ArrayDeque<Packet> packetQueue = new ArrayDeque<>();

  private final Map<Integer, ByteArrayOutputStream> pendingData = new HashMap<>();

  // messageId -> nextExpectedPacketNumber
  private final Map<Integer, Integer> pendingPacketNumber = new HashMap<>();

  private final MessageIdGenerator messageIdGenerator = new MessageIdGenerator();

  private final AtomicBoolean isSendingInProgress = new AtomicBoolean(false);

  // TODO(b/169869570): Remove when Protocol interface is available
  private final AtomicBoolean isConnectionResolved = new AtomicBoolean(false);

  private int maxWriteSize;

  /** Listener which will be notified when there is new {@link DeviceMessage} received. */
  private MessageReceivedListener messageReceivedListener;

  private DataReceivedListener dataReceivedListener;

  /** Listener which will be notified when there is error parsing the received message. */
  private MessageReceivedErrorListener messageReceivedErrorListener;

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

  public final void setDataReceivedListener(@Nullable DataReceivedListener listener) {
    logd(TAG, "Setting dataReceivedListener");
    dataReceivedListener = listener;
  }

  /**
   * Set the given listener to be notified when there was an error during receiving message from the
   * client. If listener is {@code null}, clear.
   */
  public final void setMessageReceivedErrorListener(
      @Nullable MessageReceivedErrorListener listener) {
    messageReceivedErrorListener = listener;
  }

  public final void setConnectionResolved(boolean isResolved) {
    isConnectionResolved.set(isResolved);
  }

  /**
   * Notify the {@code messageReceivedListener} about the message received if it is not {@code
   * null}.
   *
   * @param deviceMessage The message received.
   */
  protected final void notifyMessageReceivedListener(@NonNull DeviceMessage deviceMessage) {
    if (messageReceivedListener != null) {
      messageReceivedListener.onMessageReceived(deviceMessage);
    }
  }

  // TODO(b/169869570): Remove when Protocol interface is available
  public final void notifyDataReceivedErrorListener(Exception e) {
    notifyMessageReceivedErrorListener(e);
  }

  private void notifyDataReceivedListener(byte[] data) {
    logd(TAG, "Notifying dataReceivedListener");
    if (dataReceivedListener != null) {
      dataReceivedListener.onDataReceived(data);
    }
  }

  /**
   * Notify the {@code messageReceivedErrorListener} about the message received if it is not {@code
   * null}.
   *
   * @param e The exception happened when parsing the received message.
   */
  protected final void notifyMessageReceivedErrorListener(Exception e) {
    if (messageReceivedErrorListener != null) {
      messageReceivedErrorListener.onMessageReceivedError(e);
    }
  }

  /**
   * Send {@link DeviceMessage} to remote connected devices.
   *
   * @param deviceMessage The message which need to be sent
   */
  public void writeMessage(@NonNull DeviceMessage deviceMessage) {
    Message.Builder builder =
        Message.newBuilder()
            .setOperation(OperationType.forNumber(deviceMessage.getOperationType().getValue()))
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

  /**
   * Send raw data to remote connected devices.
   *
   * @param data message to send
   * @throws IllegalArgumentException if {@code data} is larger than {@code maxWriteSize}
   */
  // TODO(b/169869570): Move to Protocol interface when available
  public void writeRawBytes(@NonNull byte[] data) {
    if (data.length > maxWriteSize) {
      throw new IllegalArgumentException(
          "Length of data is greater than max write size, send as a Message instead.");
    }

    send(data);
  }

  /** Process incoming data from stream. */
  protected final synchronized void onDataReceived(byte[] data) {
    logd(TAG, "Data was received, isConnectionResolved = " + isConnectionResolved.get());
    if (isConnectionResolved.get()) {
      Packet packet;
        try {
          packet = Packet.parseFrom(data, ExtensionRegistryLite.getEmptyRegistry());
        } catch (IOException e) {
          loge(TAG, "Can not parse packet from client.", e);
          notifyMessageReceivedErrorListener(e);
          return;
        }
        processPacket(packet);
      return;
    }

    // The version and capabilities exchange use raw bytes rather than device messages, so notify
    // raw data listener rather than message listener.
    notifyDataReceivedListener(data);
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

    if (packetNumber == 1) {
      EventLog.onMessageStarted(messageId);
    }

    if (packet.getPacketNumber() != packet.getTotalPackets()) {
      return;
    }

    byte[] messageBytes = currentPayloadStream.toByteArray();
    EventLog.onMessageFullyReceived(messageId, messageBytes.length);
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

    DeviceMessage deviceMessage = new DeviceMessage(
        ByteUtils.bytesToUUID(message.getRecipient().toByteArray()),
        message.getIsPayloadEncrypted(),
        DeviceMessage.OperationType.fromValue(message.getOperation().getNumber()),
        message.getPayload().toByteArray());
    notifyMessageReceivedListener(deviceMessage);
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
     */
    void onMessageReceived(@NonNull DeviceMessage deviceMessage);
  }

  /** Listener to be invoked when a message of raw data is received from the client. */
  // TODO(b/169869570): Move to Protocol interface when available
  public interface DataReceivedListener {
    /**
     * Called when a message of raw data is received from the client.
     *
     * @param data the data received from the client
     */
    void onDataReceived(@NonNull byte[] data);
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

}
