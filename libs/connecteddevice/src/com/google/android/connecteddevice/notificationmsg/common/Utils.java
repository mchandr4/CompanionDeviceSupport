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

import static com.google.android.connecteddevice.util.SafeLog.logw;
import static java.lang.Math.min;
import static java.util.stream.Collectors.joining;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg.ConversationNotification;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg.MessagingStyle;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg.MessagingStyleMessage;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg.Person;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Utils methods. */
@SuppressWarnings("AndroidJdkLibsChecker")
public class Utils {
  private static final String TAG = "CMC.Utils";
  /**
   * Represents the maximum length of a message substring to be used when constructing the message's
   * unique handle/key.
   */
  private static final int MAX_SUB_MESSAGE_LENGTH = 5;

  /** The Regex format of a telephone number in a BluetoothMapClient contact URI. */
  private static final String MAP_CLIENT_URI_REGEX = "tel:(.+)";

  /** The starting substring index for a string formatted with the MAP_CLIENT_URI_REGEX above. */
  private static final int MAP_CLIENT_URI_PHONE_NUMBER_SUBSTRING_INDEX = 4;

  // TODO : Reference BluetoothMapClient Extras once BluetoothMapClient is SystemApi.
  protected static final String BMC_EXTRA_MESSAGE_HANDLE =
      "android.bluetooth.mapmce.profile.extra.MESSAGE_HANDLE";
  protected static final String BMC_EXTRA_SENDER_CONTACT_URI =
      "android.bluetooth.mapmce.profile.extra.SENDER_CONTACT_URI";
  protected static final String BMC_EXTRA_SENDER_CONTACT_NAME =
      "android.bluetooth.mapmce.profile.extra.SENDER_CONTACT_NAME";
  protected static final String BMC_EXTRA_MESSAGE_TIMESTAMP =
      "android.bluetooth.mapmce.profile.extra.MESSAGE_TIMESTAMP";
  protected static final String BMC_EXTRA_MESSAGE_READ_STATUS =
      "android.bluetooth.mapmce.profile.extra.MESSAGE_READ_STATUS";

  private Utils() {}

  /** Gets the latest message for a {@link NotificationMsg} Conversation. */
  public static MessagingStyleMessage getLatestMessage(ConversationNotification notification) {
    MessagingStyle messagingStyle = notification.getMessagingStyle();
    long latestTime = 0;
    MessagingStyleMessage latestMessage = null;

    for (MessagingStyleMessage message : messagingStyle.getMessagingStyleMsgList()) {
      if (message.getTimestamp() > latestTime) {
        latestTime = message.getTimestamp();
        latestMessage = message;
      }
    }
    return latestMessage;
  }

  /**
   * Helper method to create a unique handle/key for this message. This is used as this Message's
   * {@link MessageKey#getSubKey()}.
   */
  public static String createMessageHandle(MessagingStyleMessage message) {
    String textMessage = message.getTextMessage();
    String subMessage =
        textMessage.substring(min(MAX_SUB_MESSAGE_LENGTH, textMessage.length()));
    return message.getTimestamp() + "/" + message.getSender().getName() + "/" + subMessage;
  }

  /**
   * Ensure the {@link ConversationNotification} object has all the required fields.
   *
   * @param isShallowCheck should be {@code true} if the caller only wants to verify the
   *     notification and its {@link MessagingStyle} is valid, without checking all of the
   *     notification's {@link MessagingStyleMessage}s.
   */
  public static boolean isValidConversationNotification(
      ConversationNotification notification, boolean isShallowCheck) {
    if (!notification.hasMessagingStyle()) {
      logw(TAG, "ConversationNotification is missing required field: messagingStyle");
      return false;
    } else if (notification.getMessagingAppDisplayName().isEmpty()) {
      logw(TAG, "ConversationNotification is missing required field: appDisplayName");
      return false;
    } else if (notification.getMessagingAppPackageName().isEmpty()) {
      logw(TAG, "ConversationNotification is missing required field: appPackageName");
      return false;
    }
    return isValidMessagingStyle(notification.getMessagingStyle(), isShallowCheck);
  }

