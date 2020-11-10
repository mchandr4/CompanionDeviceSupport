package com.google.android.connecteddevice.notificationmsg;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.provider.Settings;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.notificationmsg.common.BaseNotificationDelegate;
import com.google.android.connecteddevice.notificationmsg.common.ConversationKey;
import com.google.android.connecteddevice.notificationmsg.common.ConversationNotificationInfo;
import com.google.android.connecteddevice.notificationmsg.common.Message;
import com.google.android.connecteddevice.notificationmsg.common.ProjectionStateListener;
import com.google.android.connecteddevice.notificationmsg.common.SenderKey;
import com.google.android.connecteddevice.notificationmsg.common.Utils;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg.Action;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg.AvatarIconSync;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg.CarToPhoneMessage;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg.ClearAppDataRequest;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg.ConversationNotification;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg.MapEntry;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg.MessagingStyleMessage;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg.PhoneToCarMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Posts Message notifications sent from the {@link ConnectedDevice}, and relays user interaction
 * with the messages back to the device.
 */
public class NotificationMsgDelegate extends BaseNotificationDelegate {
  private static final String TAG = "NotificationMsgDelegate";

  /** Key for the Reply string in a {@link MapEntry}. */
  protected static final String REPLY_KEY = "REPLY";
  /**
   * Value for {@link ClearAppDataRequest#getMessagingAppPackageName()}, representing when all
   * messaging applications' data should be removed.
   */
  protected static final String REMOVE_ALL_APP_DATA = "ALL";

  private static final AudioAttributes AUDIO_ATTRIBUTES =
      new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build();

  private final Map<String, NotificationChannelWrapper> appNameToChannel = new HashMap<>();

  /**
   * The Bluetooth Device address of the connected device. NOTE: this is NOT the same as {@link
   * ConnectedDevice#getDeviceId()}.
   */
  private String connectedDeviceBluetoothAddress;
  /** Maps a Bitmap of a sender's Large Icon to the sender's unique key for 1-1 conversations. */
  protected final Map<SenderKey, Bitmap> oneOnOneConversationAvatarMap = new HashMap<>();

  /** Tracks whether a projection application is active in the foreground. */
  private ProjectionStateListener projectionStateListener;

  private final int defaultIconResourceId;
  private final int defaultColor;
  private final int[] colors;
  private final int fontColor;
  private final Typeface typeface;
  private final Drawable defaultAvatar;
  private final String defaultDisplayName;
  private final String groupTitleSeparator;
  private final float letterToTileRatio;
  private final int contentTextResourceId;

  public NotificationMsgDelegate(
      Context context,
      int bitmapSize,
      float cornerRadiusPercent,
      int avatarNumberOfLetters,
      int defaultIconResourceId,
      int defaultColor,
      int[] colors,
      int fontColor,
      Typeface typeface,
      Drawable defaultAvatar,
      String defaultDisplayName,
      String groupTitleSeparator,
      float letterToTileRatio,
      int contentTextResourceId) {
    super(
        context, /* useLetterTile */ false, bitmapSize, cornerRadiusPercent, avatarNumberOfLetters);
    projectionStateListener = new ProjectionStateListener(context);
    this.defaultIconResourceId = defaultIconResourceId;
    this.defaultColor = defaultColor;
    this.colors = colors;
    this.fontColor = fontColor;
    this.typeface = typeface;
    this.defaultAvatar = defaultAvatar;
    this.defaultDisplayName = defaultDisplayName;
    this.groupTitleSeparator = groupTitleSeparator;
    this.letterToTileRatio = letterToTileRatio;
    this.contentTextResourceId = contentTextResourceId;
  }

  public void onMessageReceived(ConnectedDevice device, PhoneToCarMessage message) {
    String notificationKey = message.getNotificationKey();

    switch (message.getMessageDataCase()) {
      case CONVERSATION:
        initializeNewConversation(device, message.getConversation(), notificationKey);
        return;
      case MESSAGE:
        initializeNewMessage(device.getDeviceId(), message.getMessage(), notificationKey);
        return;
      case STATUS_UPDATE:
        // TODO (b/144924164): implement Action Request tracking logic.
        return;
      case AVATAR_ICON_SYNC:
        storeIcon(
            new ConversationKey(device.getDeviceId(), notificationKey),
            message.getAvatarIconSync());
        return;
      case PHONE_METADATA:
        connectedDeviceBluetoothAddress = message.getPhoneMetadata().getBluetoothDeviceAddress();
        return;
      case CLEAR_APP_DATA_REQUEST:
        clearAppData(
            device.getDeviceId(), message.getClearAppDataRequest().getMessagingAppPackageName());
        return;
      case FEATURE_ENABLED_STATE_CHANGE:
        // TODO(b/150326327): implement enabled state change behavior.
        return;
      case MESSAGEDATA_NOT_SET:
        logw(TAG, "PhoneToCarMessage: message data not set!");
    }
  }

