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

package com.google.android.connecteddevice.connection;

import static com.google.android.connecteddevice.util.SafeLog.loge;

import androidx.annotation.NonNull;
import com.google.android.connecteddevice.oob.OobConnectionManager;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage;
import com.google.android.encryptionrunner.EncryptionRunner;
import com.google.android.encryptionrunner.EncryptionRunnerFactory;
import com.google.android.encryptionrunner.HandshakeException;
import com.google.android.encryptionrunner.HandshakeMessage;
import com.google.android.encryptionrunner.HandshakeMessage.HandshakeState;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.Arrays;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

/** A secure channel established with the association flow with an out-of-band verification. */
public class OobAssociationSecureChannel extends AssociationSecureChannel {

  private static final String TAG = "OobAssociationSecureChannel";

  private final OobConnectionManager oobConnectionManager;

  private byte[] oobCode;

  public OobAssociationSecureChannel(
      DeviceMessageStream stream,
      ConnectedDeviceStorage storage,
      OobConnectionManager oobConnectionManager) {
    this(
        stream,
        storage,
        oobConnectionManager,
        EncryptionRunnerFactory.newRunner(EncryptionRunnerFactory.EncryptionRunnerType.OOB_UKEY2));
  }

  OobAssociationSecureChannel(
      DeviceMessageStream stream,
      ConnectedDeviceStorage storage,
      OobConnectionManager oobConnectionManager,
      EncryptionRunner encryptionRunner) {
    super(stream, storage, encryptionRunner);
    this.oobConnectionManager = oobConnectionManager;
  }

  @Override
  void processHandshake(@NonNull byte[] message) throws HandshakeException {
    switch (getState()) {
      case HandshakeState.IN_PROGRESS:
        processHandshakeInProgress(message);
        break;
      case HandshakeState.OOB_VERIFICATION_NEEDED:
        processHandshakeOobVerificationNeeded(message);
        break;
      default:
        super.processHandshake(message);
    }
  }

  private void processHandshakeInProgress(@NonNull byte[] message) throws HandshakeException {
    HandshakeMessage handshakeMessage = getEncryptionRunner().continueHandshake(message);
    setState(handshakeMessage.getHandshakeState());
    int state = getState();
    if (state != HandshakeState.OOB_VERIFICATION_NEEDED) {
      loge(
          TAG,
          "processHandshakeInProgress: Encountered unexpected handshake state: " + state + ".");
      notifySecureChannelFailure(CHANNEL_ERROR_INVALID_STATE);
      return;
    }

    oobCode = handshakeMessage.getFullVerificationCode();
    if (oobCode == null) {
      loge(TAG, "Unable to get out of band verification code.");
      notifySecureChannelFailure(CHANNEL_ERROR_INVALID_VERIFICATION);
      return;
    }
  }

  private void processHandshakeOobVerificationNeeded(@NonNull byte[] message) {
    byte[] decryptedCode;
    try {
      decryptedCode = oobConnectionManager.decryptVerificationCode(message);
    } catch (InvalidKeyException
        | InvalidAlgorithmParameterException
        | IllegalBlockSizeException
        | BadPaddingException e) {
      loge(TAG, "Decryption failed for verification code exchange", e);
      notifySecureChannelFailure(CHANNEL_ERROR_INVALID_HANDSHAKE);
      return;
    }

    if (oobCode == null || !Arrays.equals(oobCode, decryptedCode)) {
      loge(TAG, "Exchanged verification codes do not match. Aborting secure channel.");
      notifySecureChannelFailure(CHANNEL_ERROR_INVALID_VERIFICATION);
      return;
    }

    byte[] encryptedCode;
    try {
      encryptedCode = oobConnectionManager.encryptVerificationCode(oobCode);
    } catch (InvalidKeyException
        | InvalidAlgorithmParameterException
        | IllegalBlockSizeException
        | BadPaddingException e) {
      loge(TAG, "Encryption failed for verification code exchange.", e);
      notifySecureChannelFailure(CHANNEL_ERROR_INVALID_HANDSHAKE);
      return;
    }

    loge(TAG, "OOB accespted send encrpted code.");
    sendHandshakeMessage(encryptedCode, /* isEncrypted= */ false);

    notifyOutOfBandAccepted();
  }
}
