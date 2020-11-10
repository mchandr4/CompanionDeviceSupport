package com.google.android.encryptionrunner;

import androidx.annotation.NonNull;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

/** Represents a serializable encryption key. */
public interface Key {
  /** Returns a serialized encryption key. */
  @NonNull
  byte[] asBytes();

  /**
   * Encrypts data using this key.
   *
   * @param data the data to be encrypted
   * @return the encrypted data.
   */
  @NonNull
  byte[] encryptData(@NonNull byte[] data);

  /**
   * Decrypts data using this key.
   *
   * @param encryptedData The encrypted data.
   * @return decrypted data.
   * @throws SignatureException if encrypted data is not properly signed.
   */
  @NonNull
  byte[] decryptData(@NonNull byte[] encryptedData) throws SignatureException;

  /**
   * Returns a cryptographic digest of the key.
   *
   * @throws NoSuchAlgorithmException when a unique session can not be created.
   */
  @NonNull
  byte[] getUniqueSession() throws NoSuchAlgorithmException;
}
