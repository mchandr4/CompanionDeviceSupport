package com.google.android.connecteddevice.notificationmsg.common;

import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg.ConversationNotification;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg.MessagingStyle;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg.PhoneToCarMessage;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a conversation notification's metadata that is shared between the conversation's
 * messages. Note, each {@link ConversationKey} should map to exactly one
 * ConversationNotificationInfo object.
 */
public class ConversationNotificationInfo {
  private static final String TAG = "CMC.ConvoNotifInfo";
  private static int nextNotificationId = 0;
  final int notificationId = nextNotificationId++;

  private final String deviceName;
  private final String deviceId;
  // This is always the sender name for SMS Messages from Bluetooth MAP.
  private String convoTitle;
  private final boolean isGroupConvo;

  /** Only used for {@link NotificationMsg} conversations. */
  @Nullable private final String notificationKey;

  @Nullable private final String appDisplayName;
  private final String appPackageName;
  @Nullable private final String userDisplayName;
  @Nullable private final Icon appIcon;
  /** Uris of all members in a MMS Group Conversation. */
  @Nullable private final List<String> ccRecipientsUris;

  public final ArrayList<MessageKey> messageKeys = new ArrayList<>();

  /**
   * Creates a ConversationNotificationInfo for a {@link NotificationMsg}. Returns {@code null} if
   * the {@link ConversationNotification} is missing required fields.
   */
  @Nullable
  public static ConversationNotificationInfo createConversationNotificationInfo(
      @NonNull String deviceName,
      @NonNull String deviceId,
      @NonNull ConversationNotification conversation,
      @NonNull String notificationKey) {
    MessagingStyle messagingStyle = conversation.getMessagingStyle();

    if (!Utils.isValidConversationNotification(conversation, /* isShallowCheck= */ true)) {
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        throw new IllegalArgumentException(
            "ConversationNotificationInfo is missing required fields");
      } else {
        logw(TAG, "ConversationNotificationInfo is missing required fields");
        return null;
      }
    }

    byte[] iconBytes = conversation.getAppIcon().toByteArray();
    Icon appIcon = Icon.createWithData(iconBytes, 0, iconBytes.length);

    return new ConversationNotificationInfo(
        deviceName,
        deviceId,
        messagingStyle.getConvoTitle(),
        messagingStyle.getIsGroupConvo(),
        notificationKey,
        conversation.getMessagingAppDisplayName(),
        conversation.getMessagingAppPackageName(),
        messagingStyle.getUserDisplayName(),
        appIcon,
        /* ccUris= */ null);
  }
  /** Creates a ConversationNotificationInfo for a BluetoothMapClient intent. */
  public static ConversationNotificationInfo createConversationNotificationInfo(
      Intent intent, String conversationTitle, String appPackageName, @Nullable Icon appIcon) {
    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

    return new ConversationNotificationInfo(
        device.getName(),
        device.getAddress(),
        conversationTitle,
        Utils.isGroupConversation(intent), /* notificationKey */
        null,
        /* appDisplayName */ null,
        appPackageName, /* userDisplayName */
        null,
        appIcon,
        Utils.getInclusiveRecipientsUrisList(intent));
  }

  private ConversationNotificationInfo(
      @Nullable String deviceName,
      String deviceId,
      String convoTitle,
      boolean isGroupConvo,
      @Nullable String notificationKey,
      @Nullable String appDisplayName,
      String appPackageName,
      @Nullable String userDisplayName,
      @Nullable Icon appIcon,
      @Nullable List<String> ccUris) {
    boolean missingDeviceId = (deviceId == null);
    boolean missingTitle = (convoTitle == null);
    if (missingDeviceId || missingTitle) {
      StringBuilder builder = new StringBuilder("Missing required fields:");
      if (missingDeviceId) {
        builder.append(" deviceId");
      }
      if (missingTitle) {
        builder.append(" convoTitle");
      }
      throw new IllegalArgumentException(builder.toString());
    }
    this.deviceName = deviceName;
    this.deviceId = deviceId;
    this.convoTitle = convoTitle;
    this.isGroupConvo = isGroupConvo;
    this.notificationKey = notificationKey;
    this.appDisplayName = appDisplayName;
    this.appPackageName = appPackageName;
    this.userDisplayName = userDisplayName;
    this.appIcon = appIcon;
    this.ccRecipientsUris = ccUris;
  }

  /** Returns the id that should be used for this object's {@link android.app.Notification}. */
  public int getNotificationId() {
    return notificationId;
  }

  /** Returns the friendly name of the device that received the notification. */
  public String getDeviceName() {
    return deviceName;
  }

  /** Returns the address of the device that received the notification. */
  public String getDeviceId() {
    return deviceId;
  }

  /**
   * Returns the conversation title of this notification. If this notification came from MAP
   * profile, the title will be the Sender's name.
   */
  public String getConvoTitle() {
    return convoTitle;
  }

  /** Update the conversation title. */
  public void setConvoTitle(String newTitle) {
    convoTitle = newTitle;
  }

  /** Returns {@code true} if this message is in a group conversation. */
  public boolean isGroupConvo() {
    return isGroupConvo;
  }

  /**
   * Returns the key if this conversation is based on a {@link ConversationNotification}. Refer to
   * {@link PhoneToCarMessage#getNotificationKey()} for more info.
   */
  @Nullable
  public String getNotificationKey() {
    return notificationKey;
  }

  /**
   * Returns the display name of the application that posted this notification if this object is
   * based on a {@link ConversationNotification}.
   */
  @Nullable
  public String getAppDisplayName() {
    return appDisplayName;
  }

  /** Returns the package name of the application that posted this notification. */
  public String getAppPackageName() {
    return appPackageName;
  }

  /**
   * Returns the User Display Name if this object is based on a @link ConversationNotification}.
   * This is needed for {@link android.app.Notification.MessagingStyle}.
   */
  @Nullable
  public String getUserDisplayName() {
    return userDisplayName;
  }

  /** Returns the app's icon of the application that posted this notification. */
  @Nullable
  public Icon getAppIcon() {
    return appIcon;
  }

  public MessageKey getLastMessageKey() {
    return Iterables.getLast(messageKeys);
  }

  /**
   * Returns the sorted URIs of all the participants of a MMS/SMS/RCS conversation. Returns {@code
   * null} if this is based on a {@link NotificationMsg} conversation.
   */
  @Nullable
  public List<String> getCcRecipientsUris() {
    return ccRecipientsUris;
  }
}
