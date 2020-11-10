package com.google.android.connecteddevice.storage;

import androidx.annotation.Nullable;

/** A helper that can encrypt and decrypt arbitrary values. */
interface CryptoHelper {
  /**
   * Encrypts the given {@code value} and returns its encrypted value or {@code null} if encryption
   * fails.
   *
   * <p>Passing in {@code null} as the value will always just return {@code null}.
   */
  @Nullable
  String encrypt(@Nullable byte[] value);

  /**
   * Decrypts the given {@code value} and returns the decrypted value or {@code null} if decryption
   * cannot be performed.
   *
   * <p>Passing in {@code null} as the value will always just return {@code null}.
   */
  @Nullable
  byte[] decrypt(@Nullable String value);
}
