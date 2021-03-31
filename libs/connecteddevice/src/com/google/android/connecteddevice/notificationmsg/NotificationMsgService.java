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

package com.google.android.connecteddevice.notificationmsg;

import static com.google.android.connecteddevice.notificationmsg.common.BaseNotificationDelegate.ACTION_DISMISS_NOTIFICATION;
import static com.google.android.connecteddevice.notificationmsg.common.BaseNotificationDelegate.ACTION_MARK_AS_READ;
import static com.google.android.connecteddevice.notificationmsg.common.BaseNotificationDelegate.ACTION_REPLY;
import static com.google.android.connecteddevice.notificationmsg.common.BaseNotificationDelegate.EXTRA_CONVERSATION_KEY;
import static com.google.android.connecteddevice.notificationmsg.common.BaseNotificationDelegate.EXTRA_REMOTE_INPUT_KEY;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.notificationmsg.common.ConversationKey;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg;
import com.google.android.connecteddevice.service.MetaDataService;

/**
 * Service responsible for handling {@link NotificationMsg} messaging events from the active user's
 * securely paired {@link ConnectedDevice}s.
 */
public class NotificationMsgService extends MetaDataService {
  private static final String TAG = "NotificationMsgService";

  // TODO(b/172590500) Add proper documentation to these values.
  private static final String META_BITMAP_SIZE =
      "com.google.android.connecteddevice.notificationmsg.notification_contact_photo_size";
  private static final String META_CORNER_RADIUS =
      "com.google.android.connecteddevice.notificationmsg.contact_avatar_corner_radius_percent";
  private static final String META_AVATAR_LETTERS =
      "com.google.android.connecteddevice.notificationmsg.letters_shown_for_avatar";
  private static final String META_DEFAULT_ICON =
      "com.google.android.connecteddevice.notificationmsg.default_message_icon";
  private static final String META_TILE_DEFAULT_COLOR =
      "com.google.android.connecteddevice.notificationmsg.letter_tile_default_color";
  private static final String META_TILE_COLORS =
      "com.google.android.connecteddevice.notificationmsg.letter_tile_colors";
  private static final String META_FONT_COLOR =
      "com.google.android.connecteddevice.notificationmsg.font_color";
  private static final String META_FONT_FAMILY =
      "com.google.android.connecteddevice.notificationmsg.font_family";
  private static final String META_TEXT_STYLE =
      "com.google.android.connecteddevice.notificationmsg.text_style";
  private static final String META_DEFAULT_AVATAR =
      "com.google.android.connecteddevice.notificationmsg.default_avatar";
  private static final String META_DEFAULT_DISPLAY_NAME =
      "com.google.android.connecteddevice.notificationmsg.default_display_name";
  private static final String META_GROUP_TITLE_SEPARATOR =
      "com.google.android.connecteddevice.notificationmsg.group_title_separator";
  private static final String META_LETTER_TO_TILE_RATIO =
      "com.google.android.connecteddevice.notificationmsg.letter_to_tile_ratio";
  private static final String META_CONTENT_TEXT =
      "com.google.android.connecteddevice.notificationmsg.content_text";

  private static final float DEFAULT_CORNER_RADIUS = 0.5f;
  private static final int DEFAULT_AVATAR_LETTERS = 1;
  private static final int DEFAULT_TILE_COLOR = Color.parseColor("#cccccc");
  private static final int[] DEFAULT_TILE_COLORS = new int[] {
      Color.parseColor("#db4437"),
      Color.parseColor("#e91e63"),
      Color.parseColor("#9c27b0"),
      Color.parseColor("#673ab7"),
      Color.parseColor("#3f51b5"),
      Color.parseColor("#4285f4"),
      Color.parseColor("#039be5"),
      Color.parseColor("#0097a7"),
      Color.parseColor("#008577"),
      Color.parseColor("#0f9d58"),
      Color.parseColor("#689f38"),
      Color.parseColor("#ef6c00"),
      Color.parseColor("#ff5722"),
      Color.parseColor("#757575")
  };
  private static final int DEFAULT_FONT_COLOR = Color.parseColor("#ffffff");
  private static final String DEFAULT_FONT_FAMILY = "sans-serif-light";
  private static final int DEFAULT_TEXT_STYLE = 0;
  private static final String DEFAULT_GROUP_TITLE_SEPARATOR = "&#160;&#8226;&#160;";
  private static final float DEFAULT_LETTER_TO_TILE_RATIO = 0.67f;

  /* NOTIFICATIONS */
  static final String NOTIFICATION_MSG_CHANNEL_ID = "NOTIFICATION_MSG_CHANNEL_ID";
  private static final int SERVICE_STARTED_NOTIFICATION_ID = Integer.MAX_VALUE;
  private static final String APP_RUNNING_CHANNEL_ID = "APP_RUNNING_CHANNEL_ID";
  private static final String APP_RUNNING_CHANNEL_NAME = "APP_RUNNING_CHANNEL_ID";

  private NotificationMsgDelegate notificationMsgDelegate;
  private NotificationMsgFeature notificationMsgFeature;
  private final IBinder binder = new LocalBinder();

