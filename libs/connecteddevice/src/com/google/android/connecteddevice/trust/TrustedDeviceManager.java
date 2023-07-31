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

package com.google.android.connecteddevice.trust;

import static com.google.android.connecteddevice.trust.TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_DEVICE_NOT_SECURED;
import static com.google.android.connecteddevice.trust.TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_DISCONNECTED_DURING_ENROLLMENT;
import static com.google.android.connecteddevice.trust.TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_MESSAGE_TYPE_UNKNOWN;
import static com.google.android.connecteddevice.trust.TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_UNKNOWN;
import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logi;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.app.ActivityManager;
import android.content.Context;
import android.os.IInterface;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.android.connecteddevice.api.IDeviceAssociationCallback;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback;
import com.google.android.connecteddevice.trust.api.IOnTrustedDevicesRetrievedListener;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceManager;
import com.google.android.connecteddevice.trust.api.TrustedDevice;
import com.google.android.connecteddevice.trust.proto.PhoneAuthProto.PhoneCredentials;
import com.google.android.connecteddevice.trust.proto.TrustedDeviceMessageProto.TrustedDeviceError;
import com.google.android.connecteddevice.trust.proto.TrustedDeviceMessageProto.TrustedDeviceError.ErrorType;
import com.google.android.connecteddevice.trust.proto.TrustedDeviceMessageProto.TrustedDeviceMessage;
import com.google.android.connecteddevice.trust.proto.TrustedDeviceMessageProto.TrustedDeviceMessage.MessageType;
import com.google.android.connecteddevice.trust.proto.TrustedDeviceMessageProto.TrustedDeviceState;
import com.google.android.connecteddevice.trust.storage.FeatureStateEntity;
import com.google.android.connecteddevice.trust.storage.TrustedDeviceDao;
import com.google.android.connecteddevice.trust.storage.TrustedDeviceDatabase;
import com.google.android.connecteddevice.trust.storage.TrustedDeviceDatabaseProvider;
import com.google.android.connecteddevice.trust.storage.TrustedDeviceEntity;
import com.google.android.connecteddevice.trust.storage.TrustedDeviceTokenEntity;
import com.google.android.connecteddevice.util.ByteUtils;
import com.google.android.connecteddevice.util.SafeConsumer;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/** Manager for the feature of unlocking the head unit with a user's trusted device. */
public class TrustedDeviceManager extends ITrustedDeviceManager.Stub {
  private static final String TAG = "TrustedDeviceManager";

  @VisibleForTesting static final int TRUSTED_DEVICE_MESSAGE_VERSION = 2;

  /** Length of token generated on a trusted device. */
  private static final int ESCROW_TOKEN_LENGTH = 8;

  private static final String SHA256 = "SHA-256";

  private final RemoteCallbackList<ITrustedDeviceCallback> remoteTrustedDeviceCallbacks =
      new RemoteCallbackList<>();

  private final RemoteCallbackList<ITrustedDeviceEnrollmentCallback> remoteEnrollmentCallbacks =
      new RemoteCallbackList<>();

  private final RemoteCallbackList<IOnTrustedDeviceEnrollmentNotificationCallback>
      remoteEnrollmentNotificationCallbacks = new RemoteCallbackList<>();

  private final RemoteCallbackList<IDeviceAssociationCallback> remoteDeviceAssociationCallbacks =
      new RemoteCallbackList<>();

  private final TrustedDeviceFeature trustedDeviceFeature;

  private final Executor databaseExecutor;

  private final Executor remoteCallbackExecutor;

  private final AtomicReference<String> pendingUnlockDeviceId = new AtomicReference<>(null);

  private final TrustedDeviceDao database;

  private ITrustedDeviceAgentDelegate trustAgentDelegate;

  private ConnectedDevice pendingDevice;

  private byte[] pendingToken;

  private PendingCredentials pendingCredentials;

  private final ReentrantLock enrollConditionsLock = new ReentrantLock();

  /**
   * Enrollment will only be processed when both [isEscrowTokenActivated] and [isCredentialVerified]
   * are set to true.
   *
   * <p>Implicitly, by the design of Android system, both [onEscrowTokenActivated] and
   * [onCredentialVerified] should be invoked on the main thread which would make the two calls
   * synchronized. However, to make sure Trusted Device will be enrolled and enrolled only once when
   * all conditions are met, we still guard each call by a lock .
   */
  private boolean isEscrowTokenActivated;

  protected boolean isCredentialVerified;

  /** If the enrollment is waiting for credential setup. */
  protected final AtomicBoolean isWaitingForCredentialSetUp = new AtomicBoolean(false);

  /** The pending handle of the token for the enrollment. */
  protected PendingHandle pendingHandle;

