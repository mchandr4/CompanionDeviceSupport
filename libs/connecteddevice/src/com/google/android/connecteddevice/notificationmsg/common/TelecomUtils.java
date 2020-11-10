package com.google.android.connecteddevice.notificationmsg.common;

import static java.lang.Math.min;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.annotation.Nullable;

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
