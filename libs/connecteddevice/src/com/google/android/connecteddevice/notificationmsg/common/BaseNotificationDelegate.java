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

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.common.base.Strings.isNullOrEmpty;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Action;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Base Interface for Message Notification Delegates. Any Delegate who chooses to extend from this
 * class is responsible for:
 *
 * <p>device connection logic
 *
 * <p>sending and receiving messages from the connected devices
 *
 * <p>creation of {@link ConversationNotificationInfo} and {@link Message} objects
 *
 * <p>creation of {@link ConversationKey}, {@link MessageKey}, {@link SenderKey}
 *
 * <p>loading of largeIcons for each Sender per device
 *
 * <p>Mark-as-Read and Reply functionality
 */
@SuppressWarnings("PendingIntentMutability") // SaferPendingIntent is not available in AOSP
public class BaseNotificationDelegate {
  private static final String TAG = "BaseNotificationDelegate";

  /** Used to reply to message. */
  public static final String ACTION_REPLY = "com.android.car.messenger.common.ACTION_REPLY";

  /** Used to clear notification state when user dismisses notification. */
  public static final String ACTION_DISMISS_NOTIFICATION =
      "com.android.car.messenger.common.ACTION_DISMISS_NOTIFICATION";

  /** Used to mark a notification as read. */
  public static final String ACTION_MARK_AS_READ =
      "com.android.car.messenger.common.ACTION_MARK_AS_READ";

  /* EXTRAS */
  /** Key under which the {@link ConversationKey} is provided. */
  public static final String EXTRA_CONVERSATION_KEY =
      "com.android.car.messenger.common.EXTRA_CONVERSATION_KEY";

  /**
   * The resultKey of the {@link RemoteInput} which is sent in the reply callback {@link
   * Notification.Action}.
   */
  public static final String EXTRA_REMOTE_INPUT_KEY =
      "com.android.car.messenger.common.REMOTE_INPUT_KEY";

    /** Used to override default notification large icon. **/
  private static final String EXTRA_USE_LAUNCHER_ICON =
      "com.android.car.notification.EXTRA_USE_LAUNCHER_ICON";

  private static final String REPLY = "Reply";
  private static final String MARK_AS_READ = "Mark As Read";

  protected final Context context;
  protected NotificationManager notificationManager;
  protected final boolean useLetterTile;

  /**
   * Maps a conversation's Notification Metadata to the conversation's unique key. The extending
   * class should always keep this map updated with the latest new/updated notification information
   * before calling {@link BaseNotificationDelegate#postNotification( ConversationKey,
   * ConversationNotificationInfo, String)}.
   */
  protected final Map<ConversationKey, ConversationNotificationInfo> notificationInfos =
      new HashMap<>();

  /**
   * Maps a conversation's Notification Builder to the conversation's unique key. When the
   * conversation gets updated, this builder should be retrieved, updated, and reposted.
   */
  private final Map<ConversationKey, NotificationCompat.Builder> notificationBuilders =
      new HashMap<>();

  /**
   * Maps a message's metadata with the message's unique key. The extending class should always keep
   * this map updated with the latest message information before calling {@link
   * BaseNotificationDelegate#postNotification( ConversationKey, ConversationNotificationInfo,
   * String)}.
   */
  protected final Map<MessageKey, Message> messages = new HashMap<>();

  private final int bitmapSize;
  private final float cornerRadiusPercent;
  private final int avatarNumberOfLetters;

