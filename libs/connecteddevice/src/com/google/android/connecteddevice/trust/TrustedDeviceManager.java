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

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logi;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.app.ActivityManager;
import android.content.Context;
import android.os.IInterface;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.android.connecteddevice.api.IConnectedDeviceManager;
import com.google.android.connecteddevice.api.IDeviceAssociationCallback;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationRequestListener;
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
import com.google.android.connecteddevice.util.ByteUtils;
import com.google.android.connecteddevice.util.SafeConsumer;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Manager for the feature of unlocking the head unit with a user's trusted device. */
public class TrustedDeviceManager extends ITrustedDeviceManager.Stub {
  private static final String TAG = "TrustedDeviceManager";

  @VisibleForTesting
  static final int TRUSTED_DEVICE_MESSAGE_VERSION = 2;

  /** Length of token generated on a trusted device. */
  private static final int ESCROW_TOKEN_LENGTH = 8;

  private final RemoteCallbackList<ITrustedDeviceCallback> remoteTrustedDeviceCallbacks =
      new RemoteCallbackList<>();

  private final RemoteCallbackList<ITrustedDeviceEnrollmentCallback> remoteEnrollmentCallbacks =
      new RemoteCallbackList<>();

  private final RemoteCallbackList<IOnTrustedDeviceEnrollmentNotificationRequestListener>
      remoteEnrollmentNotificationRequestListeners = new RemoteCallbackList<>();

  private final RemoteCallbackList<IDeviceAssociationCallback> remoteDeviceAssociationCallbacks =
      new RemoteCallbackList<>();

  private final TrustedDeviceFeature trustedDeviceFeature;

  private final Executor databaseExecutor;

  private final Executor remoteCallbackExecutor;

  private final AtomicBoolean isWaitingForCredentials = new AtomicBoolean(false);

  private final AtomicReference<String> pendingUnlockDeviceId = new AtomicReference<>(null);

  private final TrustedDeviceDao database;

  private ITrustedDeviceAgentDelegate trustAgentDelegate;

  private ConnectedDevice pendingDevice;

  private byte[] pendingToken;

  private PendingCredentials pendingCredentials;

  TrustedDeviceManager(@NonNull Context context) {
    this(context,
        TrustedDeviceDatabaseProvider.get(context),
        new TrustedDeviceFeature(context),
        /* databaseExecutor= */ Executors.newSingleThreadExecutor(),
        /* remoteCallbackExecutor= */ Executors.newSingleThreadExecutor());
  }

