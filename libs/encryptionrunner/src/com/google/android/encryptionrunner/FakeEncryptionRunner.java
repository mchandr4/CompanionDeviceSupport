package com.google.android.encryptionrunner;

import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

/**
 * An encryption runner that doesn't actually do encryption. Useful for debugging. Do not use in
 * production environments.
 */
public class FakeEncryptionRunner implements EncryptionRunner {

  private static final byte[] FAKE_MESSAGE = "Fake Message".getBytes();

  public static final byte[] INIT_MESSAGE = "init".getBytes();
  public static final byte[] INIT_RESPONSE = "initResponse".getBytes();
  public static final byte[] CLIENT_RESPONSE = "clientResponse".getBytes();
  public static final String VERIFICATION_CODE = "1234";

  /** The role that this runner is playing. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({Mode.UNKNOWN, Mode.CLIENT, Mode.SERVER})
  protected @interface Mode {
    int UNKNOWN = 0;
    int CLIENT = 1;
    int SERVER = 2;
  }

  private boolean isReconnect;
  private boolean isInitReconnectVerification;
  @Mode private int mode;

  @HandshakeMessage.HandshakeState private int state;

  @Override
  public HandshakeMessage initHandshake() throws HandshakeException {
    if (state != HandshakeMessage.HandshakeState.UNKNOWN) {
      throw new IllegalStateException("runner already initialized.");
    }

    mode = Mode.CLIENT;
    state = HandshakeMessage.HandshakeState.IN_PROGRESS;

    return HandshakeMessage.newBuilder()
        .setHandshakeState(state)
        .setNextMessage(INIT_MESSAGE)
        .build();
  }

  @Override
  public HandshakeMessage respondToInitRequest(byte[] initializationRequest)
      throws HandshakeException {
    if (state != HandshakeMessage.HandshakeState.UNKNOWN) {
      throw new IllegalStateException("runner already initialized.");
    }

    mode = Mode.SERVER;

    if (!Arrays.equals(INIT_MESSAGE, initializationRequest)) {
      throw new HandshakeException("Unexpected initialization request");
    }

    state = HandshakeMessage.HandshakeState.IN_PROGRESS;

    return HandshakeMessage.newBuilder()
        .setHandshakeState(HandshakeMessage.HandshakeState.IN_PROGRESS)
        .setNextMessage(INIT_RESPONSE)
        .build();
  }

  @Override
  public HandshakeMessage continueHandshake(byte[] response) throws HandshakeException {
    if (state != HandshakeMessage.HandshakeState.IN_PROGRESS) {
      throw new HandshakeException("not waiting for response but got one");
    }

    byte[] expectedResponse;
    byte[] nextMessage;

    switch (mode) {
      case Mode.SERVER:
        expectedResponse = CLIENT_RESPONSE;
        nextMessage = null;
        break;
      case Mode.CLIENT:
        expectedResponse = INIT_RESPONSE;
        nextMessage = CLIENT_RESPONSE;
        break;
      default:
        throw new IllegalStateException(
            "Encountered unexpected role during continuation of handshake: " + mode);
    }

    if (!Arrays.equals(expectedResponse, response)) {
      throw new HandshakeException(
          String.format(
              "Expected (%s) but received (%s) during handshake continuation",
              new String(expectedResponse), new String(response)));
    }

    // The state needs to be set to verification needed before a call to `verifyPin`.
    state = HandshakeMessage.HandshakeState.VERIFICATION_NEEDED;

    // If reconnecting, then blindly accept pairing code.
    if (isReconnect) {
      notifyPinVerified();
      state = HandshakeMessage.HandshakeState.RESUMING_SESSION;
    }

    return HandshakeMessage.newBuilder()
        .setVerificationCode(VERIFICATION_CODE)
        .setNextMessage(nextMessage)
        .setHandshakeState(state)
        .build();
  }

  @Override
  public HandshakeMessage authenticateReconnection(byte[] message, byte[] previousKey)
      throws HandshakeException {
    // Blindly verify the reconnection because this is a fake encryption runner.
    return HandshakeMessage.newBuilder()
        .setHandshakeState(HandshakeMessage.HandshakeState.FINISHED)
        .setKey(new FakeKey())
        .setNextMessage(isInitReconnectVerification ? null : FAKE_MESSAGE)
        .build();
  }

  @Override
  public HandshakeMessage initReconnectAuthentication(byte[] previousKey)
      throws HandshakeException {
    isInitReconnectVerification = true;
    state = HandshakeMessage.HandshakeState.RESUMING_SESSION;

    return HandshakeMessage.newBuilder()
        .setHandshakeState(state)
        .setNextMessage(FAKE_MESSAGE)
        .build();
  }

  @Override
  public Key keyOf(byte[] serialized) {
    return new FakeKey();
  }

  @Override
  public HandshakeMessage notifyPinVerified() throws HandshakeException {
    if (state != HandshakeMessage.HandshakeState.VERIFICATION_NEEDED) {
      throw new IllegalStateException("asking to verify pin, state = " + state);
    }

    state = HandshakeMessage.HandshakeState.FINISHED;
    return HandshakeMessage.newBuilder().setKey(new FakeKey()).setHandshakeState(state).build();
  }

  @Override
  public void notifyPinNotValidated() {
    state = HandshakeMessage.HandshakeState.INVALID;
  }

  @Override
  public void setIsReconnect(boolean isReconnect) {
    this.isReconnect = isReconnect;
  }

  @HandshakeMessage.HandshakeState
  protected int getState() {
    return state;
  }

  protected void setState(@HandshakeMessage.HandshakeState int state) {
    this.state = state;
  }

  @Mode
  protected int getMode() {
    return mode;
  }

  static class FakeKey implements Key {
    private static final byte[] KEY_BYTES = "key".getBytes();
    private static final byte[] UNIQUE_SESSION_BYTES = "unique_session".getBytes();

    @Override
    public byte[] asBytes() {
      return KEY_BYTES;
    }

    @Override
    public byte[] encryptData(byte[] data) {
      return data;
    }

    @Override
    public byte[] decryptData(byte[] encryptedData) {
      return encryptedData;
    }

    @Override
    public byte[] getUniqueSession() {
      return UNIQUE_SESSION_BYTES;
    }
  }
}