  /** Ensure the {@link MessagingStyle} object has all the required fields. */
  private static boolean isValidMessagingStyle(
      MessagingStyle messagingStyle, boolean isShallowCheck) {
    if (messagingStyle.getConvoTitle().isEmpty()) {
      logw(TAG, "MessagingStyle is missing required field: convoTitle");
      return false;
    } else if (messagingStyle.getUserDisplayName().isEmpty()) {
      logw(TAG, "MessagingStyle is missing required field: userDisplayName");
      return false;
    } else if (messagingStyle.getMessagingStyleMsgCount() == 0) {
      logw(TAG, "MessagingStyle is missing required field: messagingStyleMsg");
      return false;
    }
    if (!isShallowCheck) {
      for (MessagingStyleMessage message : messagingStyle.getMessagingStyleMsgList()) {
        if (!isValidMessagingStyleMessage(message)) {
          return false;
        }
      }
    }
    return true;
  }

  /** Ensure the {@link MessagingStyleMessage} object has all the required fields. */
  public static boolean isValidMessagingStyleMessage(MessagingStyleMessage message) {
    if (message.getTextMessage().isEmpty()) {
      logw(TAG, "MessagingStyleMessage is missing required field: textMessage");
      return false;
    } else if (!message.hasSender()) {
      logw(TAG, "MessagingStyleMessage is missing required field: sender");
      return false;
    }
    return isValidSender(message.getSender());
  }

  /** Ensure the {@link Person} object has all the required fields. */
  public static boolean isValidSender(Person person) {
    if (person.getName().isEmpty()) {
      logw(TAG, "Person is missing required field: name");
      return false;
    }
    return true;
  }

