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

package com.google.android.connecteddevice.oob;

import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.security.keystore.KeyProperties;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.common.primitives.Bytes;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * This is a class that manages a token--{@link OobConnectionManager#encryptionKey}-- passed via an
 * out of band {@link OobChannel} that is distinct from the channel that is currently being secured.
 *
 * <p>Intended usage:
 *
 * <pre>{@code
 * OobConnectionManager oobConncetionManager = new OobConnectionManager();
 * oobConnectionManager.startOobExchange(channel);
 *
 * }</pre>
 *
 * <pre>{@code When a message is received:
 *   verificationCode = OobConnectionManager#decryptVerificationCode(byte[])
 *   check that verification code is valid
 *   if it is:
 *     encryptedMessage =  OobConnectionManager#encryptVerificationCode(byte[])
 *     send encryptedMessage
 *     verify handshake
 *   otherwise:
 *     fail handshake
 * }</pre>
 *
 * <pre>{@code
 * when oobData is received via the out of band channel:
 *   OobConnectionManager#setOobData(byte[])
 *
 * encryptedMessage = OobConnectionManager#encryptVerificationCode(byte[])
 * sendMessage
 * when a message is received:
 *   verificationCode = OobConnectionManager#decryptVerificationCode(byte[])
 *   check that verification code is valid
 *   if it is:
 *     verify handshake
 *   otherwise:
 *     fail handshake
 * }</pre>
 */
public class OobConnectionManager {
  private static final String TAG = "OobConnectionManager";
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  // The nonce length is chosen to be consistent with the standard specification:
  // Section 8.2 of https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38d.pdf
  @VisibleForTesting static final int NONCE_LENGTH_BYTES = 12;

  private final Cipher cipher;
  @VisibleForTesting byte[] encryptionIv = new byte[NONCE_LENGTH_BYTES];
  @VisibleForTesting byte[] decryptionIv = new byte[NONCE_LENGTH_BYTES];
  @VisibleForTesting SecretKey encryptionKey;

  public OobConnectionManager() {
    try {
      cipher = Cipher.getInstance(ALGORITHM);
    } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
      loge(TAG, "Unable to create cipher with " + ALGORITHM + ".", e);
      throw new IllegalStateException(e);
    }
  }

  /** Encrypts {@code verificationCode} using {@link OobConnectionManager#encryptionKey} */
  @NonNull
  public byte[] encryptVerificationCode(@NonNull byte[] verificationCode)
      throws InvalidAlgorithmParameterException, BadPaddingException, InvalidKeyException,
          IllegalBlockSizeException {
    cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new IvParameterSpec(encryptionIv));
    return cipher.doFinal(verificationCode);
  }

  /** Decrypts {@code encryptedMessage} using {@link OobConnectionManager#encryptionKey} */
  @NonNull
  public byte[] decryptVerificationCode(@NonNull byte[] encryptedMessage)
      throws InvalidAlgorithmParameterException, BadPaddingException, InvalidKeyException,
          IllegalBlockSizeException {
    cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new IvParameterSpec(decryptionIv));
    return cipher.doFinal(encryptedMessage);
  }

  void setOobData(@NonNull byte[] oobData) {
    encryptionIv = Arrays.copyOf(oobData, NONCE_LENGTH_BYTES);
    decryptionIv = Arrays.copyOfRange(oobData, NONCE_LENGTH_BYTES, NONCE_LENGTH_BYTES * 2);
    encryptionKey =
        new SecretKeySpec(
            Arrays.copyOfRange(oobData, NONCE_LENGTH_BYTES * 2, oobData.length),
            KeyProperties.KEY_ALGORITHM_AES);
  }

  /**
   * Start the out of band exchange with a given {@link OobChannel}.
   *
   * @param oobChannel Channel to be used for exchange.
   * @return {@code true} if exchange started successfully. {@code false} if an error occurred.
   */
  public boolean startOobExchange(@NonNull OobChannel oobChannel) {
    KeyGenerator keyGenerator = null;
    try {
      keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES);
    } catch (NoSuchAlgorithmException e) {
      loge(TAG, "Unable to get AES key generator.", e);
      return false;
    }
    encryptionKey = keyGenerator.generateKey();

    SecureRandom secureRandom = new SecureRandom();
    secureRandom.nextBytes(encryptionIv);
    secureRandom.nextBytes(decryptionIv);

    oobChannel.sendOobData(Bytes.concat(decryptionIv, encryptionIv, encryptionKey.getEncoded()));
    return true;
  }
}
