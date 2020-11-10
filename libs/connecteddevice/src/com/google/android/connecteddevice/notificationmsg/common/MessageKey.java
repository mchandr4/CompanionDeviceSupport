package com.google.android.connecteddevice.notificationmsg.common;

/**
 * {@link CompositeKey} subclass used to give each message on all the connected devices a unique
 * Key.
 */
public class MessageKey extends CompositeKey {

  /** Creates a MessageKey for a {@link Message}. */
  public MessageKey(Message message) {
    super(message.getDeviceId(), message.getHandle());
  }
}
