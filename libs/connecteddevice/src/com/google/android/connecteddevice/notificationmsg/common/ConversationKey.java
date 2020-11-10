package com.google.android.connecteddevice.notificationmsg.common;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * {@link CompositeKey} subclass used to give each conversation on all the connected devices a
 * unique Key.
 */
public class ConversationKey extends CompositeKey implements Parcelable {

  public ConversationKey(String deviceId, String key) {
    super(deviceId, key);
  }

  /** Creates a ConversationKey from a BluetoothMapClient intent. */
  public static ConversationKey createConversationKey(Intent intent) {
    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    String senderUri = Utils.getSenderUri(intent);
    String senderName = Utils.getSenderName(intent);
    String subKey = senderName + "/" + senderUri;
    if (Utils.isGroupConversation(intent)) {
      subKey = Utils.getInclusiveRecipientsUrisList(intent).toString();
    }
    return new ConversationKey(device.getAddress(), subKey);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(getDeviceId());
    dest.writeString(getSubKey());
  }

  /** Creates {@link ConversationKey} instances from {@link Parcel} sources. */
  public static final Parcelable.Creator<ConversationKey> CREATOR =
      new Parcelable.Creator<ConversationKey>() {
        @Override
        public ConversationKey createFromParcel(Parcel source) {
          return new ConversationKey(source.readString(), source.readString());
        }

        @Override
        public ConversationKey[] newArray(int size) {
          return new ConversationKey[size];
        }
      };
}
