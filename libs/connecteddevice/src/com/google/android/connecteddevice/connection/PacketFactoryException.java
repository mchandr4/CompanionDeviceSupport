package com.google.android.connecteddevice.connection;

/** Exception for signaling {@link PacketFactory} errors. */
class PacketFactoryException extends Exception {
  PacketFactoryException(String message) {
    super(message);
  }
}
