package com.google.android.encryptionrunner;

import com.google.android.encryptionrunner.HandshakeMessage.HandshakeState;
import com.google.security.cryptauth.lib.securegcm.Ukey2Handshake;

/**
 * An {@link EncryptionRunner} that uses UKey2 as the underlying implementation, and generates a
 * longer token for the out of band verification step.
 *
 * <p>See go/aae-oob-batmobile-design for more info.
 *
 * <p>See go/ukey2 for more details on UKey2 itself.
 */
public class OobUkey2EncryptionRunner extends Ukey2EncryptionRunner {
  // Choose max verification string length supported by Ukey2
  private static final int VERIFICATION_STRING_LENGTH = 32;

  @Override
  public HandshakeMessage continueHandshake(byte[] response) throws HandshakeException {
    assertInitialized();

    Ukey2Handshake uKey2Client = getUkey2Client();

    try {
      if (uKey2Client.getHandshakeState() != Ukey2Handshake.State.IN_PROGRESS) {
        throw new IllegalStateException(
            "handshake is not in progress, state =" + uKey2Client.getHandshakeState());
      }
      uKey2Client.parseHandshakeMessage(response);

      // Not obvious from ukey2 api, but getting the next message can change the state.
      // calling getNext message might go from in progress to verification needed, on
      // the assumption that we already send this message to the peer.
      byte[] nextMessage = null;
      if (uKey2Client.getHandshakeState() == Ukey2Handshake.State.IN_PROGRESS) {
        nextMessage = uKey2Client.getNextHandshakeMessage();
      }

      byte[] verificationCode = null;
      if (uKey2Client.getHandshakeState() == Ukey2Handshake.State.VERIFICATION_NEEDED) {
        // getVerificationString() needs to be called before notifyPinVerified().
        verificationCode = uKey2Client.getVerificationString(VERIFICATION_STRING_LENGTH);
      }

      return HandshakeMessage.newBuilder()
          .setHandshakeState(HandshakeState.OOB_VERIFICATION_NEEDED)
          .setNextMessage(nextMessage)
          .setOobVerificationCode(verificationCode)
          .build();
    } catch (com.google.security.cryptauth.lib.securegcm.HandshakeException
        | Ukey2Handshake.AlertException e) {
      throw new HandshakeException(e);
    }
  }
}