  @VisibleForTesting
  TrustedDeviceManager(
      @NonNull Context context,
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

  void cleanup() {
    pendingToken = null;
    pendingDevice = null;
    pendingCredentials = null;
    isWaitingForCredentials.set(false);
    pendingUnlockDeviceId.set(null);
    trustedDeviceFeature.stop();
    remoteDeviceAssociationCallbacks.kill();
    remoteTrustedDeviceCallbacks.kill();
    remoteEnrollmentCallbacks.kill();
    remoteEnrollmentNotificationRequestListeners.kill();
  }

  private void startEnrollment(@NonNull ConnectedDevice device, @NonNull byte[] token) {
    logd(TAG, "Starting trusted device enrollment process.");
    pendingDevice = device;

    notifyRemoteCallbackList(
        remoteEnrollmentNotificationRequestListeners,
        listener -> {
          try {
            listener.onTrustedDeviceEnrollmentNotificationRequest();
          } catch (RemoteException e) {
            loge(TAG, "Failed to notify the enrollment notification request.");
          }
        });

    pendingToken = token;
    if (trustAgentDelegate == null) {
      logd(
          TAG,
          "No trust agent delegate has been set yet. No further enrollment action "
              + "can be taken at this time.");
      return;
    }

    try {
      trustAgentDelegate.addEscrowToken(token, ActivityManager.getCurrentUser());
    } catch (RemoteException e) {
      loge(TAG, "Error while adding token through delegate.", e);
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
  public void onEscrowTokenAdded(int userId, long handle) {
    logd(TAG, "Escrow token has been successfully added.");
    pendingToken = null;

    if (remoteEnrollmentCallbacks.getRegisteredCallbackCount() == 0) {
      isWaitingForCredentials.set(true);
      return;
    }

    isWaitingForCredentials.set(false);
    notifyRemoteCallbackList(
        remoteEnrollmentCallbacks,
        callback -> {
          try {
            callback.onValidateCredentialsRequest();
          } catch (RemoteException e) {
            loge(TAG, "Error while requesting credential validation.", e);
          }
        });
  }

  @Override
  public void onEscrowTokenActivated(int userId, long handle) {
    if (pendingDevice == null) {
      loge(TAG, "Unable to complete device enrollment. Pending device was null.");
      return;
    }

    logd(
        TAG,
        "Enrollment completed successfully! Sending handle to connected device and "
            + "persisting trusted device record.");

    trustedDeviceFeature.sendMessageSecurely(pendingDevice, createHandleMessage(handle));

    String deviceId = pendingDevice.getDeviceId();

    TrustedDeviceEntity entity = new TrustedDeviceEntity(deviceId, userId, handle);
    databaseExecutor.execute(() -> {
      database.removeFeatureState(deviceId);
      database.addOrReplaceTrustedDevice(entity);
    });

    pendingDevice = null;

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
          TrustedDeviceEntity entity = database.getTrustedDevice(trustedDevice.getDeviceId());
          if (entity == null) {
            return;
          }
          invalidateTrustedDevice(entity);
          removeTrustedDeviceInternal(entity);
        });
  }

  @Override
  public List<ConnectedDevice> getActiveUserConnectedDevices() {
    List<ConnectedDevice> devices = new ArrayList<>();
    IConnectedDeviceManager manager = trustedDeviceFeature.getConnectedDeviceManager();
    if (manager == null) {
      loge(TAG, "Unable to get connected devices. Service not connected. ");
      return devices;
    }
    try {
      devices = manager.getActiveUserConnectedDevices();
    } catch (RemoteException e) {
      loge(TAG, "Failed to get connected devices. ", e);
    }
    return devices;
  }

  @Override
  public void initiateEnrollment(String deviceId) {
    trustedDeviceFeature.sendMessageSecurely(deviceId, createStartEnrollmentMessage());
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
    // A token has been added and is waiting on user credential validation.
    if (isWaitingForCredentials.getAndSet(false)) {
      remoteCallbackExecutor.execute(
          () -> {
            try {
              callback.onValidateCredentialsRequest();
            } catch (RemoteException e) {
              loge(TAG, "Error while notifying enrollment listener.", e);
            }
          });
    }
  }

  @Override
  public void unregisterTrustedDeviceEnrollmentCallback(ITrustedDeviceEnrollmentCallback callback) {
    remoteEnrollmentCallbacks.unregister(callback);
  }

  @Override
  public void registerTrustedDeviceEnrollmentNotificationRequestListener(
      IOnTrustedDeviceEnrollmentNotificationRequestListener listener) {
    remoteEnrollmentNotificationRequestListeners.register(listener);
  }

  @Override
  public void unregisterTrustedDeviceEnrollmentNotificationRequestListener(
      IOnTrustedDeviceEnrollmentNotificationRequestListener listener) {
    remoteEnrollmentNotificationRequestListeners.unregister(listener);
  }

  @Override
  public void setTrustedDeviceAgentDelegate(ITrustedDeviceAgentDelegate trustAgentDelegate) {
    logd(TAG, "Set trusted device agent delegate: " + trustAgentDelegate + ".");
    this.trustAgentDelegate = trustAgentDelegate;

    int userId = ActivityManager.getCurrentUser();

    // Remove invalid trusted devices.
    databaseExecutor.execute(
        () -> {
          List<TrustedDeviceEntity> entities = database.getInvalidTrustedDevicesForUser(userId);
          for (TrustedDeviceEntity entity : entities) {
            removeTrustedDeviceInternal(entity);
          }
        });

    // Add pending token if present.
    if (pendingToken != null) {
      try {
        trustAgentDelegate.addEscrowToken(pendingToken, userId);
      } catch (RemoteException e) {
        loge(TAG, "Error while adding token through delegate.", e);
      }
      return;
    }

    // Unlock with pending credentials if present.
    if (pendingCredentials != null) {
      unlockUser(pendingCredentials.deviceId, pendingCredentials.phoneCredentials);
      pendingCredentials = null;
    }
  }

  @Override
  public void clearTrustedDeviceAgentDelegate(ITrustedDeviceAgentDelegate trustAgentDelegate) {
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
    invalidateAllTrustedDevices();
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

  @WorkerThread
  private void invalidateTrustedDevice(TrustedDeviceEntity entity) {
    logd(TAG, "Marking device " + entity.id + " as invalid.");
    entity.isValid = false;
    database.addOrReplaceTrustedDevice(entity);
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

  @WorkerThread
  private void removeTrustedDeviceInternal(TrustedDeviceEntity entity) {
    if (trustAgentDelegate == null) {
      logw(
          TAG,
          "No trusted device delegate set when attempting to remove trusted device "
              + entity.id
              + ".");
      return;
    }
    logd(TAG, "Removing trusted device " + entity.id + ".");
    try {
      trustAgentDelegate.removeEscrowToken(entity.handle, entity.userId);
      database.removeTrustedDevice(entity);
    } catch (RemoteException e) {
      loge(TAG, "Error while removing token through delegate.", e);
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

  private static boolean areCredentialsValid(@Nullable PhoneCredentials credentials) {
    return credentials != null;
  }

  private void sendStateDisabledMessage(@NonNull String deviceId) {
    byte[] stateMessage = createDisabledStateSyncMessage();
    ConnectedDevice device = trustedDeviceFeature.getConnectedDeviceById(deviceId);

    if (device != null) {
      logd(TAG, "Enrolled car currently connected. Sending feature state sync message to it.");
      trustedDeviceFeature.sendMessageSecurely(device, stateMessage);
      return;
    }

    logd(TAG, "Trusted device enrollment status cleared, but vehicle not currently connected. "
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

  private void processUnlockMessage(@NonNull ConnectedDevice device, @Nullable ByteString payload) {
    if (payload == null) {
      logw(TAG, "Received unlock message with null payload. Ignoring.");
      return;
    }
    byte[] message = payload.toByteArray();

    PhoneCredentials credentials = null;
    try {
      credentials = PhoneCredentials.parseFrom(message, ExtensionRegistryLite.getEmptyRegistry());
    } catch (InvalidProtocolBufferException e) {
      loge(TAG, "Unable to parse credentials from device. Not unlocking head unit.");
      return;
    }

    if (!areCredentialsValid(credentials)) {
      loge(TAG, "Received invalid credentials from device. Not unlocking head unit.");
      return;
    }

    TrustedDeviceEntity entity = database.getTrustedDevice(device.getDeviceId());

    if (entity == null) {
      logw(TAG, "Received unlock request from an untrusted device.");
      // TODO(b/145618412) Notify device that it is no longer trusted.
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

  private void processStatusSyncMessage(@NonNull ConnectedDevice device,
      @NonNull ByteString payload) {
    TrustedDeviceState state = null;
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
      logi(TAG, "Received state sync message from client indicating feature enabled. "
          + "Nothing more to be done.");
      return;
    }

    TrustedDeviceEntity entity = database.getTrustedDevice(device.getDeviceId());

    if (entity == null) {
      logw(TAG, "Received state sync message from an untrusted device.");
      return;
    }

    logd(TAG, "Received state sync message from client indicating feature disabled. "
        + "Clearing enrollment state.");

    removeTrustedDevice(entity.toTrustedDevice());
  }

  private void processErrorMessage(@Nullable ByteString payload) {
    if (payload == null) {
      logw(TAG, "Received error message with null payload. Ignoring.");
      return;
    }
    TrustedDeviceError trustedDeviceError = null;
    try {
      trustedDeviceError =
          TrustedDeviceError.parseFrom(payload, ExtensionRegistryLite.getEmptyRegistry());
    } catch (InvalidProtocolBufferException e) {
      loge(TAG, "Received error message from client, but cannot parse.", e);
      notifyEnrollmentError(TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_UNKNOWN);
      return;
    }
    int errorType = trustedDeviceError.getTypeValue();
    int error;
    switch (errorType) {
      case ErrorType.MESSAGE_TYPE_UNKNOWN_VALUE:
        error = TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_MESSAGE_TYPE_UNKNOWN;
        break;
      case ErrorType.DEVICE_NOT_SECURED_VALUE:
        error = TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_DEVICE_NOT_SECURED;
        break;
      default:
        loge(TAG, "Encountered unexpected error type: " + errorType + ".");
        error = TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_UNKNOWN;
    }
    notifyEnrollmentError(error);
  }

  private void notifyEnrollmentError(@TrustedDeviceConstants.TrustedDeviceError int error) {
    notifyRemoteCallbackList(
        remoteEnrollmentCallbacks,
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
    databaseExecutor.execute(() -> {
      String deviceId = device.getDeviceId();
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
    TrustedDeviceState state = TrustedDeviceState.newBuilder()
        .setEnabled(false)
        .build();

    return TrustedDeviceMessage.newBuilder()
        .setVersion(TrustedDeviceManager.TRUSTED_DEVICE_MESSAGE_VERSION)
        .setType(MessageType.STATE_SYNC)
        .setPayload(state.toByteString())
        .build()
        .toByteArray();
  }

  @VisibleForTesting
  final TrustedDeviceFeature.Callback featureCallback =
      new TrustedDeviceFeature.Callback() {
        @Override
        public void onMessageReceived(ConnectedDevice device, byte[] message) {
          TrustedDeviceMessage trustedDeviceMessage = null;
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
        public void onDeviceError(ConnectedDevice device, int error) {}
      };

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
          if (devices == null || devices.isEmpty()) {
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
}