  TrustedDeviceManager(@NonNull Context context) {
    this(
        TrustedDeviceDatabaseProvider.get(context),
        new TrustedDeviceFeature(context),
        /* databaseExecutor= */ Executors.newSingleThreadExecutor(),
        /* remoteCallbackExecutor= */ Executors.newSingleThreadExecutor());
  }

  @VisibleForTesting
  TrustedDeviceManager(
      @NonNull TrustedDeviceDatabase database,
      @NonNull TrustedDeviceFeature trustedDeviceFeature,
      @NonNull Executor databaseExecutor,
      @NonNull Executor remoteCallbackExecutor) {
    this.database = database.trustedDeviceDao();
    this.databaseExecutor = databaseExecutor;
    this.remoteCallbackExecutor = remoteCallbackExecutor;
    this.trustedDeviceFeature = trustedDeviceFeature;

    trustedDeviceFeature.setCallback(featureCallback);
    trustedDeviceFeature.setAssociatedDeviceCallback(associatedDeviceCallback);
    trustedDeviceFeature.start();
    logd(TAG, "TrustedDeviceManager created successfully.");
  }

  @CallSuper
  void cleanup() {
    pendingToken = null;
    pendingDevice = null;
    pendingCredentials = null;
    pendingUnlockDeviceId.set(null);
    pendingHandle = null;
    isWaitingForCredentialSetUp.set(false);
    trustedDeviceFeature.stop();
    remoteDeviceAssociationCallbacks.kill();
    remoteTrustedDeviceCallbacks.kill();
    remoteEnrollmentCallbacks.kill();
    remoteEnrollmentNotificationCallbacks.kill();
  }

  private void startEnrollment(@NonNull ConnectedDevice device, @NonNull byte[] token) {
    logd(TAG, "Starting trusted device enrollment process.");
    pendingDevice = device;
    pendingToken = token;

    notifyRemoteCallbackList(
        remoteEnrollmentNotificationCallbacks,
        callback -> {
          try {
            callback.onTrustedDeviceEnrollmentNotificationRequest();
          } catch (RemoteException e) {
            loge(TAG, "Failed to notify the enrollment notification request.");
          }
        });
  }

  private void addEscrowToken() {
    if (pendingToken == null) {
      loge(TAG, "No pending token to be added.");
      notifyEnrollmentError(TRUSTED_DEVICE_ERROR_UNKNOWN);
      return;
    }
    // Do not notify enrollment error if TrustAgentService is not ready yet.
    // We'll make another attempt when the service is up.
    if (trustAgentDelegate == null) {
      logd(
          TAG,
          "No trust agent delegate has been set yet. No further enrollment action "
              + "can be taken at this time.");
      return;
    }
    if (!isWaitingForCredentialSetUp.get()) {
      logd(TAG, "Adding escrow token.");
      try {
        trustAgentDelegate.addEscrowToken(pendingToken, ActivityManager.getCurrentUser());
      } catch (RemoteException e) {
        loge(TAG, "Error while adding token through delegate.", e);
        notifyEnrollmentError(TRUSTED_DEVICE_ERROR_UNKNOWN);
      }
    }
  }

  private void unlockUser(@NonNull String deviceId, @NonNull PhoneCredentials credentials) {
    logd(TAG, "Unlocking with credentials.");
    try {
      pendingUnlockDeviceId.set(deviceId);
      trustAgentDelegate.unlockUserWithToken(
          credentials.getEscrowToken().toByteArray(),
          ByteUtils.bytesToLong(credentials.getHandle().toByteArray()),
          ActivityManager.getCurrentUser());
    } catch (RemoteException e) {
      loge(TAG, "Error while unlocking user through delegate.", e);
      pendingUnlockDeviceId.set(null);
    }
  }

  @Override
  public void sendUnlockRequest() {
    List<ConnectedDevice> devices = getActiveUserConnectedDevices();
    if (devices.size() != 1) {
      loge(TAG, "Cannot resolve recipient device, cannot send unlock request.");
      return;
    }

    String deviceId = devices.get(0).getDeviceId();
    logd(TAG, "Sending unlock request to device " + deviceId);
    trustedDeviceFeature.sendMessageSecurely(deviceId, createUnlockRequestMessage());
  }

  @Override
  public void onEscrowTokenAdded(int userId, long handle) {
    logd(TAG, "Escrow token has been successfully added.");
    pendingHandle = new PendingHandle(userId, handle);

    notifyRemoteEnrollmentCallbacks(
        callback -> {
          try {
            callback.onValidateCredentialsRequest();
          } catch (RemoteException e) {
            loge(TAG, "Error while requesting credential validation.", e);
          }
        });
  }