  /** Ensure the BluetoothMapClient intent has all the required fields. */
  public static boolean isValidMapClientIntent(Intent intent) {
    if (intent == null) {
      logw(TAG, "BluetoothMapClient intent is null");
      return false;
    } else if (intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) == null) {
      logw(TAG, "BluetoothMapClient intent is missing required field: device");
      return false;
    } else if (intent.getStringExtra(BMC_EXTRA_MESSAGE_HANDLE) == null) {
      logw(TAG, "BluetoothMapClient intent is missing required field: senderName");
      return false;
    } else if (intent.getStringExtra(BMC_EXTRA_SENDER_CONTACT_NAME) == null) {
      logw(TAG, "BluetoothMapClient intent is missing required field: handle");
      return false;
    } else if (intent.getStringExtra(android.content.Intent.EXTRA_TEXT) == null) {
      logw(TAG, "BluetoothMapClient intent is missing required field: messageText");
      return false;
    }
    return true;
  }

  /**
   * Creates a Letter Tile Icon that will display the given initials. If the initials are null, then
   * an avatar anonymous icon will be drawn.
   */
  public static Bitmap createLetterTile(
      Context context,
      @Nullable String initials,
      String identifier,
      int avatarSize,
      float cornerRadiusPercent,
      int defaultColor,
      int[] colors,
      int fontColor,
      Typeface typeface,
      Drawable defaultAvatar,
      float letterToTileRatio,
      int avatarNumberOfLetters) {
    // TODO: use TelecomUtils once car-telephony-common supports bp.
    LetterTileDrawable letterTileDrawable =
        createLetterTileDrawable(
            initials,
            identifier,
            defaultColor,
            colors,
            fontColor,
            typeface,
            defaultAvatar,
            letterToTileRatio,
            avatarNumberOfLetters);
    RoundedBitmapDrawable roundedBitmapDrawable =
        RoundedBitmapDrawableFactory.create(
            context.getResources(), letterTileDrawable.toBitmap(avatarSize));
    return createFromRoundedBitmapDrawable(roundedBitmapDrawable, avatarSize, cornerRadiusPercent);
  }

  /** Creates an Icon based on the given roundedBitmapDrawable. */
  private static Bitmap createFromRoundedBitmapDrawable(
      RoundedBitmapDrawable roundedBitmapDrawable, int avatarSize, float cornerRadiusPercent) {
    // TODO: use TelecomUtils once car-telephony-common supports bp.
    float radius = avatarSize * cornerRadiusPercent;
    roundedBitmapDrawable.setCornerRadius(radius);

    final Bitmap result = Bitmap.createBitmap(avatarSize, avatarSize, Bitmap.Config.ARGB_8888);
    final Canvas canvas = new Canvas(result);
    roundedBitmapDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
    roundedBitmapDrawable.draw(canvas);
    return roundedBitmapDrawable.getBitmap();
  }

  /**
   * Create a {@link LetterTileDrawable} for the given initials.
   *
   * @param initials is the letters that will be drawn on the canvas. If it is null, then an avatar
   *     anonymous icon will be drawn
   * @param identifier will decide the color for the drawable. If null, a default color will be
   *     used.
   */
  private static LetterTileDrawable createLetterTileDrawable(
      @Nullable String initials,
      @Nullable String identifier,
      int defaultColor,
      int[] colors,
      int fontColor,
      Typeface typeface,
      Drawable defaultAvatar,
      float letterToTileRatio,
      int avatarNumberOfLetters) {
    String letters =
        initials != null
            ? initials.substring(0, min(initials.length(), avatarNumberOfLetters))
            : null;
    return new LetterTileDrawable(
        letters,
        identifier,
        defaultColor,
        colors,
        fontColor,
        typeface,
        defaultAvatar,
        letterToTileRatio);
  }

  /** Returns whether the BluetoothMapClient intent represents a group conversation. */
  public static boolean isGroupConversation(Intent intent) {
    return (intent.getStringArrayExtra(Intent.EXTRA_CC) != null
        && intent.getStringArrayExtra(Intent.EXTRA_CC).length > 1);
  }

  /**
   * Returns the initials based on the name and nameAlt.
   *
   * @param name should be the display name of a contact.
   * @param nameAlt should be alternative display name of a contact.
   */
  public static String getInitials(String name, String nameAlt) {
    // TODO: use TelecomUtils once car-telephony-common supports bp.
    StringBuilder initials = new StringBuilder();
    if (!TextUtils.isEmpty(name) && Character.isLetter(name.charAt(0))) {
      initials.append(Character.toUpperCase(name.charAt(0)));
    }
    if (!TextUtils.isEmpty(nameAlt)
        && !TextUtils.equals(name, nameAlt)
        && Character.isLetter(nameAlt.charAt(0))) {
      initials.append(Character.toUpperCase(nameAlt.charAt(0)));
    }
    return initials.toString();
  }

  /** Returns the list of sender uri for a BluetoothMapClient intent. */
  public static String getSenderUri(Intent intent) {
    return intent.getStringExtra(BMC_EXTRA_SENDER_CONTACT_URI);
  }

  /** Returns the sender name for a BluetoothMapClient intent. */
  public static String getSenderName(Intent intent) {
    return intent.getStringExtra(BMC_EXTRA_SENDER_CONTACT_NAME);
  }

  /** Returns the list of recipient uris for a BluetoothMapClient intent. */
  public static List<String> getInclusiveRecipientsUrisList(Intent intent) {
    List<String> ccUris = new ArrayList<>();
    String uri = getSenderUri(intent);
    if (isGroupConversation(intent)) {
      String[] extras = intent.getStringArrayExtra(Intent.EXTRA_CC);
      if (extras == null) {
        extras = new String[0];
      }
      Collections.addAll(ccUris, extras);
    }
    if (!ccUris.contains(uri)) {
      ccUris.add(uri);
    }
    // TODO : remove sorting.
    Collections.sort(ccUris);

    return ccUris;
  }

  /** Extracts the phone number from the BluetoothMapClient contact Uri. */
  @Nullable
  public static String getPhoneNumberFromMapClient(@Nullable String senderContactUri) {
    if (senderContactUri == null || !senderContactUri.matches(MAP_CLIENT_URI_REGEX)) {
      logw(TAG, " contactUri is malformed! " + senderContactUri);
      return null;
    }

    return senderContactUri.substring(MAP_CLIENT_URI_PHONE_NUMBER_SUBSTRING_INDEX);
  }

  /**
   * Creates a Header for a group conversation, where the senderName and groupName are both shown,
   * separated by a delimiter.
   *
   * @param senderName Sender's name.
   * @param groupName Group conversation's name.
   * @param delimiter delimiter that separates each element.
   */
  public static String constructGroupConversationHeader(
      String senderName, String groupName, String delimiter) {
    return constructGroupConversationHeader(
        senderName, groupName, delimiter, BidiFormatter.getInstance());
  }

  /**
   * Creates a Header for a group conversation, where the senderName and groupName are both shown,
   * separated by a delimiter.
   *
   * @param senderName Sender's name.
   * @param groupName Group conversation's name.
   * @param delimiter delimiter that separates each element.
   * @param bidiFormatter formatter for the context's locale.
   */
  public static String constructGroupConversationHeader(
      String senderName, String groupName, String delimiter, BidiFormatter bidiFormatter) {
    String formattedSenderName =
        bidiFormatter.unicodeWrap(
            senderName, TextDirectionHeuristics.FIRSTSTRONG_LTR, /* isolate= */ true);
    String formattedGroupName =
        bidiFormatter.unicodeWrap(
            groupName, TextDirectionHeuristics.FIRSTSTRONG_LTR, /* isolate= */ true);
    String title = String.join(delimiter, formattedSenderName, formattedGroupName);
    return bidiFormatter.unicodeWrap(title, TextDirectionHeuristics.LOCALE);
  }

  /**
   * Given a name of all the participants in a group conversation (some names might be phone
   * numbers), this function creates the conversation title by putting the names in alphabetical
   * order first, then adding any phone numbers. This title should not exceed the
   * conversationTitleLength, so not all participants' names are guaranteed to be in the
   * conversation title.
   */
  public static String constructGroupConversationTitle(
      List<String> names, String delimiter, int conversationTitleLength) {
    return constructGroupConversationTitle(
        names, delimiter, conversationTitleLength, BidiFormatter.getInstance());
  }

  /**
   * Given a name of all the participants in a group conversation (some names might be phone
   * numbers), this function creates the conversation title by putting the names in alphabetical
   * order first, then adding any phone numbers. This title should not exceed the
   * conversationTitleLength, so not all participants' names are guaranteed to be in the
   * conversation title.
   */
  public static String constructGroupConversationTitle(
      List<String> names,
      String delimiter,
      int conversationTitleLength,
      BidiFormatter bidiFormatter) {
    List<String> sortedNames =
        getSortedSubsetNames(names, conversationTitleLength, delimiter.length());
    String formattedDelimiter =
        bidiFormatter.unicodeWrap(delimiter, TextDirectionHeuristics.LOCALE);

    String conversationName =
        sortedNames.stream()
            .map(name -> bidiFormatter.unicodeWrap(name, TextDirectionHeuristics.FIRSTSTRONG_LTR))
            .collect(joining(formattedDelimiter));
    return bidiFormatter.unicodeWrap(conversationName, TextDirectionHeuristics.LOCALE);
  }

  /**
   * Sorts the list, and returns the first elements whose total length is less than the given
   * conversationTitleLength.
   */
  private static List<String> getSortedSubsetNames(
      List<String> names, int conversationTitleLength, int delimiterLength) {
    Collections.sort(names, Utils.ALPHA_THEN_NUMERIC_COMPARATOR);
    int namesCounter = 0;
    int indexCounter = 0;
    while (namesCounter < conversationTitleLength && indexCounter < names.size()) {
      namesCounter = namesCounter + names.get(indexCounter).length() + delimiterLength;
      indexCounter = indexCounter + 1;
    }
    return names.subList(0, indexCounter);
  }

  /** Comparator that sorts names alphabetically first, then phone numbers numerically. */
  public static final Comparator<String> ALPHA_THEN_NUMERIC_COMPARATOR =
      new Comparator<String>() {
        private boolean isPhoneNumber(String input) {
          PhoneNumberUtil util = PhoneNumberUtil.getInstance();
          try {
            Phonenumber.PhoneNumber phoneNumber = util.parse(input, /* defaultRegion */ null);
            return util.isValidNumber(phoneNumber);
          } catch (NumberParseException e) {
            return false;
          }
        }

        private boolean isOfSameType(String o1, String o2) {
          boolean isO1PhoneNumber = isPhoneNumber(o1);
          boolean isO2PhoneNumber = isPhoneNumber(o2);
          return isO1PhoneNumber == isO2PhoneNumber;
        }

        @Override
        public int compare(String o1, String o2) {
          // if both are names, sort based on names.
          // if both are number, sort numerically.
          // if one is phone number and the other is a name, give name precedence.
          if (!isOfSameType(o1, o2)) {
            return isPhoneNumber(o1) ? 1 : -1;
          } else {
            return o1.compareTo(o2);
          }
        }
      };
}
