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

package com.google.android.connecteddevice.storage;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.security.annotations.SuppressInsecureCipherModeCheckerNoReview;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;

/**
 * A {@link CryptoHelper} that utilizes the Android {@link KeyStore} to encrypt and decrypt values.
 */
final class KeyStoreCryptoHelper implements CryptoHelper {
  private static final String TAG = "KeystoreCryptoHelper";

  private static final String KEY_ALIAS = "Ukey2Key";
  private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
  private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";

  /**
   * A separator for use when encrypting values that allows this {@code KeyStoreCryptoHelper} to
   * embed the IV spec information for use when decrypting.
   *
   * <p>When encrypting, the returned value will be of the form:
   *
   * <p>{@code encrypted value + IV_SPEC_SEPARATOR + ivSpec}
   */
  private static final String IV_SPEC_SEPARATOR = ";";

  // The length of the authentication tag for a cipher in GCM mode. The GCM specification states
  // that this length can only have the values {128, 120, 112, 104, 96}. Using the highest
  // possible value.
  private static final int GCM_AUTHENTICATION_TAG_LENGTH = 128;

  @SuppressInsecureCipherModeCheckerNoReview
  @Override
  @Nullable
  public String encrypt(@Nullable byte[] value) {
    if (value == null) {
      logw(TAG, "Received a null value to encrypt. Returning null.");
      return null;
    }

    Key key = getKeyStoreKey(KEY_ALIAS);
    try {
      Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key);
      return Base64.encodeToString(cipher.doFinal(value), Base64.DEFAULT)
          + IV_SPEC_SEPARATOR
          + Base64.encodeToString(cipher.getIV(), Base64.DEFAULT);
    } catch (IllegalBlockSizeException
        | BadPaddingException
        | NoSuchAlgorithmException
        | NoSuchPaddingException
        | IllegalStateException
        | InvalidKeyException e) {
      loge(TAG, "Unable to encrypt value with key " + KEY_ALIAS, e);
      return null;
    }
  }

  @SuppressInsecureCipherModeCheckerNoReview
  @Override
  @Nullable
  public byte[] decrypt(@Nullable String value) {
    if (value == null) {
      logw(TAG, "Received a null value to decrypt. Returning null.");
      return null;
    }

    String[] values = value.split(IV_SPEC_SEPARATOR, -1);

    if (values.length != 2) {
      logd(TAG, "Stored encryption key had the wrong length.");
      return null;
    }

    byte[] encryptedValue = Base64.decode(values[0], Base64.DEFAULT);
    byte[] ivSpec = Base64.decode(values[1], Base64.DEFAULT);

    try {
      Key key = getKeyStoreKey(KEY_ALIAS);
      Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_AUTHENTICATION_TAG_LENGTH, ivSpec));
      return cipher.doFinal(encryptedValue);
    } catch (IllegalBlockSizeException
        | BadPaddingException
        | NoSuchAlgorithmException
        | NoSuchPaddingException
        | IllegalStateException
        | InvalidKeyException
        | InvalidAlgorithmParameterException e) {
      loge(TAG, "Unable to decrypt value with key " + KEY_ALIAS, e);
      return null;
    }
  }

  @Nullable
  private static Key getKeyStoreKey(@NonNull String keyAlias) {
    KeyStore keyStore;
    try {
      keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
      keyStore.load(null);
      if (!keyStore.containsAlias(keyAlias)) {
        KeyGenerator keyGenerator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
        keyGenerator.init(
            new KeyGenParameterSpec.Builder(
                    keyAlias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        keyGenerator.generateKey();
      }
      return keyStore.getKey(keyAlias, null);

    } catch (KeyStoreException
        | NoSuchAlgorithmException
        | UnrecoverableKeyException
        | NoSuchProviderException
        | CertificateException
        | IOException
        | InvalidAlgorithmParameterException e) {
      loge(TAG, "Unable to retrieve key " + keyAlias + " from KeyStore.", e);
      throw new IllegalStateException(e);
    }
  }
}