  /**
   * Implicitly, by the design of Android system, both [onEscrowTokenActivated] and
   * [onCredentialVerified] should be invoked on the main thread which would make the two calls
   * synchronized. However, to make sure Trusted Device will be enrolled and enrolled only once when
   * all conditions are met, we still guard each call by a lock .
   */
  @Override
  public void onEscrowTokenActivated(int userId, long handle) {
    logd(TAG, "Escrow token has been activated");
    enrollConditionsLock.lock();
    try {
      isEscrowTokenActivated = true;
      if (!isCredentialVerified) {
        logd(
            TAG,
            "User "
                + userId
                + " hasn't confirmed their credential yet. Waiting for the user to confirm.");
        return;
      }
      logd(TAG, "Enroll in Trusted Device for user " + userId);
      enrollInTrustedDevice(userId, handle);
    } finally {
      enrollConditionsLock.unlock();
    }
  }

  @Override
  public void onCredentialVerified() {
    logd(TAG, "User has verified their credential.");
    enrollConditionsLock.lock();
    try {
      isCredentialVerified = true;
      if (!isEscrowTokenActivated) {
        logd(TAG, "Escrow token is not activated. Waiting for system to activate the token.");
        return;
      }
      if (pendingHandle == null) {
        loge(TAG, "Pending handle not found. Abort enrollment.");
        notifyEnrollmentError(TRUSTED_DEVICE_ERROR_UNKNOWN);
      } else {
        enrollInTrustedDevice(pendingHandle.userId, pendingHandle.handle);
      }
    } finally {
      enrollConditionsLock.unlock();
    }
  }

  @Override
  public void onUserUnlocked() {
    String deviceId = pendingUnlockDeviceId.getAndSet(null);
    if (deviceId == null) {
      loge(TAG, "No pending trusted device is waiting for the unlocked ACK message.");
      return;
    }
    logd(TAG, "Sending ACK message to device " + deviceId);
    trustedDeviceFeature.sendMessageSecurely(deviceId, createAcknowledgmentMessage());
  }

  @Override
  public void retrieveTrustedDevicesForActiveUser(IOnTrustedDevicesRetrievedListener listener) {
    databaseExecutor.execute(
        () -> {
          List<TrustedDevice> devices = getTrustedDevicesForActiveUser();
          try {
            listener.onTrustedDevicesRetrieved(devices);
          } catch (RemoteException exception) {
            loge(TAG, "Failed to notify that trusted devices are retrieved.", exception);
          }
        });
  }

  @Override
  public void removeTrustedDevice(TrustedDevice trustedDevice) {
    logd(TAG, "Received request to remove trusted device");
    databaseExecutor.execute(
        () -> {
          TrustedDeviceEntity entity =
              database.getTrustedDeviceIfValid(trustedDevice.getDeviceId());
          if (entity == null) {
            return;
          }
          invalidateTrustedDevice(entity);
          removeTrustedDeviceInternal(entity);
        });
  }

  @Override
  public List<ConnectedDevice> getActiveUserConnectedDevices() {
    return trustedDeviceFeature.getConnectedDevices();
  }

  @Override
  public void initiateEnrollment(String deviceId) {
    trustedDeviceFeature.sendMessageSecurely(deviceId, createStartEnrollmentMessage());
  }

  @Override
  public void processEnrollment(boolean isDeviceSecure) {
    if (isDeviceSecure) {
      isWaitingForCredentialSetUp.set(false);
      logd(TAG, "Processing enrollment on secure device.");
      addEscrowToken();
      return;
    }
    logd(
        TAG,
        "Request to process enrollment on insecure device. "
            + "Notifying callbacks of secure device request.");
    isWaitingForCredentialSetUp.set(true);
    notifyRemoteEnrollmentCallbacks(
        callback -> {
          try {
            callback.onSecureDeviceRequest();
          } catch (RemoteException e) {
            loge(TAG, "Error notifying callbacks of secure device request.", e);
          }
        });
  }

  @Override
  public void abortEnrollment() {
    logd(TAG, "Aborting enrollment");
    if (pendingHandle != null) {
      logd(TAG, "Removing added token.");
      removeEscrowToken(pendingHandle.handle, pendingHandle.userId);
    }
    reset();
  }

  @Override
  public void registerTrustedDeviceCallback(ITrustedDeviceCallback callback) {
    remoteTrustedDeviceCallbacks.register(callback);
  }

  @Override
  public void unregisterTrustedDeviceCallback(ITrustedDeviceCallback callback) {
    remoteTrustedDeviceCallbacks.unregister(callback);
  }

  @Override
  public void registerAssociatedDeviceCallback(IDeviceAssociationCallback callback) {
    remoteDeviceAssociationCallbacks.register(callback);
  }

  @Override
  public void unregisterAssociatedDeviceCallback(IDeviceAssociationCallback callback) {
    remoteDeviceAssociationCallbacks.unregister(callback);
  }

