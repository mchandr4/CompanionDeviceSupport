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

import android.net.Uri
import android.util.Base64
import androidx.annotation.VisibleForTesting
import com.google.android.companionprotos.OutOfBandAssociationData
import com.google.android.companionprotos.OutOfBandAssociationToken
import com.google.android.connecteddevice.model.OobData
import com.google.protobuf.ByteString

/** Build Uri which will be used to communicate with remote device. */
class CompanionUriBuilder(private val uri: Uri? = null) {
  private var oobData: OobData? = null
  private var deviceId: ByteArray? = null
  private val builder: Uri.Builder = uri?.buildUpon() ?: Uri.Builder()

  fun scheme(scheme: String) = apply { builder.scheme(scheme) }
  fun authority(authority: String) = apply { builder.authority(authority) }
  fun appendPath(path: String) = apply { builder.appendPath(path) }

  /**
   * Add query entry to the [Uri].
   *
   * The key can not be reserved keys, e.g. "oobData".
   */
  fun appendQueryParameter(key: String, value: String) = apply {
    if (key == OOB_DATA_PARAMETER_KEY) {
      throw IllegalArgumentException("Invalid key.")
    }
    builder.appendQueryParameter(key, value)
  }

  fun oobData(oobData: OobData) = apply { this.oobData = oobData }
  fun deviceId(identifier: ByteArray) = apply { this.deviceId = identifier }

  fun build(): Uri {
    val oobData = this.oobData
    val oobAssociationData =
      OutOfBandAssociationData.newBuilder().run {
        if (deviceId != null) {
          setDeviceIdentifier(ByteString.copyFrom(deviceId))
        }
        if (oobData != null) {
          val token =
            OutOfBandAssociationToken.newBuilder().run {
              setEncryptionKey(ByteString.copyFrom(oobData.encryptionKey))
              setIhuIv(ByteString.copyFrom(oobData.ihuIv))
              setMobileIv(ByteString.copyFrom(oobData.mobileIv))
              build()
            }
          setToken(token)
        }
        build()
      }
    builder.appendQueryParameter(
      OOB_DATA_PARAMETER_KEY,
      Base64.encodeToString(oobAssociationData.toByteArray(), Base64.URL_SAFE)
    )
    return builder.build()
  }
  companion object {
    /**
     * The key name of the companion device out-of-band data.
     *
     * The value should be a Base64-encoded url-safe string of the OOB data.
     */
    @VisibleForTesting internal const val OOB_DATA_PARAMETER_KEY = "oobData"
  }
}
