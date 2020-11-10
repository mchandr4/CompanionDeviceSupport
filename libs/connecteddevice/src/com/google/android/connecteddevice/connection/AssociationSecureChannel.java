package com.google.android.connecteddevice.connection;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.encryptionrunner.EncryptionRunnerFactory.newRunner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage;
import com.google.android.connecteddevice.util.ByteUtils;
import com.google.android.encryptionrunner.EncryptionRunner;
import com.google.android.encryptionrunner.EncryptionRunnerFactory.EncryptionRunnerType;
import com.google.android.encryptionrunner.HandshakeException;
import com.google.android.encryptionrunner.HandshakeMessage;
import com.google.android.encryptionrunner.HandshakeMessage.HandshakeState;
import com.google.android.encryptionrunner.Key;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.UUID;

/** A secure channel established with the association flow. */
public class AssociationSecureChannel extends SecureChannel {

  private static final String TAG = "AssociationSecureChannel";

  private static final int DEVICE_ID_BYTES = 16;

  private final ConnectedDeviceStorage storage;

  private ShowVerificationCodeListener showVerificationCodeListener;

  @HandshakeState private int state = HandshakeState.UNKNOWN;

  private Key pendingKey;

  private String deviceId;

  public AssociationSecureChannel(DeviceMessageStream stream, ConnectedDeviceStorage storage) {
    this(stream, storage, newRunner(EncryptionRunnerType.UKEY2));
  }

  AssociationSecureChannel(
      DeviceMessageStream stream,
      ConnectedDeviceStorage storage,
      EncryptionRunner encryptionRunner) {
    super(stream, encryptionRunner);
    encryptionRunner.setIsReconnect(false);
    this.storage = storage;
  }

  @Override
  void processHandshake(@NonNull byte[] message) throws HandshakeException {
    switch (state) {
      case HandshakeState.UNKNOWN:
        processHandshakeUnknown(message);
        break;
      case HandshakeState.IN_PROGRESS:
        processHandshakeInProgress(message);
        break;
      case HandshakeState.FINISHED:
        processHandshakeDeviceIdAndSecret(message);
        break;
      default:
        loge(TAG, "Encountered unexpected handshake state: " + state + ".");
        notifySecureChannelFailure(CHANNEL_ERROR_INVALID_STATE);
    }
  }

  private void processHandshakeUnknown(@NonNull byte[] message) throws HandshakeException {
    logd(TAG, "Responding to handshake init request.");
    HandshakeMessage handshakeMessage = getEncryptionRunner().respondToInitRequest(message);
    state = handshakeMessage.getHandshakeState();
    sendHandshakeMessage(handshakeMessage.getNextMessage(), /* isEncrypted= */ false);
  }

  private void processHandshakeInProgress(@NonNull byte[] message) throws HandshakeException {
    logd(TAG, "Continuing handshake.");
    HandshakeMessage handshakeMessage = getEncryptionRunner().continueHandshake(message);
    state = handshakeMessage.getHandshakeState();
    if (state != HandshakeState.VERIFICATION_NEEDED) {
      loge(
          TAG,
          "processHandshakeInProgress: Encountered unexpected handshake state: " + state + ".");
      notifySecureChannelFailure(CHANNEL_ERROR_INVALID_STATE);
      return;
    }

    String code = handshakeMessage.getVerificationCode();
    if (code == null) {
      loge(TAG, "Unable to get verification code.");
      notifySecureChannelFailure(CHANNEL_ERROR_INVALID_VERIFICATION);
      return;
    }
    processVerificationCode(code);
  }

  private void processVerificationCode(@NonNull String code) {
    if (showVerificationCodeListener == null) {
      loge(
          TAG,
          "No verification code listener has been set. Unable to display verification code to "
              + "user.");
      notifySecureChannelFailure(CHANNEL_ERROR_INVALID_STATE);
      return;
    }

    logd(TAG, "Showing pairing code: " + code);
    showVerificationCodeListener.showVerificationCode(code);
  }

  private void processHandshakeDeviceIdAndSecret(@NonNull byte[] message) {
    UUID deviceId = ByteUtils.bytesToUUID(Arrays.copyOf(message, DEVICE_ID_BYTES));
    if (deviceId == null) {
      loge(TAG, "Received invalid device id. Aborting.");
      notifySecureChannelFailure(CHANNEL_ERROR_INVALID_DEVICE_ID);
      return;
    }
    this.deviceId = deviceId.toString();
    notifyCallback(callback -> callback.onDeviceIdReceived(this.deviceId));

    storage.saveEncryptionKey(this.deviceId, pendingKey.asBytes());
    pendingKey = null;
    try {
      storage.saveChallengeSecret(
          this.deviceId, Arrays.copyOfRange(message, DEVICE_ID_BYTES, message.length));
    } catch (InvalidParameterException e) {
      loge(TAG, "Error saving challenge secret.", e);
      notifySecureChannelFailure(CHANNEL_ERROR_STORAGE_ERROR);
      return;
    }

    notifyCallback(Callback::onSecureChannelEstablished);
  }

  /** Set the listener that notifies to show verification code. {@code null} to clear. */
  public void setShowVerificationCodeListener(@Nullable ShowVerificationCodeListener listener) {
    showVerificationCodeListener = listener;
  }

  @VisibleForTesting
  @Nullable
  public ShowVerificationCodeListener getShowVerificationCodeListener() {
    return showVerificationCodeListener;
  }

  /**
   * Called by the client to notify that the user has accepted a pairing code or any out-of-band
   * confirmation, and send confirmation signals to remote bluetooth device.
   */
  public void notifyOutOfBandAccepted() {
    HandshakeMessage message;
    try {
      message = getEncryptionRunner().notifyPinVerified();
    } catch (HandshakeException e) {
      loge(TAG, "Error during PIN verification", e);
      notifySecureChannelFailure(CHANNEL_ERROR_INVALID_VERIFICATION);
      return;
    }
    if (message.getHandshakeState() != HandshakeState.FINISHED) {
      loge(
          TAG,
          "Handshake not finished after calling verify PIN. Instead got "
              + "state: "
              + message.getHandshakeState()
              + ".");
      notifySecureChannelFailure(CHANNEL_ERROR_INVALID_STATE);
      return;
    }

    Key localKey = message.getKey();
    if (localKey == null) {
      loge(TAG, "Unable to finish association, generated key is null.");
      notifySecureChannelFailure(CHANNEL_ERROR_INVALID_ENCRYPTION_KEY);
      return;
    }
    state = message.getHandshakeState();
    setEncryptionKey(localKey);
    pendingKey = localKey;
    logd(TAG, "Pairing code successfully verified.");
    sendUniqueIdToClient();
  }

  private void sendUniqueIdToClient() {
    UUID uniqueId = storage.getUniqueId();
    logd(TAG, "Sending car's device id of " + uniqueId + " to device.");
    sendHandshakeMessage(ByteUtils.uuidToBytes(uniqueId), /* isEncrypted= */ true);
  }

  @HandshakeState
  int getState() {
    return state;
  }

  void setState(@HandshakeState int state) {
    this.state = state;
  }

  /** Listener that will be invoked to display verification code. */
  public interface ShowVerificationCodeListener {
    /**
     * Invoke when a verification need to be displayed during device association.
     *
     * @param code The verification code to show.
     */
    void showVerificationCode(@NonNull String code);
  }
}
