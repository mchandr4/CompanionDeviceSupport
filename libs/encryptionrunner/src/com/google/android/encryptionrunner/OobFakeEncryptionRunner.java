package com.google.android.encryptionrunner;

import java.util.Arrays;

/**
 * An encryption runner that doesn't actually do encryption. Useful for debugging out of band
 * association. Do not use in production environments.
 */
public class OobFakeEncryptionRunner extends FakeEncryptionRunner {
  @Override
  public HandshakeMessage continueHandshake(byte[] response) throws HandshakeException {
    if (getState() != HandshakeMessage.HandshakeState.IN_PROGRESS) {
      throw new HandshakeException("not waiting for response but got one");
    }

    @HandshakeMessage.HandshakeState
    int newState = HandshakeMessage.HandshakeState.OOB_VERIFICATION_NEEDED;
    switch (getMode()) {
      case Mode.SERVER:
        if (!Arrays.equals(CLIENT_RESPONSE, response)) {
          throw new HandshakeException("unexpected response: " + new String(response));
        }
        setState(newState);
        return HandshakeMessage.newBuilder()
            .setOobVerificationCode(VERIFICATION_CODE.getBytes())
            .setHandshakeState(newState)
            .build();
      case Mode.CLIENT:
        if (!Arrays.equals(INIT_RESPONSE, response)) {
          throw new HandshakeException("unexpected response: " + new String(response));
        }
        setState(newState);
        return HandshakeMessage.newBuilder()
            .setHandshakeState(newState)
            .setNextMessage(CLIENT_RESPONSE)
            .setOobVerificationCode(VERIFICATION_CODE.getBytes())
            .build();
      default:
        throw new IllegalStateException("unexpected role: " + getMode());
    }
  }

  @Override
  public HandshakeMessage notifyPinVerified() throws HandshakeException {
    @HandshakeMessage.HandshakeState int state = getState();
    if (state != HandshakeMessage.HandshakeState.OOB_VERIFICATION_NEEDED) {
      throw new IllegalStateException("asking to verify pin, state = " + state);
    }
    state = HandshakeMessage.HandshakeState.FINISHED;
    return HandshakeMessage.newBuilder().setKey(new FakeKey()).setHandshakeState(state).build();
  }
}
