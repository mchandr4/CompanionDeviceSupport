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

import static java.lang.Math.min;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

/** Telecom utility methods. */
public class TelecomUtils {
  private TelecomUtils() {}

  /**
   * Create a {@link LetterTileDrawable} for the given initials.
   *
   * @param initials is the letters that will be drawn on the canvas. If it is null, then an avatar
   *     anonymous icon will be drawn
   * @param identifier will decide the color for the drawable. If null, a default color will be
   *     used.
   * @param avatarNumberOfLetters is how many letters are shown in the avatar.
   */
  public static LetterTileDrawable createLetterTile(
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

  /**
   * Creates a Letter Tile Icon that will display the given initials. If the initials are null, then
   * an avatar anonymous icon will be drawn.
   */
  public static Icon createLetterTile(
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
    LetterTileDrawable letterTileDrawable =
        TelecomUtils.createLetterTile(
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
  public static Icon createFromRoundedBitmapDrawable(
      RoundedBitmapDrawable roundedBitmapDrawable, int avatarSize, float cornerRadiusPercent) {
    float radius = avatarSize * cornerRadiusPercent;
    roundedBitmapDrawable.setCornerRadius(radius);

    final Bitmap result = Bitmap.createBitmap(avatarSize, avatarSize, Bitmap.Config.ARGB_8888);
    final Canvas canvas = new Canvas(result);
    roundedBitmapDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
    roundedBitmapDrawable.draw(canvas);
    return Icon.createWithBitmap(result);
  }
}