  @Override
  public void registerTrustedDeviceEnrollmentCallback(ITrustedDeviceEnrollmentCallback callback) {
    remoteEnrollmentCallbacks.register(callback);
  }

  @Override
  public void unregisterTrustedDeviceEnrollmentCallback(ITrustedDeviceEnrollmentCallback callback) {
    remoteEnrollmentCallbacks.unregister(callback);
  }

  @Override
  public void registerTrustedDeviceEnrollmentNotificationCallback(
      IOnTrustedDeviceEnrollmentNotificationCallback callback) {
    remoteEnrollmentNotificationCallbacks.register(callback);
  }

  @Override
  public void unregisterTrustedDeviceEnrollmentNotificationCallback(
      IOnTrustedDeviceEnrollmentNotificationCallback callback) {
    remoteEnrollmentNotificationCallbacks.unregister(callback);
  }

  @Override
  public void setTrustedDeviceAgentDelegate(ITrustedDeviceAgentDelegate trustAgentDelegate) {
    setTrustedDeviceAgentDelegateInternal(trustAgentDelegate);

    // Add pending token if present.
    addEscrowToken();
  }

  @Override
  public void clearTrustedDeviceAgentDelegate(
      ITrustedDeviceAgentDelegate trustAgentDelegate,
      boolean isDeviceSecure) {
    if (trustAgentDelegate.asBinder() != this.trustAgentDelegate.asBinder()) {
      logd(
          TAG,
          "TrustedDeviceAgentDelegate "
              + trustAgentDelegate
              + " doesn't match the "
              + "current TrustedDeviceAgentDelegate: "
              + this.trustAgentDelegate
              + ". Ignoring call to clear.");
      return;
    }
    logd(TAG, "Clear current TrustedDeviceAgentDelegate: " + trustAgentDelegate + ".");
    this.trustAgentDelegate = null;
    if (isDeviceSecure) {
      return;
    }
    logd(TAG, "No lock screen credential set. Invalidating all enrolled trusted devices.");
    invalidateAllTrustedDevices();
  }

  /**
   * Set trusted device delegate.
   *
   * <p>This method is expect to be called when a user profile credential is created.
   */
  protected void setTrustedDeviceAgentDelegateInternal(
      ITrustedDeviceAgentDelegate trustAgentDelegate) {
    logd(TAG, "Set trusted device agent delegate: " + trustAgentDelegate + ".");
    this.trustAgentDelegate = trustAgentDelegate;

    int userId = ActivityManager.getCurrentUser();

    // Remove invalid trusted devices.
    databaseExecutor.execute(
        () -> {
          byte[] stateMessage = createDisabledStateSyncMessage();
          List<TrustedDeviceEntity> entities = database.getInvalidTrustedDevicesForUser(userId);
          for (TrustedDeviceEntity entity : entities) {
            FeatureStateEntity stateEntity = new FeatureStateEntity(entity.id, stateMessage);
            databaseExecutor.execute(() -> database.addOrReplaceFeatureState(stateEntity));
            removeTrustedDeviceInternal(entity);
          }
        });

    // Unlock with pending credentials if present.
    if (pendingCredentials != null) {
      unlockUser(pendingCredentials.deviceId, pendingCredentials.phoneCredentials);
      pendingCredentials = null;
    }
  }

  /** Get the pending token for enrollment. */
  protected byte[] getPendingToken() {
    return pendingToken;
  }

  /** Get trusted device database. */
  protected TrustedDeviceDao getTrustedDeviceDatabase() {
    return database;
  }

  private void invalidateAllTrustedDevices() {
    databaseExecutor.execute(
        () -> {
          List<TrustedDeviceEntity> entities =
              database.getValidTrustedDevicesForUser(ActivityManager.getCurrentUser());
          for (TrustedDeviceEntity entity : entities) {
            invalidateTrustedDevice(entity);
          }
        });
  }

  /**
   * Invalidate a trusted device.
   *
   * @param entity of the trusted device to invalidate.
   */
  @WorkerThread
  protected void invalidateTrustedDevice(TrustedDeviceEntity entity) {
    logd(TAG, "Marking device " + entity.id + " as invalid.");
    entity.isValid = false;
    database.addOrReplaceTrustedDevice(entity);
    logd(TAG, "Removing hashed token for device " + entity.id + ".");
    database.removeTrustedDeviceHashedToken(entity.id);
    sendStateDisabledMessage(entity.id);
    notifyRemoteCallbackList(
        remoteTrustedDeviceCallbacks,
        callback -> {
          try {
            callback.onTrustedDeviceRemoved(entity.toTrustedDevice());
          } catch (RemoteException e) {
            loge(TAG, "Failed to notify that a trusted device has been removed.", e);
          }
        });
  }