  /**
   * Constructor for the BaseNotificationDelegate class.
   *
   * @param context of the calling application.
   * @param useLetterTile whether a letterTile icon should be used if no avatar icon is given.
   */
  public BaseNotificationDelegate(
      Context context,
      boolean useLetterTile,
      int bitmapSize,
      float cornerRadiusPercent,
      int avatarNumberOfLetters) {
    this.context = context;
    this.useLetterTile = useLetterTile;
    notificationManager =
        (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
    this.bitmapSize = bitmapSize;
    this.cornerRadiusPercent = cornerRadiusPercent;
    this.avatarNumberOfLetters = avatarNumberOfLetters;
  }

  /** Removes all messages related to the inputted predicate, and cancels their notifications. */
  public void cleanupMessagesAndNotifications(Predicate<CompositeKey> predicate) {
    clearNotifications(predicate);
    notificationBuilders.entrySet().removeIf(entry -> predicate.test(entry.getKey()));
    notificationInfos.entrySet().removeIf(entry -> predicate.test(entry.getKey()));
    messages
        .entrySet()
        .removeIf(messageKeyMapMessageEntry -> predicate.test(messageKeyMapMessageEntry.getKey()));
  }

  /**
   * Clears all notifications matching the predicate. Example method calls are when user wants to
   * clear (a) message notification(s), or when the Bluetooth device that received the messages has
   * been disconnected.
   */
  public void clearNotifications(Predicate<CompositeKey> predicate) {
    notificationInfos.forEach(
        (conversationKey, notificationInfo) -> {
          if (predicate.test(conversationKey)) {
            notificationManager.cancel(notificationInfo.getNotificationId());
          }
        });
  }

  protected void dismissInternal(ConversationKey convoKey) {
    clearNotifications(key -> key.equals(convoKey));
    excludeFromNotification(convoKey);
  }

  /**
   * Excludes messages from a notification so that the messages are not shown to the user once the
   * notification gets updated with newer messages.
   */
  protected void excludeFromNotification(ConversationKey convoKey) {
    ConversationNotificationInfo info = notificationInfos.get(convoKey);
    if (info == null) {
      logd(TAG, "ConversationNotificationInfo is null for: " + convoKey);
      return;
    }
    for (MessageKey key : info.messageKeys) {
      Message message = messages.get(key);
      message.excludeFromNotification();
    }
  }

  /**
   * Helper method to add {@link Message}s to the {@link ConversationNotificationInfo}. This should
   * be called when a new message has arrived.
   */
  protected void addMessageToNotificationInfo(Message message, ConversationKey convoKey) {
    MessageKey messageKey = new MessageKey(message);
    boolean repeatMessage = messages.containsKey(messageKey);
    messages.put(messageKey, message);
    if (!repeatMessage) {
      ConversationNotificationInfo notificationInfo = notificationInfos.get(convoKey);
      notificationInfo.messageKeys.add(messageKey);
    }
  }

  /**
   * Creates a new notification, or updates an existing notification with the latest messages, then
   * posts it. This should be called after the {@link ConversationNotificationInfo} object has been
   * created, and all of its {@link Message} objects have been linked to it.
   */
  protected void postNotification(
      ConversationKey conversationKey,
      ConversationNotificationInfo notificationInfo,
      String channelId,
      @Nullable Bitmap avatarIcon,
      int contentTextResourceId,
      String defaultDisplayName,
      String groupTitleSeparator,
      int defaultIconResourceId,
      int defaultColor,
      int[] colors,
      int fontColor,
      Typeface typeface,
      Drawable defaultAvatar,
      float letterToTileRatio) {
    boolean newNotification = !notificationBuilders.containsKey(conversationKey);

    NotificationCompat.Builder builder =
        newNotification
            ? new NotificationCompat.Builder(context, channelId)
            : notificationBuilders.get(conversationKey);
    builder.setChannelId(channelId);
    Message lastMessage = messages.get(Iterables.getLast(notificationInfo.messageKeys));

    builder.setContentTitle(notificationInfo.getConvoTitle());
    builder.setContentText(
        context
            .getResources()
            .getQuantityString(
                contentTextResourceId,
                notificationInfo.messageKeys.size(),
                notificationInfo.messageKeys.size()));

    Bundle avatarBundle = new Bundle();
    avatarBundle.putBoolean(EXTRA_USE_LAUNCHER_ICON, false);
    builder.addExtras(avatarBundle);
    if (avatarIcon != null) {
      builder.setLargeIcon(avatarIcon);
    } else if (useLetterTile) {
      BitmapDrawable drawable =
          (BitmapDrawable)
              TelecomUtils.createLetterTile(
                      context,
                      Utils.getInitials(lastMessage.getSenderName(), ""),
                      lastMessage.getSenderName(),
                      bitmapSize,
                      cornerRadiusPercent,
                      defaultColor,
                      colors,
                      fontColor,
                      typeface,
                      defaultAvatar,
                      letterToTileRatio,
                      avatarNumberOfLetters)
                  .loadDrawable(context);
      builder.setLargeIcon(drawable.getBitmap());
    }
    // Else, no avatar icon will be shown.

    builder.setWhen(lastMessage.getReceivedTime());

    // Create MessagingStyle
    String userName =
        isNullOrEmpty(notificationInfo.getUserDisplayName())
            ? defaultDisplayName
            : notificationInfo.getUserDisplayName();
    Person user = new Person.Builder().setName(userName).build();
    NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(user);
    Person sender =
        new Person.Builder()
            .setName(lastMessage.getSenderName())
            .setUri(lastMessage.getSenderContactUri())
            .build();
    notificationInfo.messageKeys.stream()
        .map(messages::get)
        .forEachOrdered(
            message -> {
              if (!message.shouldExcludeFromNotification()) {
                messagingStyle.addMessage(
                    message.getMessageText(),
                    message.getReceivedTime(),
                    notificationInfo.isGroupConvo()
                        ? new Person.Builder()
                            .setName(message.getSenderName())
                            .setUri(message.getSenderContactUri())
                            .build()
                        : sender);
              }
            });
    if (notificationInfo.isGroupConvo()) {
      messagingStyle.setConversationTitle(
          Utils.constructGroupConversationHeader(
              lastMessage.getSenderName(),
              notificationInfo.getConvoTitle(),
              groupTitleSeparator));
    }

    // We are creating this notification for the first time.
    if (newNotification) {
      builder.setCategory(Notification.CATEGORY_MESSAGE);
      if (notificationInfo.getAppIcon() != null) {
        builder.setSmallIcon(IconCompat.createFromIcon(context, notificationInfo.getAppIcon()));
        builder.setColor(notificationInfo.getAppIconColor());
      } else {
        builder.setSmallIcon(defaultIconResourceId);
      }

      builder.setShowWhen(true);
      messagingStyle.setGroupConversation(notificationInfo.isGroupConvo());

      if (notificationInfo.getAppDisplayName() != null) {
        Bundle displayName = new Bundle();
        displayName.putCharSequence(
            Notification.EXTRA_SUBSTITUTE_APP_NAME, notificationInfo.getAppDisplayName());
        builder.addExtras(displayName);
      }

      PendingIntent deleteIntent =
          createServiceIntent(
              conversationKey, notificationInfo.getNotificationId(), ACTION_DISMISS_NOTIFICATION);
      builder.setDeleteIntent(deleteIntent);

      List<Action> actions =
          buildNotificationActions(conversationKey, notificationInfo.getNotificationId());
      for (final Action action : actions) {
        builder.addAction(action);
      }
    }
    builder.setStyle(messagingStyle);

    notificationBuilders.put(conversationKey, builder);
    notificationManager.notify(notificationInfo.getNotificationId(), builder.build());
  }

  /** Can be overridden by any Delegates that have some devices that do not support reply. */
  protected boolean shouldAddReplyAction(String deviceAddress) {
    return true;
  }

  private List<Action> buildNotificationActions(
      ConversationKey conversationKey, int notificationId) {
    final int icon = android.R.drawable.ic_media_play;

    final List<NotificationCompat.Action> actionList = new ArrayList<>();

    // Reply action
    if (shouldAddReplyAction(conversationKey.getDeviceId())) {
      PendingIntent replyIntent =
          createServiceIntent(conversationKey, notificationId, ACTION_REPLY);
      actionList.add(
          new NotificationCompat.Action.Builder(icon, REPLY, replyIntent)
              .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
              .setShowsUserInterface(false)
              .addRemoteInput(
                  new androidx.core.app.RemoteInput.Builder(EXTRA_REMOTE_INPUT_KEY).build())
              .build());
    }

    // Mark-as-read Action. This will be the callback of Notification Center's "Read" action.
    PendingIntent markAsReadIntent =
        createServiceIntent(conversationKey, notificationId, ACTION_MARK_AS_READ);
    actionList.add(
        new NotificationCompat.Action.Builder(icon, MARK_AS_READ, markAsReadIntent)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build());

    return actionList;
  }

  private PendingIntent createServiceIntent(
      ConversationKey conversationKey, int notificationId, String action) {
    Intent intent =
        new Intent(context, context.getClass())
            .setAction(action)
            .setClassName(context, context.getClass().getName())
            .putExtra(EXTRA_CONVERSATION_KEY, conversationKey);

    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
    if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
        flags |= PendingIntent.FLAG_MUTABLE;
    }

    return PendingIntent.getService(context, notificationId, intent, flags);
  }
}
