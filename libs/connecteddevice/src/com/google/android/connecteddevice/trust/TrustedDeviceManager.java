package com.google.android.connecteddevice.trust;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.app.ActivityManager;
import androidx.room.Room;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.api.IConnectedDeviceManager;
import com.google.android.connecteddevice.api.IDeviceAssociationCallback;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationRequestListener;
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
import com.google.android.connecteddevice.trust.storage.TrustedDeviceDao;
import com.google.android.connecteddevice.trust.storage.TrustedDeviceDatabase;
import com.google.android.connecteddevice.trust.storage.TrustedDeviceEntity;
import com.google.android.connecteddevice.util.ByteUtils;
import com.google.android.connecteddevice.util.RemoteCallbackBinder;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Manager for the feature of unlocking the head unit with a user's trusted device. */
public class TrustedDeviceManager extends ITrustedDeviceManager.Stub {
  private static final String TAG = "TrustedDeviceManager";

  private static final int TRUSTED_DEVICE_MESSAGE_VERSION = 2;

  /** Length of token generated on a trusted device. */
  private static final int ESCROW_TOKEN_LENGTH = 8;

  private final Map<IBinder, ITrustedDeviceCallback> trustedDeviceCallbacks =
      new ConcurrentHashMap<>();

  private final Map<IBinder, ITrustedDeviceEnrollmentCallback> enrollmentCallbacks =
      new ConcurrentHashMap<>();

  private final Map<IBinder, IOnTrustedDeviceEnrollmentNotificationRequestListener>
      enrollmentNotificationRequestListeners = new ConcurrentHashMap<>();

  private final Map<IBinder, IDeviceAssociationCallback> associatedDeviceCallbacks =
      new ConcurrentHashMap<>();

  private final Set<RemoteCallbackBinder> callbackBinders = new CopyOnWriteArraySet<>();

  private final TrustedDeviceFeature trustedDeviceFeature;

  private final Executor executor = Executors.newSingleThreadExecutor();

  private final AtomicBoolean isWaitingForCredentials = new AtomicBoolean(false);

  private final AtomicReference<String> pendingUnlockDeviceId = new AtomicReference<>(null);

  private final TrustedDeviceDao database;

  private ITrustedDeviceAgentDelegate trustAgentDelegate;

  private ConnectedDevice pendingDevice;

  private byte[] pendingToken;

  private PendingCredentials pendingCredentials;

  TrustedDeviceManager(@NonNull Context context) {
    trustedDeviceFeature = new TrustedDeviceFeature(context);
    trustedDeviceFeature.setCallback(featureCallback);
    trustedDeviceFeature.setAssociatedDeviceCallback(associatedDeviceCallback);
    trustedDeviceFeature.start();
    database =
        Room.databaseBuilder(
                context.createDeviceProtectedStorageContext(),
                TrustedDeviceDatabase.class,
                TrustedDeviceDatabase.DATABASE_NAME)
            .build()
            .trustedDeviceDao();
    logd(TAG, "TrustedDeviceManager created successfully.");
  }

  void cleanup() {
    pendingToken = null;
    pendingDevice = null;
    pendingCredentials = null;
    isWaitingForCredentials.set(false);
    pendingUnlockDeviceId.set(null);
    trustedDeviceCallbacks.clear();
    enrollmentCallbacks.clear();
    associatedDeviceCallbacks.clear();
    trustedDeviceFeature.stop();
  }

