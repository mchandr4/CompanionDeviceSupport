package com.google.android.encryptionrunner;

/** Exception indicating an error during a Handshake of EncryptionRunner. */
public class HandshakeException extends Exception {
  public HandshakeException(String message) {
    super(message);
  }

  public HandshakeException(Exception e) {
    super(e);
  }
}
