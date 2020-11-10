package com.google.android.connecteddevice.notificationmsg.common;

import static java.lang.Math.abs;
import static java.lang.Math.min;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import androidx.annotation.Nullable;

/**
 * A drawable that encapsulates all the functionality needed to display a letter tile to represent a
 * contact image.
 */
@SuppressWarnings("StaticAssignmentInConstructor")
public class LetterTileDrawable extends Drawable {
  /** Letter tile */
  private static int[] colors;

  private static int defaultColor;
  private static int tileFontColor;
  private static float letterToTileRatio;
  private static Drawable defaultPersonAvatar;
  private static Drawable defaultBusinessAvatar;
  private static Drawable defaultVoicemailAvatar;

  /** Reusable components to avoid new allocations */
  private static final Paint SINGLE_PAINT = new Paint();

  private static final Rect RECT = new Rect();

  /** Contact type constants */
  public static final int TYPE_PERSON = 1;

  public static final int TYPE_BUSINESS = 2;
  public static final int TYPE_VOICEMAIL = 3;
  public static final int TYPE_DEFAULT = TYPE_PERSON;

  private final Paint paint;

  private String letters;
  private int color;
  private int contactType = TYPE_DEFAULT;
  private float scale = 1.0f;
  private float offset = 0.0f;
  private boolean isCircle = false;

  /** A custom Drawable that draws letters on a colored background. */
  // This constructor allows passing the letters and identifier directly. There is no need to
  // call setContactDetails() again. setIsCircular(true) needs to be called separately if needed.
  public LetterTileDrawable(
      @Nullable String letters,
      @Nullable String identifier,
      int defaultColor,
      int[] colors,
      int fontColor,
      Typeface typeface,
      Drawable defaultAvatar,
      float letterToTileRatio) {
    paint = new Paint();
    paint.setFilterBitmap(true);
    paint.setDither(true);
    setScale(0.7f);

    if (LetterTileDrawable.colors == null) {
      LetterTileDrawable.defaultColor = defaultColor;
      LetterTileDrawable.colors = colors;

      tileFontColor = fontColor;
      LetterTileDrawable.letterToTileRatio = letterToTileRatio;
      defaultPersonAvatar = defaultAvatar;
      defaultBusinessAvatar = defaultAvatar;
      defaultVoicemailAvatar = defaultAvatar;
      SINGLE_PAINT.setTypeface(typeface);
      SINGLE_PAINT.setTextAlign(Align.CENTER);
      SINGLE_PAINT.setAntiAlias(true);
    }

    setContactDetails(letters, identifier);
  }

  @Override
  public void draw(final Canvas canvas) {
    final Rect bounds = getBounds();
    if (!isVisible() || bounds.isEmpty()) {
      return;
    }
    // Draw letter tile.
    drawLetterTile(canvas);
  }

  /**
   * Draw the drawable onto the canvas at the current bounds taking into account the current scale.
   */
  private void drawDrawableOnCanvas(final Drawable drawable, final Canvas canvas) {
    // The drawable should be drawn in the middle of the canvas without changing its width to
    // height ratio.
    final Rect destRect = copyBounds();

    // Crop the destination bounds into a square, scaled and offset as appropriate
    final int halfLength = (int) (scale * min(destRect.width(), destRect.height()) / 2);

    destRect.set(
        destRect.centerX() - halfLength,
        (int) (destRect.centerY() - halfLength + offset * destRect.height()),
        destRect.centerX() + halfLength,
        (int) (destRect.centerY() + halfLength + offset * destRect.height()));

    drawable.setAlpha(paint.getAlpha());
    drawable.setColorFilter(tileFontColor, PorterDuff.Mode.SRC_IN);
    drawable.setBounds(destRect);
    drawable.draw(canvas);
  }