  /**
   * Remove an added or activated escrow token.
   *
   * @param handle of the token.
   * @param userId of the user that the token is added or activated for.
   * @return {@code true} if the token has been removed.
   */
  protected boolean removeEscrowToken(long handle, int userId) {
    if (trustAgentDelegate == null) {
      logw(
          TAG,
          "No trusted device delegate set when attempting to remove token for user "
              + userId + ".");
      return false;
    }
    logd(TAG, "Removing token for user " + userId + ".");
    try {
      trustAgentDelegate.removeEscrowToken(handle, userId);
    } catch (RemoteException e) {
      loge(TAG, "Error while removing token through delegate.", e);
      return false;
    }
    return true;
  }

  /** Notify remote enrollment callbacks. */
  protected void notifyRemoteEnrollmentCallbacks(
      SafeConsumer<ITrustedDeviceEnrollmentCallback> notification) {
    notifyRemoteCallbackList(remoteEnrollmentCallbacks, notification);
  }

  private void reset() {
    pendingToken = null;
    pendingHandle = null;
    pendingDevice = null;
    isEscrowTokenActivated = false;
    isCredentialVerified = false;
    isWaitingForCredentialSetUp.set(false);
  }

  @WorkerThread
  private void removeTrustedDeviceInternal(TrustedDeviceEntity entity) {
    logd(TAG, "Removing trusted device " + entity.id + ".");
    if (removeEscrowToken(entity.handle, entity.userId)) {
      database.removeTrustedDevice(entity);
    }
  }

  @WorkerThread
  private List<TrustedDevice> getTrustedDevicesForActiveUser() {
    List<TrustedDeviceEntity> foundEntities =
        database.getValidTrustedDevicesForUser(ActivityManager.getCurrentUser());

    List<TrustedDevice> trustedDevices = new ArrayList<>();
    if (foundEntities == null) {
      return trustedDevices;
    }

    for (TrustedDeviceEntity entity : foundEntities) {
      trustedDevices.add(entity.toTrustedDevice());
    }

    return trustedDevices;
  }

  @WorkerThread
  private boolean areCredentialsValid(@Nullable PhoneCredentials credentials, String deviceId) {
    if (credentials == null) {
      return false;
    }
    TrustedDeviceTokenEntity entity = database.getTrustedDeviceHashedToken(deviceId);
    if (entity == null) {
      loge(TAG, "Unable to find hashed token for device " + deviceId + ".");
      return false;
    }
    byte[] hashedToken =
        hashToken(credentials.getEscrowToken().toByteArray(), UUID.fromString(deviceId));
    if (hashedToken == null) {
      return false;
    }
    return MessageDigest.isEqual(
        hashedToken, ByteUtils.hexStringToByteArray(entity.getHashedToken()));
  }