  /** API for interacting with {@link NotificationMsgService}. */
  public class LocalBinder extends Binder {
    NotificationMsgService getService() {
      return NotificationMsgService.this;
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  @Override
  public void onCreate() {
    super.onCreate();

    notificationMsgDelegate = createNotificationMsgDelegate();
    notificationMsgFeature = new NotificationMsgFeature(this, notificationMsgDelegate);
    notificationMsgFeature.start();
    sendServiceRunningNotification();
  }

  @Override
  public void onDestroy() {
    notificationMsgFeature.stop();
    super.onDestroy();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null || intent.getAction() == null) {
      return START_STICKY;
    }

    String action = intent.getAction();

    switch (action) {
      case ACTION_REPLY:
        handleReplyIntent(intent);
        break;
      case ACTION_DISMISS_NOTIFICATION:
        handleDismissNotificationIntent(intent);
        break;
      case ACTION_MARK_AS_READ:
        handleMarkAsReadIntent(intent);
        break;
      default:
        logw(TAG, "Unsupported action: " + action);
    }

    return START_STICKY;
  }

  /**
   * Posts a service running (silent/hidden) notification, so we don't throw ANR after service is
   * started.
   */
  private void sendServiceRunningNotification() {
    NotificationManager notificationManager = getSystemService(NotificationManager.class);
    // Create notification channel for app running notification
    NotificationChannel appRunningNotificationChannel =
        new NotificationChannel(
            APP_RUNNING_CHANNEL_ID,
            APP_RUNNING_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_MIN);
    notificationManager.createNotificationChannel(appRunningNotificationChannel);

    final Notification notification =
        new NotificationCompat.Builder(this, APP_RUNNING_CHANNEL_ID)
            .setSmallIcon(requireMetaResourceId(META_DEFAULT_ICON))
            .setContentTitle(APP_RUNNING_CHANNEL_NAME)
            .setContentText(APP_RUNNING_CHANNEL_NAME)
            .build();
    startForeground(SERVICE_STARTED_NOTIFICATION_ID, notification);
  }

  private void handleDismissNotificationIntent(Intent intent) {
    ConversationKey key = getConversationKey(intent);
    if (key == null) {
      logw(TAG, "Dropping dismiss intent. Received null conversation key.");
      return;
    }
    notificationMsgFeature.sendData(key.getDeviceId(),
        notificationMsgDelegate.dismiss(key).toByteArray());
  }

  private void handleMarkAsReadIntent(Intent intent) {
    ConversationKey key = getConversationKey(intent);
    if (key == null) {
      logw(TAG, "Dropping mark as read intent. Received null conversation key.");
      return;
    }
    notificationMsgFeature.sendData(key.getDeviceId(),
        notificationMsgDelegate.markAsRead(key).toByteArray());
  }

  private void handleReplyIntent(Intent intent) {
    ConversationKey key = getConversationKey(intent);
    Bundle bundle = RemoteInput.getResultsFromIntent(intent);
    if (bundle == null || key == null) {
      logw(TAG, "Dropping voice reply intent. Received null arguments.");
      return;
    }
    CharSequence message = bundle.getCharSequence(EXTRA_REMOTE_INPUT_KEY);
    notificationMsgFeature.sendData(key.getDeviceId(),
        notificationMsgDelegate.reply(key, message.toString()).toByteArray());
  }

  @Nullable
  private static ConversationKey getConversationKey(Intent intent) {
    return intent.getParcelableExtra(EXTRA_CONVERSATION_KEY);
  }

  private NotificationMsgDelegate createNotificationMsgDelegate() {
    return new NotificationMsgDelegate(
        this,
        getResources().getDimensionPixelSize(requireMetaResourceId(META_BITMAP_SIZE)),
        getMetaFloat(META_CORNER_RADIUS, DEFAULT_CORNER_RADIUS),
        getMetaInt(META_AVATAR_LETTERS, DEFAULT_AVATAR_LETTERS),
        requireMetaResourceId(META_DEFAULT_ICON),
        getMetaInt(META_TILE_DEFAULT_COLOR, DEFAULT_TILE_COLOR),
        getLetterTileColors(),
        getMetaInt(META_FONT_COLOR, DEFAULT_FONT_COLOR),
        Typeface.create(
            getMetaString(META_FONT_FAMILY, DEFAULT_FONT_FAMILY),
            getMetaInt(META_TEXT_STYLE, DEFAULT_TEXT_STYLE)),
        getResources().getDrawable(
            requireMetaResourceId(META_DEFAULT_AVATAR), /* theme= */ null),
        requireMetaString(META_DEFAULT_DISPLAY_NAME),
        getMetaString(META_GROUP_TITLE_SEPARATOR, DEFAULT_GROUP_TITLE_SEPARATOR),
        getMetaFloat(META_LETTER_TO_TILE_RATIO, DEFAULT_LETTER_TO_TILE_RATIO),
        requireMetaResourceId(META_CONTENT_TEXT));
  }

  private int[] getLetterTileColors() {
    int resId = getMetaResourceId(META_TILE_COLORS, -1);
    if (resId == -1) {
      return DEFAULT_TILE_COLORS;
    }
    TypedArray ta = getResources().obtainTypedArray(resId);
    int defaultColor = getMetaInt(META_TILE_DEFAULT_COLOR, DEFAULT_TILE_COLOR);
    if (ta.length() == 0) {
      // Looks like robolectric shadow doesn't currently support
      // obtainTypedArray and always returns length 0 array, which will make some code
      // below that does a division by length of sColors choke. Workaround by creating
      // an array of length 1.
      return new int[] {defaultColor};
    }

    int[] colors = new int[ta.length()];
    for (int i = ta.length() - 1; i >= 0; i--) {
      colors[i] = ta.getColor(i, defaultColor);
    }
    ta.recycle();
    return colors;
  }
}
