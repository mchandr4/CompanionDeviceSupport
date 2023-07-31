/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.google.android.connecteddevice.ui

import android.graphics.Bitmap
import android.graphics.Color
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Factory class of QR code. */
object QrCodeGenerator {
  private const val TAG = "QrCodeGenerator"
  /**
   * Returns a [Bitmap] of a QR code with the given [content].
   *
   * @param content the data which will be rendered by the QR code.
   * @param sizeInPixels size of the QR code image.
   * @param foregroundColor the color of the data of the QR code, please set this according to the
   *   app theme.
   * @param backgroundColor the color of the background of the QR code.
   * @param errorCorrection determines the percentage of the total QR code that is allowed to be
   *   dirty or damaged without being unable to read.
   */
  @JvmOverloads
  @JvmStatic
  fun createQrCode(
    content: String,
    sizeInPixels: Int,
    foregroundColor: Int = Color.WHITE,
    backgroundColor: Int = Color.TRANSPARENT,
    errorCorrection: ErrorCorrectionLevel = ErrorCorrectionLevel.L
  ): Bitmap? {
    val hints =
      mapOf<EncodeHintType, ErrorCorrectionLevel>(
        EncodeHintType.ERROR_CORRECTION to errorCorrection
      )
    val bitMatrix =
      try {
        QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizeInPixels, sizeInPixels, hints)
      } catch (e: Exception) {
        when (e) {
          is IllegalArgumentException, is WriterException -> {
            loge(TAG, "Cannot generate the QR code.", e)
            return null
          }
          else -> throw e
        }
      }
    val bitmap = Bitmap.createBitmap(sizeInPixels, sizeInPixels, Bitmap.Config.ARGB_8888)
    for (i in 0 until sizeInPixels) {
      for (j in 0 until sizeInPixels) {
        bitmap.setPixel(i, j, if (bitMatrix.get(i, j)) foregroundColor else backgroundColor)
      }
    }
    return bitmap
  }
}
