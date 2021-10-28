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

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.companionprotos.OutOfBandAssociationData
import com.google.android.companionprotos.outOfBandAssociationToken
import com.google.android.connecteddevice.model.OobData
import com.google.android.connecteddevice.ui.CompanionUriBuilder.Companion.OOB_DATA_PARAMETER_KEY
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.ByteString
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompanionUriBuilderTest {
  @Test
  fun build_returnUriWithCorrectInfo() {
    val testScheme = "scheme"
    val testAuthority = "authority"
    val testPath = "path"
    val testOobData = OobData(ByteArray(10), ByteArray(10), ByteArray(10))
    val testDeviceIdentifier = ByteArray(10)
    val testQueryKey = "key"
    val testQueryValue = "Value"
    val uri =
      CompanionUriBuilder()
        .apply {
          scheme(testScheme)
          authority(testAuthority)
          appendPath(testPath)
          oobData(testOobData)
          deviceId(testDeviceIdentifier)
          appendQueryParameter(testQueryKey, testQueryValue)
        }
        .build()

    val oobToken = outOfBandAssociationToken {
      encryptionKey = ByteString.copyFrom(testOobData.encryptionKey)
      ihuIv = ByteString.copyFrom(testOobData.ihuIv)
      mobileIv = ByteString.copyFrom(testOobData.mobileIv)
    }

    val mobileOobData =
      OutOfBandAssociationData.parseFrom(
        Base64.decode(uri.getQueryParameter(OOB_DATA_PARAMETER_KEY), Base64.DEFAULT)
      )
    assertThat(mobileOobData.token).isEqualTo(oobToken)
    assertThat(mobileOobData.deviceIdentifier).isEqualTo(ByteString.copyFrom(testDeviceIdentifier))
    assertThat(uri.scheme).isEqualTo(testScheme)
    assertThat(uri.authority).isEqualTo(testAuthority)
    assertThat(uri.pathSegments.first()).isEqualTo(testPath)
    assertThat(uri.getQueryParameter(testQueryKey)).isEqualTo(testQueryValue)
  }

  @Test
  fun build_oobDataNotSet_DoNotThrow() {
    val testDeviceIdentifier = ByteArray(10)
    val uri =
      CompanionUriBuilder()
        .apply {
          scheme("scheme")
          authority("authority")
          appendPath("path")
          deviceId(testDeviceIdentifier)
        }
        .build()

    val mobileOobData =
      OutOfBandAssociationData.parseFrom(
        Base64.decode(uri.getQueryParameter(OOB_DATA_PARAMETER_KEY), Base64.DEFAULT)
      )
    assertThat(mobileOobData.hasToken()).isFalse()
    // assertThat(mobileOobData.token.ihuIv).isEmpty()
    // assertThat(mobileOobData.token.mobileIv).isEmpty()
  }

  @Test
  fun build_deviceIdNotSet_DoNotThrow() {
    val uri =
      CompanionUriBuilder()
        .apply {
          scheme("scheme")
          authority("authority")
          appendPath("path")
        }
        .build()

    val mobileOobData =
      OutOfBandAssociationData.parseFrom(
        Base64.decode(uri.getQueryParameter(OOB_DATA_PARAMETER_KEY), Base64.DEFAULT)
      )
    assertThat(mobileOobData.deviceIdentifier).isEmpty()
  }

  @Test
  fun build_useReservedKey_Throw() {
    val builder = CompanionUriBuilder()
    assertThrows(IllegalArgumentException::class.java) {
      builder.appendQueryParameter(OOB_DATA_PARAMETER_KEY, "value")
    }
  }
}