  @Nullable
  private static byte[] hashToken(byte[] token, UUID deviceId) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance(SHA256);
    } catch (NoSuchAlgorithmException e) {
      loge(TAG, "Unable to find " + SHA256 + " algorithm. Token hash could not be generated.");
      return null;
    }

    digest.update(token);
    digest.update(ByteUtils.uuidToBytes(deviceId));
    return digest.digest();
  }

  private void sendStateDisabledMessage(@NonNull String deviceId) {
    byte[] stateMessage = createDisabledStateSyncMessage();
    ConnectedDevice device = trustedDeviceFeature.getConnectedDeviceById(deviceId);

    if (device != null) {
      logd(TAG, "Enrolled car currently connected. Sending feature state sync message to it.");
      trustedDeviceFeature.sendMessageSecurely(device, stateMessage);
      return;
    }

    logd(
        TAG,
        "Trusted device enrollment status cleared, but vehicle not currently connected. "
            + "Saving status to send on next connection.");

    FeatureStateEntity stateEntity = new FeatureStateEntity(deviceId, stateMessage);
    databaseExecutor.execute(() -> database.addOrReplaceFeatureState(stateEntity));
  }

  private void processEnrollmentMessage(
      @NonNull ConnectedDevice device, @Nullable ByteString payload) {
    if (payload == null) {
      logw(TAG, "Received enrollment message with null payload. Ignoring.");
      return;
    }

    byte[] message = payload.toByteArray();
    if (message.length != ESCROW_TOKEN_LENGTH) {
      logw(TAG, "Received invalid escrow token of length " + message.length + ". Ignoring.");
      return;
    }

    startEnrollment(device, message);
  }

  private void enrollInTrustedDevice(int userId, long handle) {
    if (pendingDevice == null) {
      loge(TAG, "Unable to complete device enrollment. Pending device is null.");
      notifyEnrollmentError(TRUSTED_DEVICE_ERROR_UNKNOWN);
      return;
    }
    byte[] hashedToken = hashToken(pendingToken, UUID.fromString(pendingDevice.getDeviceId()));
    if (hashedToken == null) {
      loge(TAG, "Unable to hash pending token. Aborting enrollment.");
      notifyEnrollmentError(TRUSTED_DEVICE_ERROR_UNKNOWN);
      return;
    }

    logd(
        TAG,
        "Enrollment completed successfully! Sending handle to connected device and "
            + "persisting trusted device record.");

    trustedDeviceFeature.sendMessageSecurely(pendingDevice, createHandleMessage(handle));

    String deviceId = pendingDevice.getDeviceId();

    TrustedDeviceEntity entity = new TrustedDeviceEntity(deviceId, userId, handle);
    TrustedDeviceTokenEntity tokenEntity =
        new TrustedDeviceTokenEntity(deviceId, ByteUtils.byteArrayToHexString(hashedToken));
    databaseExecutor.execute(
        () -> {
          database.removeFeatureState(deviceId);
          database.addOrReplaceTrustedDevice(entity);
          database.addOrReplaceTrustedDeviceHashedToken(tokenEntity);
        });

    reset();

    TrustedDevice trustedDevice = new TrustedDevice(deviceId, userId, handle);
    notifyRemoteCallbackList(
        remoteTrustedDeviceCallbacks,
        callback -> {
          try {
            callback.onTrustedDeviceAdded(trustedDevice);
          } catch (RemoteException e) {
            loge(TAG, "Failed to notify that enrollment completed successfully.", e);
          }
        });
  }

  private void processUnlockMessage(@NonNull ConnectedDevice device, @Nullable ByteString payload) {
    if (payload == null) {
      logw(TAG, "Received unlock message with null payload. Ignoring.");
      return;
    }
    byte[] message = payload.toByteArray();

    PhoneCredentials credentials;
    try {
      credentials = PhoneCredentials.parseFrom(message, ExtensionRegistryLite.getEmptyRegistry());
    } catch (InvalidProtocolBufferException e) {
      loge(TAG, "Unable to parse credentials from device. Not unlocking head unit.");
      return;
    }

    if (!areCredentialsValid(credentials, device.getDeviceId())) {
      loge(TAG, "Received invalid credentials from device. Not unlocking head unit.");
      return;
    }

    TrustedDeviceEntity entity = database.getTrustedDeviceIfValid(device.getDeviceId());

    if (entity == null) {
      logw(TAG, "Received unlock request from an untrusted device.");
      return;
    }

    if (entity.userId != ActivityManager.getCurrentUser()) {
      logw(TAG, "Received credentials from background user " + entity.userId + ". Ignoring.");
      return;
    }

    TrustedDeviceEventLog.onCredentialsReceived();

    if (trustAgentDelegate == null) {
      logd(TAG, "No trust agent delegate set yet. Credentials will be delivered once " + "set.");
      pendingCredentials = new PendingCredentials(device.getDeviceId(), credentials);
      return;
    }

    logd(
        TAG,
        "Received unlock credentials from trusted device "
            + device.getDeviceId()
            + ". Attempting unlock.");

    unlockUser(device.getDeviceId(), credentials);
  }

  private void processStatusSyncMessage(
      @NonNull ConnectedDevice device,
      @NonNull ByteString payload) {
    TrustedDeviceState state;
    try {
      state = TrustedDeviceState.parseFrom(payload, ExtensionRegistryLite.getEmptyRegistry());
    } catch (InvalidProtocolBufferException e) {
      loge(TAG, "Received state sync message from client, but cannot parse. Ignoring", e);
      return;
    }

    // The user could only turn off trusted device on the phone when it is not connected. Thus,
    // only need to sync state if the feature is disabled. Otherwise, if the two devices are
    // connected and the feature is enabled, then the normal enrollment flow will be triggered.
    if (state.getEnabled()) {
      logi(
          TAG,
          "Received state sync message from client indicating feature enabled. "
              + "Nothing more to be done.");
      return;
    }

    TrustedDeviceEntity entity = database.getTrustedDeviceIfValid(device.getDeviceId());

    if (entity == null) {
      logw(TAG, "Received state sync message from an untrusted device.");
      return;
    }

    logd(
        TAG,
        "Received state sync message from client indicating feature disabled. "
            + "Clearing enrollment state.");

    removeTrustedDevice(entity.toTrustedDevice());
  }

  private void processErrorMessage(@Nullable ByteString payload) {
    if (payload == null) {
      logw(TAG, "Received error message with null payload. Ignoring.");
      return;
    }
    TrustedDeviceError trustedDeviceError;
    try {
      trustedDeviceError =
          TrustedDeviceError.parseFrom(payload, ExtensionRegistryLite.getEmptyRegistry());
    } catch (InvalidProtocolBufferException e) {
      loge(TAG, "Received error message from client, but cannot parse.", e);
      notifyEnrollmentError(TRUSTED_DEVICE_ERROR_UNKNOWN);
      return;
    }
    int errorType = trustedDeviceError.getTypeValue();
    int error;
    switch (errorType) {
      case ErrorType.MESSAGE_TYPE_UNKNOWN_VALUE:
        error = TRUSTED_DEVICE_ERROR_MESSAGE_TYPE_UNKNOWN;
        break;
      case ErrorType.DEVICE_NOT_SECURED_VALUE:
        error = TRUSTED_DEVICE_ERROR_DEVICE_NOT_SECURED;
        break;
      default:
        loge(TAG, "Encountered unexpected error type: " + errorType + ".");
        error = TRUSTED_DEVICE_ERROR_UNKNOWN;
    }
    notifyEnrollmentError(error);
  }

  private void notifyEnrollmentError(@TrustedDeviceConstants.TrustedDeviceError int error) {
    notifyRemoteEnrollmentCallbacks(
        callback -> {
          try {
            callback.onTrustedDeviceEnrollmentError(error);
          } catch (RemoteException e) {
            loge(TAG, "Error while notifying enrollment error.", e);
          }
        });
  }

  private <T extends IInterface> void notifyRemoteCallbackList(
      RemoteCallbackList<T> remoteCallbackList, SafeConsumer<T> notification) {
    int num = remoteCallbackList.beginBroadcast();
    for (int i = 0; i < num; i++) {
      T callback = remoteCallbackList.getBroadcastItem(i);
      remoteCallbackExecutor.execute(() -> notification.accept(callback));
    }
    remoteCallbackList.finishBroadcast();
  }

  private void onSecureChannelEstablished(@NonNull ConnectedDevice device) {
    databaseExecutor.execute(
        () -> {
          String deviceId = device.getDeviceId();
          TrustedDeviceEntity entity = database.getTrustedDeviceIfValid(deviceId);

          if (entity != null && entity.userId != ActivityManager.getCurrentUser()) {
            logd(
                TAG,
                "UserID of trusted device is different than current user."
                    + "Removing device and clearing state.");
            database.removeFeatureState(deviceId);
            removeTrustedDevice(entity.toTrustedDevice());
            return;
          }

          FeatureStateEntity stateEntity = database.getFeatureState(deviceId);

          if (stateEntity == null) {
            logd(TAG, "A device has connected securely. No feature state messages to send to it.");
            return;
          }

          logd(TAG, "Connected device has stored feature state messages. Syncing now.");

          trustedDeviceFeature.sendMessageSecurely(device, stateEntity.state);
          database.removeFeatureState(deviceId);
        });
  }

  private static byte[] createAcknowledgmentMessage() {
    return TrustedDeviceMessage.newBuilder()
        .setVersion(TRUSTED_DEVICE_MESSAGE_VERSION)
        .setType(MessageType.ACK)
        .build()
        .toByteArray();
  }

  private static byte[] createHandleMessage(long handle) {
    return TrustedDeviceMessage.newBuilder()
        .setVersion(TRUSTED_DEVICE_MESSAGE_VERSION)
        .setType(MessageType.HANDLE)
        .setPayload(ByteString.copyFrom(ByteUtils.longToBytes(handle)))
        .build()
        .toByteArray();
  }

  private static byte[] createStartEnrollmentMessage() {
    return TrustedDeviceMessage.newBuilder()
        .setVersion(TRUSTED_DEVICE_MESSAGE_VERSION)
        .setType(MessageType.START_ENROLLMENT)
        .build()
        .toByteArray();
  }

  @NonNull
  private static byte[] createDisabledStateSyncMessage() {
    TrustedDeviceState state = TrustedDeviceState.newBuilder().setEnabled(false).build();

    return TrustedDeviceMessage.newBuilder()
        .setVersion(TrustedDeviceManager.TRUSTED_DEVICE_MESSAGE_VERSION)
        .setType(MessageType.STATE_SYNC)
        .setPayload(state.toByteString())
        .build()
        .toByteArray();
  }

  private static byte[] createUnlockRequestMessage() {
    return TrustedDeviceMessage.newBuilder()
        .setVersion(TRUSTED_DEVICE_MESSAGE_VERSION)
        .setType(MessageType.UNLOCK_REQUEST)
        .build()
        .toByteArray();
  }

  @VisibleForTesting
  final TrustedDeviceFeature.Callback featureCallback =
      new TrustedDeviceFeature.Callback() {
        @Override
        public void onMessageReceived(ConnectedDevice device, byte[] message) {
          TrustedDeviceMessage trustedDeviceMessage;
          try {
            trustedDeviceMessage =
                TrustedDeviceMessage.parseFrom(message, ExtensionRegistryLite.getEmptyRegistry());
          } catch (InvalidProtocolBufferException e) {
            loge(TAG, "Received message from client, but cannot parse.", e);
            return;
          }

          switch (trustedDeviceMessage.getType()) {
            case ESCROW_TOKEN:
              processEnrollmentMessage(device, trustedDeviceMessage.getPayload());
              break;
            case UNLOCK_CREDENTIALS:
              processUnlockMessage(device, trustedDeviceMessage.getPayload());
              break;
            case ACK:
              // The client sends an acknowledgment when the handle has been received, but
              // nothing needs to be on the IHU side. So simply log this message.
              logd(TAG, "Received acknowledgment message from client.");
              break;
            case ERROR:
              processErrorMessage(trustedDeviceMessage.getPayload());
              break;
            case STATE_SYNC:
              // Note: proto does not allow payload to be null.
              processStatusSyncMessage(device, trustedDeviceMessage.getPayload());
              break;
            default:
              // The client should only be sending requests to either enroll or unlock.
              loge(
                  TAG,
                  "Received a message from the client with an invalid MessageType ( "
                      + trustedDeviceMessage.getType().name()
                      + "). Ignoring.");
          }
        }

        @Override
        public void onSecureChannelEstablished(ConnectedDevice device) {
          TrustedDeviceManager.this.onSecureChannelEstablished(device);
        }

        @Override
        public void onDeviceDisconnected() {
          handleDisconnection();
        }

        @Override
        public void onDeviceError(ConnectedDevice device, int error) {}
      };

  private void handleDisconnection() {
    // Pending credentials should only be kept within the connected session.
    pendingCredentials = null;

    // If it's not enrollment flow, return directly.
    if (pendingToken == null) {
      logd(TAG, "Disconnected not during enrollment, ignore.");
      return;
    }

    logd(TAG, "Disconnected during enrollment, abort enrollment.");
    notifyEnrollmentError(TRUSTED_DEVICE_ERROR_DISCONNECTED_DURING_ENROLLMENT);
    notifyRemoteCallbackList(
        remoteEnrollmentNotificationCallbacks,
        callback -> {
          try {
            callback.onTrustedDeviceEnrollmentNotificationCancellation();
          } catch (RemoteException e) {
            loge(TAG, "Failed to notify the enrollment notification request.");
          }
        });
  }

  private final TrustedDeviceFeature.AssociatedDeviceCallback associatedDeviceCallback =
      new TrustedDeviceFeature.AssociatedDeviceCallback() {
        @Override
        public void onAssociatedDeviceAdded(AssociatedDevice device) {
          notifyRemoteCallbackList(
              remoteDeviceAssociationCallbacks,
              callback -> {
                try {
                  callback.onAssociatedDeviceAdded(device);
                } catch (RemoteException e) {
                  loge(TAG, "Failed to notify that an associated device has been added.", e);
                }
              });
        }

        @Override
        public void onAssociatedDeviceRemoved(AssociatedDevice device) {
          List<TrustedDevice> devices = getTrustedDevicesForActiveUser();
          if (devices.isEmpty()) {
            return;
          }
          TrustedDevice deviceToRemove = null;
          for (TrustedDevice trustedDevice : devices) {
            if (trustedDevice.getDeviceId().equals(device.getDeviceId())) {
              deviceToRemove = trustedDevice;
              break;
            }
          }
          if (deviceToRemove != null) {
            removeTrustedDevice(deviceToRemove);
          }
          notifyRemoteCallbackList(
              remoteDeviceAssociationCallbacks,
              callback -> {
                try {
                  callback.onAssociatedDeviceRemoved(device);
                } catch (RemoteException e) {
                  loge(TAG, "Failed to notify that an associated device has been " + "removed.", e);
                }
              });
        }

        @Override
        public void onAssociatedDeviceUpdated(AssociatedDevice device) {
          notifyRemoteCallbackList(
              remoteDeviceAssociationCallbacks,
              callback -> {
                try {
                  callback.onAssociatedDeviceUpdated(device);
                } catch (RemoteException e) {
                  loge(TAG, "Failed to notify that an associated device has been " + "updated.", e);
                }
              });
        }
      };

  private static class PendingCredentials {
    final String deviceId;
    final PhoneCredentials phoneCredentials;

    PendingCredentials(@NonNull String deviceId, @NonNull PhoneCredentials credentials) {
      this.deviceId = deviceId;
      phoneCredentials = credentials;
    }
  }

  /** Handle information for a pending token. */
  protected static class PendingHandle {
    final int userId;
    final long handle;

    PendingHandle(int userId, long handle) {
      this.userId = userId;
      this.handle = handle;
    }
  }
}