  protected CarToPhoneMessage dismiss(ConversationKey convoKey) {
    super.dismissInternal(convoKey);
    // TODO(b/144924164): add a request id to the action.
    Action action =
        Action.newBuilder()
            .setActionName(Action.ActionName.DISMISS)
            .setNotificationKey(convoKey.getSubKey())
            .build();
    return CarToPhoneMessage.newBuilder()
        .setNotificationKey(convoKey.getSubKey())
        .setActionRequest(action)
        .build();
  }

  protected CarToPhoneMessage markAsRead(ConversationKey convoKey) {
    excludeFromNotification(convoKey);
    // TODO(b/144924164): add a request id to the action.
    Action action =
        Action.newBuilder()
            .setActionName(Action.ActionName.MARK_AS_READ)
            .setNotificationKey(convoKey.getSubKey())
            .build();
    return CarToPhoneMessage.newBuilder()
        .setNotificationKey(convoKey.getSubKey())
        .setActionRequest(action)
        .build();
  }

  protected CarToPhoneMessage reply(ConversationKey convoKey, String message) {
    // TODO(b/144924164): add a request id to the action.
    MapEntry entry = MapEntry.newBuilder().setKey(REPLY_KEY).setValue(message).build();
    Action action =
        Action.newBuilder()
            .setActionName(Action.ActionName.REPLY)
            .setNotificationKey(convoKey.getSubKey())
            .addMapEntry(entry)
            .build();
    return CarToPhoneMessage.newBuilder()
        .setNotificationKey(convoKey.getSubKey())
        .setActionRequest(action)
        .build();
  }

  protected void onDestroy() {
    // Erase all the notifications and local data, so that no user data stays on the device
    // after the feature is stopped.
    cleanupMessagesAndNotifications(key -> true);
    projectionStateListener.destroy();
    oneOnOneConversationAvatarMap.clear();
    appNameToChannel.clear();
    connectedDeviceBluetoothAddress = null;
  }

  protected void onDeviceDisconnected(String deviceId) {
    connectedDeviceBluetoothAddress = null;
    cleanupMessagesAndNotifications(key -> key.matches(deviceId));
    oneOnOneConversationAvatarMap
        .entrySet()
        .removeIf(conversationKey -> conversationKey.getKey().matches(deviceId));
  }

  private void initializeNewConversation(
      ConnectedDevice device, ConversationNotification notification, String notificationKey) {
    String deviceAddress = device.getDeviceId();
    ConversationKey convoKey = new ConversationKey(deviceAddress, notificationKey);

    if (!Utils.isValidConversationNotification(notification, /* isShallowCheck= */ false)) {
      logd(TAG, "Failed to initialize new Conversation, object missing required fields");
      return;
    }

    ConversationNotificationInfo convoInfo;
    if (notificationInfos.containsKey(convoKey)) {
      logw(TAG, "Conversation already exists! " + notificationKey);
      convoInfo = notificationInfos.get(convoKey);
    } else {
      convoInfo =
          ConversationNotificationInfo.createConversationNotificationInfo(
              device.getDeviceName(), device.getDeviceId(), notification, notificationKey);
      notificationInfos.put(convoKey, convoInfo);
    }

    String appDisplayName = convoInfo.getAppDisplayName();

    List<MessagingStyleMessage> messages =
        notification.getMessagingStyle().getMessagingStyleMsgList();
    MessagingStyleMessage latestMessage = messages.get(0);
    for (MessagingStyleMessage messagingStyleMessage : messages) {
      createNewMessage(deviceAddress, messagingStyleMessage, convoKey);
      if (messagingStyleMessage.getTimestamp() > latestMessage.getTimestamp()) {
        latestMessage = messagingStyleMessage;
      }
    }
    postNotification(
        convoKey,
        convoInfo,
        getChannelId(appDisplayName),
        getAvatarIcon(convoKey, latestMessage),
        contentTextResourceId,
        defaultDisplayName,
        groupTitleSeparator,
        defaultIconResourceId,
        defaultColor,
        colors,
        fontColor,
        typeface,
        defaultAvatar,
        letterToTileRatio);
  }

  private void initializeNewMessage(
      String deviceAddress, MessagingStyleMessage messagingStyleMessage, String notificationKey) {
    ConversationKey convoKey = new ConversationKey(deviceAddress, notificationKey);
    if (!notificationInfos.containsKey(convoKey)) {
      logw(TAG, "Conversation not found for notification: " + notificationKey);
      return;
    }

    if (!Utils.isValidMessagingStyleMessage(messagingStyleMessage)) {
      logd(TAG, "Failed to initialize new Message, object missing required fields");
      return;
    }

    createNewMessage(deviceAddress, messagingStyleMessage, convoKey);
    ConversationNotificationInfo convoInfo = notificationInfos.get(convoKey);

    postNotification(
        convoKey,
        convoInfo,
        getChannelId(convoInfo.getAppDisplayName()),
        getAvatarIcon(convoKey, messagingStyleMessage),
        contentTextResourceId,
        defaultDisplayName,
        groupTitleSeparator,
        defaultIconResourceId,
        defaultColor,
        colors,
        fontColor,
        typeface,
        defaultAvatar,
        letterToTileRatio);
  }