  private void drawLetterTile(final Canvas canvas) {
    // Draw background color.
    SINGLE_PAINT.setColor(color);

    SINGLE_PAINT.setAlpha(paint.getAlpha());
    final Rect bounds = getBounds();
    final int minDimension = min(bounds.width(), bounds.height());

    if (isCircle) {
      canvas.drawCircle(bounds.centerX(), bounds.centerY(), minDimension / 2, SINGLE_PAINT);
    } else {
      canvas.drawRect(bounds, SINGLE_PAINT);
    }

    if (!TextUtils.isEmpty(letters)) {
      // Scale text by canvas bounds and user selected scaling factor
      SINGLE_PAINT.setTextSize(scale * letterToTileRatio * minDimension);
      // sPaint.setTextSize(sTileLetterFontSize);
      SINGLE_PAINT.getTextBounds(letters, 0, letters.length(), RECT);
      SINGLE_PAINT.setColor(tileFontColor);

      // Draw the letter in the canvas, vertically shifted up or down by the user-defined
      // offset
      canvas.drawText(
          letters,
          0,
          letters.length(),
          bounds.centerX(),
          bounds.centerY() + offset * bounds.height() + RECT.height() / 2,
          SINGLE_PAINT);
    } else {
      // Draw the default image if there is no letter/digit to be drawn
      final Drawable drawable = getDrawablepForContactType(contactType);
      drawDrawableOnCanvas(drawable, canvas);
    }
  }

  public int getColor() {
    return color;
  }

  /** Returns a deterministic color based on the provided contact identifier string. */
  private int pickColor(final String identifier) {
    if (TextUtils.isEmpty(identifier) || contactType == TYPE_VOICEMAIL) {
      return defaultColor;
    }
    // String.hashCode() implementation is not supposed to change across java versions, so
    // this should guarantee the same email address always maps to the same color.
    // The email should already have been normalized by the ContactRequest.
    final int color = abs(identifier.hashCode()) % colors.length;
    return colors[color];
  }

  private static Drawable getDrawablepForContactType(int contactType) {
    switch (contactType) {
      case TYPE_BUSINESS:
        return defaultBusinessAvatar;
      case TYPE_VOICEMAIL:
        return defaultVoicemailAvatar;
      case TYPE_PERSON:
      default:
        return defaultPersonAvatar;
    }
  }

  @Override
  public void setAlpha(final int alpha) {
    paint.setAlpha(alpha);
  }

  @Override
  public void setColorFilter(final ColorFilter cf) {
    paint.setColorFilter(cf);
  }

  @Override
  public int getOpacity() {
    return PixelFormat.OPAQUE;
  }

  /**
   * Scale the drawn letter tile to a ratio of its default size
   *
   * @param scale The ratio the letter tile should be scaled to as a percentage of its default size,
   *     from a scale of 0 to 2.0f. The default is 1.0f.
   */
  public void setScale(float scale) {
    this.scale = scale;
  }

  /**
   * Assigns the vertical offset of the position of the letter tile to the ContactDrawable
   *
   * @param offset The provided offset must be within the range of -0.5f to 0.5f. If set to -0.5f,
   *     the letter will be shifted upwards by 0.5 times the height of the canvas it is being drawn
   *     on, which means it will be drawn with the center of the letter starting at the top edge of
   *     the canvas. If set to 0.5f, the letter will be shifted downwards by 0.5 times the height of
   *     the canvas it is being drawn on, which means it will be drawn with the center of the letter
   *     starting at the bottom edge of the canvas. The default is 0.0f.
   */
  public void setOffset(float offset) {
    this.offset = offset;
  }

  /**
   * Sets the details.
   *
   * @param letters The letters need to be drawn
   * @param identifier decides the color for the drawable.
   */
  public void setContactDetails(@Nullable String letters, @Nullable String identifier) {
    this.letters = letters;
    color = pickColor(identifier);
  }

  public void setContactType(int contactType) {
    this.contactType = contactType;
  }

  public void setIsCircular(boolean isCircle) {
    this.isCircle = isCircle;
  }

  /**
   * Convert the drawable to a bitmap.
   *
   * @param size The target size of the bitmap.
   * @return A bitmap representation of the drawable.
   */
  public Bitmap toBitmap(int size) {
    Bitmap largeIcon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(largeIcon);
    Rect bounds = getBounds();
    setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
    draw(canvas);
    setBounds(bounds);
    return largeIcon;
  }
}
