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

package com.google.android.connecteddevice.oob

import android.security.keystore.KeyProperties
import androidx.annotation.VisibleForTesting
import com.google.android.companionprotos.OutOfBandAssociationToken
import com.google.android.connecteddevice.model.OobData
import com.google.android.connecteddevice.transport.ProtocolDelegate
import com.google.android.connecteddevice.transport.ProtocolDevice
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.protobuf.ByteString
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

/**
 * Manages all OOB related actions,
 *
 * The actions taken by this runner should be ordered as below:
 * 1. [sendOobData] to the remote [ProtocolDevice], OOB data is generated at the same time.
 * 2. [encryptData] and [decryptData] data after the OOB data exchange succeed. Will throw
 * [IllegalStateException] if attempting to encrypt/decrypt without first having called
 * [sendOobData].
 */
open class OobRunner
@JvmOverloads
constructor(
  private val delegate: ProtocolDelegate,
  private val oobProtocolName: String,
  internal val keyAlgorithm: String = KeyProperties.KEY_ALGORITHM_AES
) {
  @VisibleForTesting internal var ihuIv = ByteArray(NONCE_LENGTH_BYTES)
  @VisibleForTesting internal var mobileIv = ByteArray(NONCE_LENGTH_BYTES)
  @VisibleForTesting internal var encryptionKey: SecretKey? = null
  @VisibleForTesting internal val establishedOobChannels = mutableListOf<OobChannel>()

  private val cipher =
    try {
      Cipher.getInstance(ALGORITHM)
    } catch (e: Exception) {
      loge(TAG, "Unable to create cipher with $ALGORITHM.", e)
      throw IllegalStateException(e)
    }

  /**
   * Returns generates OOB data and iterates through all available OOB channels, establish OOB
   * connections and send OOB data to remote device.
   */
  open fun sendOobData(): OobData {
    logd(TAG, "Listening for OOB connection to send OOB data.")
    val keyGenerator =
      try {
        KeyGenerator.getInstance(keyAlgorithm)
      } catch (e: NoSuchAlgorithmException) {
        loge(TAG, "Unable to get AES key generator.", e)
        throw IllegalStateException(e)
      }
    val secretKey = keyGenerator.generateKey()
    encryptionKey = secretKey
    val secureRandom = SecureRandom()
    secureRandom.nextBytes(ihuIv)
    secureRandom.nextBytes(mobileIv)
    val oobData = OobData(secretKey.encoded, ihuIv, mobileIv)
    establishedOobChannels.clear()
    val oobChannel = TransportOobChannel(delegate, oobProtocolName)
    if (oobChannel.completeOobDataExchange(toOobProto(oobData))) {
      establishedOobChannels.add(oobChannel)
    }
    return oobData
  }

  /** Encrypt [data] with OOB key, throw exception when encryption failed. */
  @Throws(IllegalStateException::class)
  open fun encryptData(data: ByteArray): ByteArray {
    return try {
      cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, IvParameterSpec(ihuIv))
      cipher.doFinal(data)
    } catch (e: Exception) {
      loge(TAG, "Encountered exception when encrypt data.", e)
      throw IllegalStateException("Failed to encrypt data", e)
    }
  }

  /** Decrypt [data] with OOB key, throw exception when decryption failed. */
  @Throws(IllegalStateException::class)
  open fun decryptData(data: ByteArray): ByteArray {
    return try {
      cipher.init(Cipher.DECRYPT_MODE, encryptionKey, IvParameterSpec(mobileIv))
      cipher.doFinal(data)
    } catch (e: Exception) {
      loge(TAG, "Encountered exception when decrypt data.", e)
      throw IllegalStateException("Failed to decrypt data", e)
    }
  }

  /** Reset OOB data, interrupt any ongoing data exchange and prevent invoke of callback */
  open fun reset() {
    logd(TAG, "Reset OOB key and interrupt OOB channel.")
    for (channel in establishedOobChannels) {
      channel.interrupt()
    }
    establishedOobChannels.clear()
    ihuIv = ByteArray(NONCE_LENGTH_BYTES)
    mobileIv = ByteArray(NONCE_LENGTH_BYTES)
    encryptionKey = null
  }

  private fun toOobProto(oobData: OobData): ByteArray {
    return OutOfBandAssociationToken.newBuilder()
      .run {
        setEncryptionKey(ByteString.copyFrom(oobData.encryptionKey))
        setIhuIv(ByteString.copyFrom(oobData.ihuIv))
        setMobileIv(ByteString.copyFrom(oobData.mobileIv))
        build()
      }
      .toByteArray()
  }

  companion object {
    private const val TAG = "OobRunner"
    private const val ALGORITHM = "AES/GCM/NoPadding"

    // The nonce length is chosen to be consistent with the standard specification:
    // Section 8.2 of https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38d.pdf
    private const val NONCE_LENGTH_BYTES = 12
  }
}