  @Nullable
  private Bitmap getAvatarIcon(ConversationKey convoKey, MessagingStyleMessage message) {
    ConversationNotificationInfo notificationInfo = notificationInfos.get(convoKey);
    if (!notificationInfo.isGroupConvo()) {
      return oneOnOneConversationAvatarMap.get(
          SenderKey.createSenderKey(convoKey, message.getSender()));
    } else if (!message.getSender().getAvatar().isEmpty()) {
      byte[] iconArray = message.getSender().getAvatar().toByteArray();
      return BitmapFactory.decodeByteArray(iconArray, 0, iconArray.length);
    }
    return null;
  }

  private void storeIcon(ConversationKey convoKey, AvatarIconSync iconSync) {
    if (!Utils.isValidAvatarIconSync(iconSync) || !notificationInfos.containsKey(convoKey)) {
      logw(TAG, "storeIcon: invalid AvatarIconSync obj or no conversation found.");
      return;
    }
    if (notificationInfos.get(convoKey).isGroupConvo()) {
      return;
    }
    byte[] iconArray = iconSync.getPerson().getAvatar().toByteArray();
    Bitmap bitmap = BitmapFactory.decodeByteArray(iconArray, /* offset= */ 0, iconArray.length);
    if (bitmap != null) {
      oneOnOneConversationAvatarMap.put(
          SenderKey.createSenderKey(convoKey, iconSync.getPerson()), bitmap);
    } else {
      logw(TAG, "storeIcon: Bitmap could not be created from byteArray");
    }
  }

  private String getChannelId(String appDisplayName) {
    if (!appNameToChannel.containsKey(appDisplayName)) {
      appNameToChannel.put(appDisplayName, new NotificationChannelWrapper(appDisplayName));
    }
    return appNameToChannel
        .get(appDisplayName)
        .getChannelId(
            projectionStateListener.isProjectionInActiveForeground(
                connectedDeviceBluetoothAddress));
  }

  private void createNewMessage(
      String deviceAddress, MessagingStyleMessage messagingStyleMessage, ConversationKey convoKey) {
    String appPackageName = notificationInfos.get(convoKey).getAppPackageName();
    Message message =
        Message.parseFromMessage(
            deviceAddress,
            messagingStyleMessage,
            SenderKey.createSenderKey(convoKey, messagingStyleMessage.getSender()));
    addMessageToNotificationInfo(message, convoKey);
    AvatarIconSync iconSync =
        AvatarIconSync.newBuilder()
            .setPerson(messagingStyleMessage.getSender())
            .setMessagingAppPackageName(appPackageName)
            .build();
    storeIcon(convoKey, iconSync);
  }

  private void clearAppData(String deviceId, String packageName) {
    if (!packageName.equals(REMOVE_ALL_APP_DATA)) {
      // Clearing data for specific package names is not supported since this use case
      // is not needed right now.
      logw(TAG, "clearAppData not supported for arg: " + packageName);
      return;
    }
    cleanupMessagesAndNotifications(key -> key.matches(deviceId));
    oneOnOneConversationAvatarMap
        .entrySet()
        .removeIf(conversationKey -> conversationKey.getKey().matches(deviceId));
  }

  /** Creates notification channels per unique messaging application. */
  private class NotificationChannelWrapper {
    private static final String SILENT_CHANNEL_NAME_SUFFIX = "-no-hun";
    private final String importantChannelId;
    private final String silentChannelId;

    NotificationChannelWrapper(String appDisplayName) {
      importantChannelId = generateNotificationChannelId();
      setupImportantNotificationChannel(importantChannelId, appDisplayName);
      silentChannelId = generateNotificationChannelId();
      setupSilentNotificationChannel(silentChannelId, appDisplayName + SILENT_CHANNEL_NAME_SUFFIX);
    }

    /**
     * Returns the channel id based on whether the notification should have a heads-up notification
     * and an alert sound.
     */
    String getChannelId(boolean showSilently) {
      if (showSilently) {
        return silentChannelId;
      }
      return importantChannelId;
    }

    private void setupImportantNotificationChannel(String channelId, String channelName) {
      NotificationChannel msgChannel =
          new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
      msgChannel.setDescription(channelName);
      msgChannel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AUDIO_ATTRIBUTES);
      notificationManager.createNotificationChannel(msgChannel);
    }

    private void setupSilentNotificationChannel(String channelId, String channelName) {
      NotificationChannel msgChannel =
          new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW);
      notificationManager.createNotificationChannel(msgChannel);
    }

    private String generateNotificationChannelId() {
      return NotificationMsgService.NOTIFICATION_MSG_CHANNEL_ID
          + "|"
          + NotificationChannelIdGenerator.generateChannelId();
    }
  }

  /** Helper class that generates unique IDs per Notification Channel. */
  static class NotificationChannelIdGenerator {
    private static int nextNotificationChannelId = 0;

    private NotificationChannelIdGenerator() {}

    static int generateChannelId() {
      return ++nextNotificationChannelId;
    }
  }

  @VisibleForTesting
  void setNotificationManager(NotificationManager manager) {
    notificationManager = manager;
  }

  @VisibleForTesting
  void setProjectionStateListener(ProjectionStateListener listener) {
    projectionStateListener = listener;
  }
}
