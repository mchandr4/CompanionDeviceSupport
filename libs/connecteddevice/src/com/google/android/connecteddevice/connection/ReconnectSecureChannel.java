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

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage;
import com.google.android.connecteddevice.util.ByteUtils;
import com.google.android.encryptionrunner.EncryptionRunner;
import com.google.android.encryptionrunner.EncryptionRunnerFactory;
import com.google.android.encryptionrunner.EncryptionRunnerFactory.EncryptionRunnerType;
import com.google.android.encryptionrunner.HandshakeException;
import com.google.android.encryptionrunner.HandshakeMessage;
import com.google.android.encryptionrunner.HandshakeMessage.HandshakeState;
import com.google.android.encryptionrunner.Key;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** A secure channel established with the reconnection flow. */
public class ReconnectSecureChannel extends SecureChannel {

  private static final String TAG = "ReconnectSecureChannel";

  private final ConnectedDeviceStorage storage;

  private final String deviceId;

  private final byte[] expectedChallengeResponse;

  private final AtomicBoolean hasVerifiedDevice = new AtomicBoolean(false);

  @HandshakeState private int state = HandshakeState.UNKNOWN;


  /**
   * Create a new secure reconnection channel.
   *
   * @param stream The {@link DeviceMessageStream} for communication with the device.
   * @param storage {@link ConnectedDeviceStorage} for secure storage.
   * @param deviceId Id of the device being reconnected.
   * @param expectedChallengeResponse Expected response to challenge issued in reconnect. Should
   *     pass {@code null} when device verification is not needed during the reconnection process.
   */
  public ReconnectSecureChannel(
      @NonNull DeviceMessageStream stream,
      @NonNull ConnectedDeviceStorage storage,
      @NonNull String deviceId,
      @Nullable byte[] expectedChallengeResponse) {
    super(stream, newReconnectRunner());
    this.storage = storage;
    this.deviceId = deviceId;
    if (expectedChallengeResponse == null) {
      // Skip the device verification step for spp reconnection
      hasVerifiedDevice.set(true);
    }
    this.expectedChallengeResponse = expectedChallengeResponse;
  }

  private static EncryptionRunner newReconnectRunner() {
    EncryptionRunner encryptionRunner =
        EncryptionRunnerFactory.newRunner(EncryptionRunnerType.UKEY2);
    encryptionRunner.setIsReconnect(true);
    return encryptionRunner;
  }

  @Override
  void processHandshake(byte[] message) throws HandshakeException {
    switch (state) {
      case HandshakeState.UNKNOWN:
        if (!hasVerifiedDevice.get()) {
          processHandshakeDeviceVerification(message);
        } else {
          processHandshakeInitialization(message);
        }
        break;
      case HandshakeState.IN_PROGRESS:
        processHandshakeInProgress(message);
        break;
      case HandshakeState.RESUMING_SESSION:
        processHandshakeResumingSession(message);
        break;
      default:
        loge(TAG, "Encountered unexpected handshake state: " + state + ".");
        notifySecureChannelFailure(CHANNEL_ERROR_INVALID_STATE);
    }
  }

  private void processHandshakeDeviceVerification(byte[] message) {
    byte[] challengeResponse = Arrays.copyOf(message, expectedChallengeResponse.length);
    byte[] deviceChallenge =
        Arrays.copyOfRange(message, expectedChallengeResponse.length, message.length);
    if (!Arrays.equals(expectedChallengeResponse, challengeResponse)) {
      notifySecureChannelFailure(CHANNEL_ERROR_INVALID_ENCRYPTION_KEY);
      return;
    }
    logd(TAG, "Responding to challenge " + ByteUtils.byteArrayToHexString(deviceChallenge) + ".");
    byte[] deviceChallengeResponse = storage.hashWithChallengeSecret(deviceId, deviceChallenge);
    if (deviceChallengeResponse == null) {
      notifySecureChannelFailure(CHANNEL_ERROR_STORAGE_ERROR);
    }
    sendHandshakeMessage(deviceChallengeResponse, /* isEncrypted= */ false);
    hasVerifiedDevice.set(true);
  }

  private void processHandshakeInitialization(byte[] message) throws HandshakeException {
    logd(TAG, "Responding to handshake init request.");
    HandshakeMessage handshakeMessage = getEncryptionRunner().respondToInitRequest(message);
    state = handshakeMessage.getHandshakeState();
    sendHandshakeMessage(handshakeMessage.getNextMessage(), /* isEncrypted= */ false);
  }

  private void processHandshakeInProgress(@NonNull byte[] message) throws HandshakeException {
    logd(TAG, "Continuing handshake.");
    HandshakeMessage handshakeMessage = getEncryptionRunner().continueHandshake(message);
    state = handshakeMessage.getHandshakeState();
  }

  private void processHandshakeResumingSession(@NonNull byte[] message) throws HandshakeException {
    logd(TAG, "Start reconnection authentication.");

    byte[] previousKey = storage.getEncryptionKey(deviceId);
    if (previousKey == null) {
      loge(TAG, "Unable to resume session, previous key is null.");
      notifySecureChannelFailure(CHANNEL_ERROR_INVALID_ENCRYPTION_KEY);
      return;
    }

    HandshakeMessage handshakeMessage =
        getEncryptionRunner().authenticateReconnection(message, previousKey);
    state = handshakeMessage.getHandshakeState();
    if (state != HandshakeState.FINISHED) {
      loge(TAG, "Unable to resume session, unexpected next handshake state: " + state + ".");
      notifySecureChannelFailure(CHANNEL_ERROR_INVALID_STATE);
      return;
    }

    Key newKey = handshakeMessage.getKey();
    if (newKey == null) {
      loge(TAG, "Unable to resume session, new key is null.");
      notifySecureChannelFailure(CHANNEL_ERROR_INVALID_ENCRYPTION_KEY);
      return;
    }

    storage.saveEncryptionKey(deviceId, newKey.asBytes());
    logd(TAG, "Saved new key for reconnection.");
    setEncryptionKey(newKey);
    sendServerAuthToClient(handshakeMessage.getNextMessage());
    notifyCallback(Callback::onSecureChannelEstablished);
  }

  private void sendServerAuthToClient(@Nullable byte[] message) {
    if (message == null) {
      loge(TAG, "Unable to send server authentication message to client, message is null.");
      notifySecureChannelFailure(CHANNEL_ERROR_INVALID_MSG);
      return;
    }

    sendHandshakeMessage(message, /* isEncrypted= */ false);
  }
}
