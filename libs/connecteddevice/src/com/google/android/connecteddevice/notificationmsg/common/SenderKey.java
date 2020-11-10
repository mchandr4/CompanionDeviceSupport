package com.google.android.connecteddevice.notificationmsg.common;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg.Person;

/**
 * {@link CompositeKey} subclass used to give each contact on all the connected devices a unique
 * Key.
 */
public class SenderKey extends CompositeKey {
  /** Creates a senderkey for SMS, MMS, and {@link NotificationMsg}. */
  private SenderKey(String deviceId, String senderName, String keyMetadata) {
    super(deviceId, senderName + "/" + keyMetadata);
  }

  /**
   * Returns the SenderKey for the BluetoothMapClient intent. This should be unique for each contact
   * per device.
   */
  public static SenderKey createSenderKey(Intent intent) {
    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    return new SenderKey(
        device.getAddress(), Utils.getSenderName(intent), Utils.getSenderUri(intent));
  }

  /**
   * Returns the SenderKey based on a {@link NotificationMsg} DAO. This key is only guaranteed to be
   * unique for a 1-1 conversation. If the ConversationKey is for a group conversation, the
   * senderKey will not be unique if more than one participant in the conversation share the same
   * name.
   */
  public static SenderKey createSenderKey(ConversationKey convoKey, Person person) {
    return new SenderKey(convoKey.getDeviceId(), person.getName(), convoKey.getSubKey());
  }
}