  private void startEnrollment(@NonNull ConnectedDevice device, @NonNull byte[] token) {
    logd(TAG, "Starting trusted device enrollment process.");
    pendingDevice = device;

    notifyEnrollmentNotificationRequestListeners();

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

    if (enrollmentCallbacks.isEmpty()) {
      isWaitingForCredentials.set(true);
      return;
    }

    isWaitingForCredentials.set(false);
    notifyEnrollmentCallbacks(
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

    TrustedDeviceEntity entity = new TrustedDeviceEntity();
    entity.id = deviceId;
    entity.userId = userId;
    entity.handle = handle;

    Executors.newSingleThreadExecutor().execute(() -> database.addOrReplaceTrustedDevice(entity));

    pendingDevice = null;

    TrustedDevice trustedDevice = new TrustedDevice(deviceId, userId, handle);
    notifyTrustedDeviceCallbacks(
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
  public List<TrustedDevice> getTrustedDevicesForActiveUser() {
    List<TrustedDeviceEntity> foundEntities =
        database.getTrustedDevicesForUser(ActivityManager.getCurrentUser());

    List<TrustedDevice> trustedDevices = new ArrayList<>();
    if (foundEntities == null) {
      return trustedDevices;
    }

    for (TrustedDeviceEntity entity : foundEntities) {
      trustedDevices.add(entity.toTrustedDevice());
    }

    return trustedDevices;
  }

  @Override
  public void removeTrustedDevice(TrustedDevice trustedDevice) {
    if (trustAgentDelegate == null) {
      loge(TAG, "No TrustAgent delegate has been set. Unable to remove trusted device.");
      return;
    }

    try {
      trustAgentDelegate.removeEscrowToken(trustedDevice.getHandle(), trustedDevice.getUserId());
      database.removeTrustedDevice(new TrustedDeviceEntity(trustedDevice));
    } catch (RemoteException e) {
      loge(TAG, "Error while removing token through delegate.", e);
      return;
    }
    notifyTrustedDeviceCallbacks(
        callback -> {
          try {
            callback.onTrustedDeviceRemoved(trustedDevice);
          } catch (RemoteException e) {
            loge(TAG, "Failed to notify that a trusted device has been removed.", e);
          }
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
    trustedDeviceCallbacks.put(callback.asBinder(), callback);
    RemoteCallbackBinder remoteBinder =
        new RemoteCallbackBinder(
            callback.asBinder(), iBinder -> unregisterTrustedDeviceCallback(callback));
    callbackBinders.add(remoteBinder);
  }

  @Override
  public void unregisterTrustedDeviceCallback(ITrustedDeviceCallback callback) {
    IBinder binder = callback.asBinder();
    trustedDeviceCallbacks.remove(binder);
    removeRemoteBinder(binder);
  }

  @Override
  public void registerAssociatedDeviceCallback(IDeviceAssociationCallback callback) {
    associatedDeviceCallbacks.put(callback.asBinder(), callback);
    RemoteCallbackBinder remoteBinder =
        new RemoteCallbackBinder(
            callback.asBinder(), iBinder -> unregisterAssociatedDeviceCallback(callback));
    callbackBinders.add(remoteBinder);
  }

  @Override
  public void unregisterAssociatedDeviceCallback(IDeviceAssociationCallback callback) {
    IBinder binder = callback.asBinder();
    associatedDeviceCallbacks.remove(binder);
    removeRemoteBinder(binder);
  }

  @Override
  public void registerTrustedDeviceEnrollmentCallback(ITrustedDeviceEnrollmentCallback callback) {
    enrollmentCallbacks.put(callback.asBinder(), callback);
    RemoteCallbackBinder remoteBinder =
        new RemoteCallbackBinder(
            callback.asBinder(), iBinder -> unregisterTrustedDeviceEnrollmentCallback(callback));
    callbackBinders.add(remoteBinder);
    // A token has been added and is waiting on user credential validation.
    if (isWaitingForCredentials.getAndSet(false)) {
      executor.execute(
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
    IBinder binder = callback.asBinder();
    enrollmentCallbacks.remove(binder);
    removeRemoteBinder(binder);
  }

  @Override
  public void registerTrustedDeviceEnrollmentNotificationRequestListener(
      IOnTrustedDeviceEnrollmentNotificationRequestListener listener) {
    logd(TAG, "Registering listener.");
    enrollmentNotificationRequestListeners.put(listener.asBinder(), listener);
    RemoteCallbackBinder remoteBinder =
        new RemoteCallbackBinder(
            listener.asBinder(),
            iBinder -> unregisterTrustedDeviceEnrollmentNotificationRequestListener(listener));
    callbackBinders.add(remoteBinder);
  }

  @Override
  public void unregisterTrustedDeviceEnrollmentNotificationRequestListener(
      IOnTrustedDeviceEnrollmentNotificationRequestListener listener) {
    IBinder binder = listener.asBinder();
    enrollmentNotificationRequestListeners.remove(binder);
    removeRemoteBinder(binder);
  }

  @Override
  public void setTrustedDeviceAgentDelegate(ITrustedDeviceAgentDelegate trustAgentDelegate) {
    logd(TAG, "Set trusted device agent delegate: " + trustAgentDelegate + ".");
    this.trustAgentDelegate = trustAgentDelegate;

    // Add pending token if present.
    if (pendingToken != null) {
      try {
        trustAgentDelegate.addEscrowToken(pendingToken, ActivityManager.getCurrentUser());
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
  }

  private static boolean areCredentialsValid(@Nullable PhoneCredentials credentials) {
    return credentials != null;
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
      credentials = PhoneCredentials.parseFrom(message,
          ExtensionRegistryLite.getEmptyRegistry());
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

  private void processErrorMessage(@Nullable ByteString payload) {
    if (payload == null) {
      logw(TAG, "Received error message with null payload. Ignoring.");
      return;
    }
    TrustedDeviceError trustedDeviceError = null;
    try {
      trustedDeviceError = TrustedDeviceError.parseFrom(payload,
          ExtensionRegistryLite.getEmptyRegistry());
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
    notifyEnrollmentCallbacks(
        callback -> {
          try {
            callback.onTrustedDeviceEnrollmentError(error);
          } catch (RemoteException e) {
            loge(TAG, "Error while notifying enrollment error.", e);
          }
        });
  }

  private void notifyTrustedDeviceCallbacks(Consumer<ITrustedDeviceCallback> notification) {
    trustedDeviceCallbacks.forEach(
        (iBinder, callback) -> executor.execute(() -> notification.accept(callback)));
  }

  private void notifyEnrollmentCallbacks(Consumer<ITrustedDeviceEnrollmentCallback> notification) {
    enrollmentCallbacks.forEach(
        (iBinder, callback) -> executor.execute(() -> notification.accept(callback)));
  }

  private void notifyAssociatedDeviceCallbacks(Consumer<IDeviceAssociationCallback> notification) {
    associatedDeviceCallbacks.forEach(
        (iBinder, callback) -> executor.execute(() -> notification.accept(callback)));
  }

  private void notifyEnrollmentNotificationRequestListeners() {
    enrollmentNotificationRequestListeners.forEach(
        (iBinder, listener) ->
            executor.execute(
                () -> {
                  try {
                    listener.onTrustedDeviceEnrollmentNotificationRequest();
                  } catch (RemoteException e) {
                    loge(TAG, "Failed to notify the enrollment notification request.");
                  }
                }));
  }

  private void removeRemoteBinder(IBinder binder) {
    RemoteCallbackBinder remoteBinderToRemove = null;
    for (RemoteCallbackBinder remoteBinder : callbackBinders) {
      if (remoteBinder.getCallbackBinder().equals(binder)) {
        remoteBinderToRemove = remoteBinder;
        break;
      }
    }
    if (remoteBinderToRemove != null) {
      remoteBinderToRemove.cleanUp();
      callbackBinders.remove(remoteBinderToRemove);
    }
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

  private final TrustedDeviceFeature.Callback featureCallback =
      new TrustedDeviceFeature.Callback() {
        @Override
        public void onMessageReceived(ConnectedDevice device, byte[] message) {
          TrustedDeviceMessage trustedDeviceMessage = null;
          try {
            trustedDeviceMessage =
                TrustedDeviceMessage.parseFrom(
                    message, ExtensionRegistryLite.getEmptyRegistry());
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
        public void onDeviceError(ConnectedDevice device, int error) {}
      };

  private final TrustedDeviceFeature.AssociatedDeviceCallback associatedDeviceCallback =
      new TrustedDeviceFeature.AssociatedDeviceCallback() {
        @Override
        public void onAssociatedDeviceAdded(AssociatedDevice device) {
          notifyAssociatedDeviceCallbacks(
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
          notifyAssociatedDeviceCallbacks(
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
          notifyAssociatedDeviceCallbacks(
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
