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
import com.google.android.companionprotos.CapabilitiesExchangeProto.CapabilitiesExchange.OobChannelType
import com.google.android.companionprotos.OutOfBandAssociationToken
import com.google.android.connecteddevice.model.OobData
import com.google.android.connecteddevice.transport.ProtocolDevice
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logw
import com.google.protobuf.ByteString
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

/**
 * Manages all OOB related actions, those actions should be ordered as below:
 * 1. [generateOobData] returns [ByteArray] which contains the OOB key.
 * 2. [startOobDataExchange] with the remote [ProtocolDevice] and exchange OOB data.
 * 3. [encryptData] and [decryptData] data after the OOB data exchange succeed. Will throw
 * [IllegalStateException] if attempting to encrypt/decrypt without first having called
 * [generateOobData].
 */
open class OobRunner
@JvmOverloads
constructor(
  private val oobChannelFactory: OobChannelFactory,
  open val supportedTypes: List<String>,
  internal val keyAlgorithm: String = KeyProperties.KEY_ALGORITHM_AES
) {
  @VisibleForTesting internal var ihuIv = ByteArray(NONCE_LENGTH_BYTES)
  @VisibleForTesting internal var mobileIv = ByteArray(NONCE_LENGTH_BYTES)
  @VisibleForTesting internal var encryptionKey: SecretKey? = null
  private val cipher =
    try {
      Cipher.getInstance(ALGORITHM)
    } catch (e: Exception) {
      loge(TAG, "Unable to create cipher with $ALGORITHM.", e)
      throw IllegalStateException(e)
    }
  private var currentOobChannel: OobChannel? = null
  private var oobData: OobData? = null

  /** Generate OOB data which should be exchanged with remote device. */
  open fun generateOobData(): OobData {
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
    this.oobData = oobData
    return oobData
  }

  /**
   * Iterate through all available OOB channels, establish OOB channel and exchange OOB key with
   * remote device, result will be returned through [callback].
   */
  open fun startOobDataExchange(
    protocolDevice: ProtocolDevice,
    commonTypes: List<OobChannelType>,
    securityVersion: Int,
    callback: Callback
  ): Boolean {
    for (oobType in commonTypes) {
      if (oobType.name !in supportedTypes) {
        logw(TAG, "Unsupported OOB channel ${oobType.name}. Ignored.")
        continue
      }
      val oobChannel = oobChannelFactory.createOobChannel(oobType)
      if (oobChannel.completeOobDataExchange(
          protocolDevice,
          generateOobChannelCallback(
            oobChannel,
            callback,
            securityVersion >= MIN_SECURITY_VERSION_FOR_OOB_PROTO
          )
        )
      ) {
        currentOobChannel = oobChannel
        return true
      }
    }
    return false
  }

  private fun generateOobChannelCallback(
    oobChannel: OobChannel,
    callback: Callback,
    isProtoApplied: Boolean
  ) =
    object : OobChannel.Callback {
      override fun onOobExchangeSuccess() {
        val data = oobData
        if (data == null) {
          loge(
            TAG,
            "OOB channel established successfully with invalid OOB data, issue failure " +
              "callback."
          )
          callback.onOobDataExchangeFailure()
          return
        }
        oobChannel.sendOobData(if (isProtoApplied) toOobProto(data) else data.toRawBytes())
        callback.onOobDataExchangeSuccess()
      }
      override fun onOobExchangeFailure() {
        callback.onOobDataExchangeFailure()
      }
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
    currentOobChannel?.interrupt()
    ihuIv = ByteArray(NONCE_LENGTH_BYTES)
    mobileIv = ByteArray(NONCE_LENGTH_BYTES)
    encryptionKey = null
  }

  private fun toOobProto(oobData: OobData): ByteArray {
    return OutOfBandAssociationToken.newBuilder().run {
        setEncryptionKey(ByteString.copyFrom(oobData.encryptionKey))
        setIhuIv(ByteString.copyFrom(oobData.ihuIv))
        setMobileIv(ByteString.copyFrom(oobData.mobileIv))
        build()
      }
      .toByteArray()
  }

  private fun OobData.toRawBytes() = mobileIv + ihuIv + encryptionKey

  /** Callbacks for [OobRunner.startOobDataExchange] */
  interface Callback {
    /** Called when [OobRunner.startOobDataExchange] finishes successfully. */
    fun onOobDataExchangeSuccess()

    /** Called when OOB data exchange fails. */
    fun onOobDataExchangeFailure()
  }

  companion object {
    private const val TAG = "OobRunner"
    private const val ALGORITHM = "AES/GCM/NoPadding"
    @VisibleForTesting internal const val MIN_SECURITY_VERSION_FOR_OOB_PROTO = 4

    // The nonce length is chosen to be consistent with the standard specification:
    // Section 8.2 of https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38d.pdf
    private const val NONCE_LENGTH_BYTES = 12
  }
}
