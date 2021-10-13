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

package com.google.android.connecteddevice;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.connection.AssociationCallback;
import com.google.android.connecteddevice.connection.CarBluetoothManager;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.DeviceMessage;
import com.google.android.connecteddevice.model.Errors;
import com.google.android.connecteddevice.model.StartAssociationResponse;
import com.google.android.connecteddevice.oob.OobChannel;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage.AssociatedDeviceCallback;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage.OnAssociatedDevicesRetrievedListener;
import com.google.android.connecteddevice.util.ByteUtils;
import com.google.android.connecteddevice.util.EventLog;
import com.google.android.connecteddevice.util.SafeConsumer;
import com.google.android.connecteddevice.util.ThreadSafeCallbacks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Manager of devices connected to the car. */
@SuppressWarnings("AndroidConcurrentHashMap") // Targeting sdk version 29
public class ConnectedDeviceManager {

  private static final String TAG = "ConnectedDeviceManager";

  // Device name length is limited by available bytes in BLE advertisement data packet.
  //
  // BLE advertisement limits data packet length to 31
  // Currently we send:
  // - 18 bytes for 16 chars UUID: 16 bytes + 2 bytes for header;
  // - 3 bytes for advertisement being connectable;
  // which leaves 10 bytes.
  // Subtracting 2 bytes used by header, we have 8 bytes for device name.
  // The device name length defined here should be smaller than the limit 8.
  private static final int DEVICE_NAME_LENGTH = 2;

  private final ConnectedDeviceStorage storage;

  private final CarBluetoothManager carBluetoothManager;

  /**
   * The {@link Executor} that will handle tasks linked with connecting to a remote device; this
   * includes advertising for association and reconnection with an associated device.
   */
  private final Executor connectionExecutor;

  private final Executor storageExecutor;

  private final ThreadSafeCallbacks<DeviceAssociationCallback> deviceAssociationCallbacks =
      new ThreadSafeCallbacks<>();

  private final ThreadSafeCallbacks<ConnectionCallback> activeUserConnectionCallbacks =
      new ThreadSafeCallbacks<>();

  private final ThreadSafeCallbacks<ConnectionCallback> allUserConnectionCallbacks =
      new ThreadSafeCallbacks<>();

  // deviceId -> (recipientId -> callbacks)
  private final Map<String, Map<UUID, ThreadSafeCallbacks<DeviceCallback>>> deviceCallbacks =
      new ConcurrentHashMap<>();

  // deviceId -> device
  private final Map<String, ConnectedDevice> connectedDevices = new ConcurrentHashMap<>();

  // recipientId -> (deviceId -> message bytes)
  private final Map<UUID, Map<String, List<DeviceMessage>>> recipientMissedMessages =
      new ConcurrentHashMap<>();

  // Recipient ids that received multiple callback registrations indicate that the recipient id
  // has been compromised. Another party now has access the messages intended for that recipient.
  // As a safeguard, that recipient id will be added to this list and blocked from further
  // callback notifications.
  private final Set<UUID> blockedRecipients = new CopyOnWriteArraySet<>();

  private final AtomicBoolean isConnectingToUserDevice = new AtomicBoolean(false);

  private final AtomicBoolean hasStarted = new AtomicBoolean(false);

  private byte[] nameForAssociation;

  private AssociationCallback associationCallback;

  private MessageDeliveryDelegate messageDeliveryDelegate;

  private OobChannel oobChannel;

  public ConnectedDeviceManager(
      @NonNull CarBluetoothManager carBluetoothManager, @NonNull ConnectedDeviceStorage storage) {
    this(
        carBluetoothManager,
        storage,
        Executors.newCachedThreadPool(),
        Executors.newSingleThreadExecutor(),
        Executors.newSingleThreadExecutor());
  }

  @VisibleForTesting
  ConnectedDeviceManager(
      @NonNull CarBluetoothManager carBluetoothManager,
      @NonNull ConnectedDeviceStorage storage,
      @NonNull Executor connectionExecutor,
      @NonNull Executor callbackExecutor,
      @NonNull Executor storageExecutor) {
    this.storage = storage;
    this.carBluetoothManager = carBluetoothManager;
    this.connectionExecutor = connectionExecutor;
    this.carBluetoothManager.registerCallback(generateCarManagerCallback(), callbackExecutor);
    this.storage.setAssociatedDeviceCallback(associatedDeviceCallback);
    this.storageExecutor = storageExecutor;
  }

  /**
   * Start internal processes and begin discovering devices. Must be called before any connections
   * can be made using {@link #connectToActiveUserDevice()}.
   */
  public void start() {
    if (hasStarted.getAndSet(true)) {
      reset();
    } else {
      logd(TAG, "Starting ConnectedDeviceManager.");
      EventLog.onConnectedDeviceManagerStarted();
    }
    carBluetoothManager.start();
    connectToActiveUserDevice();
  }

  /** Reset internal processes and disconnect any active connections. */
  public void reset() {
    logd(TAG, "Resetting ConnectedDeviceManager.");
    for (ConnectedDevice device : connectedDevices.values()) {
      removeConnectedDevice(device.getDeviceId());
    }
    carBluetoothManager.stop();
    isConnectingToUserDevice.set(false);
    if (oobChannel != null) {
      oobChannel.interrupt();
      oobChannel = null;
    }
    associationCallback = null;
  }

  /** Returns {@link List<ConnectedDevice>} of devices currently connected. */
  @NonNull
  public List<ConnectedDevice> getActiveUserConnectedDevices() {
    List<ConnectedDevice> activeUserConnectedDevices = new ArrayList<>();
    for (ConnectedDevice device : connectedDevices.values()) {
      if (device.isAssociatedWithDriver()) {
        activeUserConnectedDevices.add(device);
      }
    }
    logd(TAG, "Returned " + activeUserConnectedDevices.size() + " active user devices.");
    return activeUserConnectedDevices;
  }

  /**
   * Register a callback for triggered associated device related events.
   *
   * @param callback {@link DeviceAssociationCallback} to register.
   * @param executor {@link Executor} to execute triggers on.
   */
  public void registerDeviceAssociationCallback(
      @NonNull DeviceAssociationCallback callback, @NonNull Executor executor) {
    deviceAssociationCallbacks.add(callback, executor);
  }

  /**
   * Unregister a device association callback.
   *
   * @param callback {@link DeviceAssociationCallback} to unregister.
   */
  public void unregisterDeviceAssociationCallback(@NonNull DeviceAssociationCallback callback) {
    deviceAssociationCallbacks.remove(callback);
  }

  /**
   * Register a callback for manager triggered connection events for only the currently active
   * user's devices.
   *
   * @param callback {@link ConnectionCallback} to register.
   * @param executor {@link Executor} to execute triggers on.
   */
  public void registerActiveUserConnectionCallback(
      @NonNull ConnectionCallback callback, @NonNull Executor executor) {
    activeUserConnectionCallbacks.add(callback, executor);
    logd(TAG, "ActiveUserConnectionCallback registered.");
  }

  /**
   * Unregister a connection callback from manager.
   *
   * @param callback {@link ConnectionCallback} to unregister.
   */
  public void unregisterConnectionCallback(ConnectionCallback callback) {
    activeUserConnectionCallbacks.remove(callback);
    allUserConnectionCallbacks.remove(callback);
  }

  /** Connect to a device for the active user if available. */
  @VisibleForTesting
  void connectToActiveUserDevice() {
    connectionExecutor.execute(
        () -> {
          logd(TAG, "Received request to connect to active user's device.");
          connectToActiveUserDeviceInternal();
        });
  }

  private void connectToActiveUserDeviceInternal() {
    boolean isLockAcquired = isConnectingToUserDevice.compareAndSet(false, true);
    if (!isLockAcquired) {
      logd(
          TAG,
          "A request has already been made to connect to this user's device. "
              + "Ignoring redundant request.");
      return;
    }
    List<AssociatedDevice> userDevices = storage.getDriverAssociatedDevices();
    if (userDevices.isEmpty()) {
      logw(TAG, "No devices associated with active user. Ignoring.");
      isConnectingToUserDevice.set(false);
      return;
    }

    // Only currently support one device per user for fast association, so take the
    // first one.
    AssociatedDevice userDevice = userDevices.get(0);
    if (!userDevice.isConnectionEnabled()) {
      logd(TAG, "Connection is disabled on device " + userDevice + ".");
      isConnectingToUserDevice.set(false);
      return;
    }
    if (connectedDevices.containsKey(userDevice.getDeviceId())) {
      logd(TAG, "Device has already been connected. No need to attempt connection " + "again.");
      isConnectingToUserDevice.set(false);
      return;
    }
    EventLog.onStartDeviceSearchStarted();
    carBluetoothManager.connectToDevice(UUID.fromString(userDevice.getDeviceId()));
  }

  /**
   * Start the association with a new device.
   *
   * @param callback Callback for association events.
   */
  public void startAssociation(@NonNull AssociationCallback callback) {
    associationCallback = callback;
    connectionExecutor.execute(
        () -> {
          logd(TAG, "Received request to start association.");
          carBluetoothManager.startAssociation(
              getNameForAssociation(), internalAssociationCallback);
        });
  }

  /** Stop the association with any device. */
  public void stopAssociation(@NonNull AssociationCallback callback) {
    if (associationCallback != callback) {
      logd(TAG, "Stop association called with unrecognized callback. Ignoring.");
      return;
    }
    logd(TAG, "Stopping association.");
    associationCallback = null;
    carBluetoothManager.stopAssociation();
    if (oobChannel != null) {
      oobChannel.interrupt();
    }
    oobChannel = null;
  }

  /**
   * Retrieves devices associated with the active user and notifies the given {@link
   * OnAssociatedDevicesRetrievedListener}.
   */
  public void retrieveActiveUserAssociatedDevices(OnAssociatedDevicesRetrievedListener listener) {
    storageExecutor.execute(
        () -> listener.onAssociatedDevicesRetrieved(storage.getDriverAssociatedDevices()));
  }

  /** Notify that the user has accepted a pairing code or any out-of-band confirmation. */
  public void notifyOutOfBandAccepted() {
    carBluetoothManager.notifyOutOfBandAccepted();
  }

  /**
   * Remove the associated device with the given device identifier for the current user.
   *
   * @param deviceId Device identifier.
   */
  public void removeActiveUserAssociatedDevice(@NonNull String deviceId) {
    storageExecutor.execute(() -> storage.removeAssociatedDevice(deviceId));
    disconnectDevice(deviceId);
  }

  /**
   * Enable connection on an associated device.
   *
   * @param deviceId Device identifier.
   */
  public void enableAssociatedDeviceConnection(@NonNull String deviceId) {
    logd(TAG, "enableAssociatedDeviceConnection() called on " + deviceId);
    storageExecutor.execute(
        () -> {
          storage.updateAssociatedDeviceConnectionEnabled(
              deviceId, /* isConnectionEnabled= */ true);
          connectToActiveUserDevice();
        });
  }

  /**
   * Disable connection on an associated device.
   *
   * @param deviceId Device identifier.
   */
  public void disableAssociatedDeviceConnection(@NonNull String deviceId) {
    logd(TAG, "disableAssociatedDeviceConnection() called on " + deviceId);
    storageExecutor.execute(
        () -> {
          storage.updateAssociatedDeviceConnectionEnabled(
              deviceId, /* isConnectionEnabled= */ false);
          disconnectDevice(deviceId);
          isConnectingToUserDevice.set(false);
        });
  }

  private void disconnectDevice(String deviceId) {
    ConnectedDevice device = connectedDevices.get(deviceId);
    if (device != null) {
      carBluetoothManager.disconnectDevice(deviceId);
      removeConnectedDevice(deviceId);
    }
  }

  /**
   * Register a callback for a specific device and recipient.
   *
   * @param device {@link ConnectedDevice} to register triggers on.
   * @param recipientId {@link UUID} to register as recipient of.
   * @param callback {@link DeviceCallback} to register.
   * @param executor {@link Executor} on which to execute callback.
   */
  public void registerDeviceCallback(
      @NonNull ConnectedDevice device,
      @NonNull UUID recipientId,
      @NonNull DeviceCallback callback,
      @NonNull Executor executor) {
    if (isRecipientDenyListed(recipientId)) {
      notifyOfBlocking(device, recipientId, callback, executor);
      return;
    }
    logd(
        TAG,
        "New callback registered on device "
            + device.getDeviceId()
            + " for recipient "
            + recipientId);
    String deviceId = device.getDeviceId();
    Map<UUID, ThreadSafeCallbacks<DeviceCallback>> recipientCallbacks =
        deviceCallbacks.computeIfAbsent(deviceId, key -> new ConcurrentHashMap<>());

    // Device already has a callback registered with this recipient UUID. For the
    // protection of the user, this UUID is now deny listed from future subscriptions
    // and the original subscription is notified and removed.
    if (recipientCallbacks.containsKey(recipientId)) {
      denyListRecipient(deviceId, recipientId);
      notifyOfBlocking(device, recipientId, callback, executor);
      return;
    }

    ThreadSafeCallbacks<DeviceCallback> newCallbacks = new ThreadSafeCallbacks<>();
    newCallbacks.add(callback, executor);
    recipientCallbacks.put(recipientId, newCallbacks);

    List<DeviceMessage> messages = popMissedMessages(recipientId, device.getDeviceId());
    if (messages != null) {
      for (DeviceMessage message : messages) {
        newCallbacks.invoke(deviceCallback -> deviceCallback.onMessageReceived(device, message));
      }
    }
  }

  /**
   * Set the delegate for message delivery operations.
   *
   * @param delegate The {@link MessageDeliveryDelegate} to set. {@code null} to unset.
   */
  public void setMessageDeliveryDelegate(@Nullable MessageDeliveryDelegate delegate) {
    messageDeliveryDelegate = delegate;
  }

  private static void notifyOfBlocking(
      @NonNull ConnectedDevice device,
      @NonNull UUID recipientId,
      @NonNull DeviceCallback callback,
      @NonNull Executor executor) {
    loge(
        TAG,
        "Multiple callbacks registered for recipient "
            + recipientId
            + "! Your recipient id is no longer secure and has been blocked from future use.");
    executor.execute(
        () -> callback.onDeviceError(device, Errors.DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED));
  }

  private void saveMissedMessage(@NonNull String deviceId, @NonNull DeviceMessage message) {
    // Store last message in case recipient registers callbacks in the future.
    UUID recipientId = message.getRecipient();
    logd(
        TAG,
        "No recipient registered for device "
            + deviceId
            + " and recipient "
            + recipientId
            + " combination. Saving message.");
    recipientMissedMessages
        .computeIfAbsent(recipientId, __ -> new HashMap<>())
        .computeIfAbsent(deviceId, __ -> new ArrayList<>())
        .add(message);
  }

  /**
   * Remove all messages sent for this device prior to a {@link DeviceCallback} being registered.
   *
   * @param recipientId Recipient's id
   * @param deviceId Device id
   * @return The missed {@code DeviceMessage}s, or {@code null} if no messages were missed.
   */
  @Nullable
  private List<DeviceMessage> popMissedMessages(
      @NonNull UUID recipientId,
      @NonNull String deviceId) {
    Map<String, List<DeviceMessage>> missedMessages = recipientMissedMessages.get(recipientId);
    if (missedMessages == null) {
      return null;
    }

    return missedMessages.remove(deviceId);
  }

  /**
   * Unregister callback from device events.
   *
   * @param device {@link ConnectedDevice} callback was registered on.
   * @param recipientId {@link UUID} callback was registered under.
   * @param callback {@link DeviceCallback} to unregister.
   */
  public void unregisterDeviceCallback(
      @NonNull ConnectedDevice device,
      @NonNull UUID recipientId,
      @NonNull DeviceCallback callback) {
    logd(
        TAG,
        "Device callback unregistered on device "
            + device.getDeviceId()
            + " for "
            + "recipient "
            + recipientId
            + ".");

    Map<UUID, ThreadSafeCallbacks<DeviceCallback>> recipientCallbacks =
        deviceCallbacks.get(device.getDeviceId());
    if (recipientCallbacks == null) {
      return;
    }
    ThreadSafeCallbacks<DeviceCallback> callbacks = recipientCallbacks.get(recipientId);
    if (callbacks == null) {
      return;
    }

    callbacks.remove(callback);
    if (callbacks.size() == 0) {
      recipientCallbacks.remove(recipientId);
    }
  }

  /**
   * Securely send message to a device.
   *
   * @param device {@link ConnectedDevice} to send the message to.
   * @param deviceMessage Message to send to device.
   * @throws IllegalStateException Secure channel has not been established.
   */
  public void sendMessage(@NonNull ConnectedDevice device, @NonNull DeviceMessage deviceMessage) {
    String deviceId = device.getDeviceId();
    boolean isEncrypted = deviceMessage.isMessageEncrypted();
    logd(
        TAG,
        "Sending new message to device "
            + deviceId
            + " for "
            + deviceMessage.getRecipient()
            + " containing "
            + deviceMessage.getMessage().length
            + ". Message will be sent securely: "
            + isEncrypted
            + ".");

    ConnectedDevice connectedDevice = connectedDevices.get(deviceId);
    if (connectedDevice == null) {
      loge(TAG, "Attempted to send message to unknown device " + deviceId + ". Ignoring.");
      return;
    }

    if (isEncrypted && !connectedDevice.hasSecureChannel()) {
      throw new IllegalStateException(
          "Cannot send a message securely to device that has not "
              + "established a secure channel.");
    }

    carBluetoothManager.sendMessage(deviceId, deviceMessage);
  }

  private boolean isRecipientDenyListed(UUID recipientId) {
    return blockedRecipients.contains(recipientId);
  }

  private void denyListRecipient(@NonNull String deviceId, @NonNull UUID recipientId) {
    Map<UUID, ThreadSafeCallbacks<DeviceCallback>> recipientCallbacks =
        deviceCallbacks.get(deviceId);
    if (recipientCallbacks == null) {
      // Should never happen, but null-safety check.
      return;
    }

    ThreadSafeCallbacks<DeviceCallback> existingCallback = recipientCallbacks.get(recipientId);
    if (existingCallback == null) {
      // Should never happen, but null-safety check.
      return;
    }

    ConnectedDevice connectedDevice = connectedDevices.get(deviceId);
    if (connectedDevice != null) {
      recipientCallbacks
          .get(recipientId)
          .invoke(
              callback ->
                  callback.onDeviceError(
                      connectedDevice, Errors.DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED));
    }

    recipientCallbacks.remove(recipientId);
    blockedRecipients.add(recipientId);
  }

  @VisibleForTesting
  void addConnectedDevice(@NonNull String deviceId) {
    if (connectedDevices.containsKey(deviceId)) {
      // Device already connected. No-op until secure channel established.
      return;
    }
    logd(TAG, "New device with id " + deviceId + " connected.");
    ConnectedDevice connectedDevice =
        new ConnectedDevice(
            deviceId,
            /* deviceName= */ null,
            storage.getDriverAssociatedDeviceIds().contains(deviceId),
            /* hasSecureChannel= */ false);

    connectedDevices.put(deviceId, connectedDevice);
    invokeConnectionCallbacks(
        connectedDevice.isAssociatedWithDriver(),
        callback -> callback.onDeviceConnected(connectedDevice));
  }

  @VisibleForTesting
  void removeConnectedDevice(@NonNull String deviceId) {
    logd(TAG, "Device " + deviceId + " disconnected.");
    ConnectedDevice connectedDevice = connectedDevices.get(deviceId);
    isConnectingToUserDevice.set(false);
    boolean isAssociated = false;
    if (connectedDevice != null) {
      connectedDevices.remove(deviceId);
      isAssociated = connectedDevice.isAssociatedWithDriver();
      invokeConnectionCallbacks(
          isAssociated, callback -> callback.onDeviceDisconnected(connectedDevice));
    }

    if (isAssociated || connectedDevices.isEmpty()) {
      // Try to regain connection to active user's device.
      connectToActiveUserDevice();
    }
  }

  @VisibleForTesting
  void onSecureChannelEstablished(@NonNull String deviceId) {
    if (connectedDevices.get(deviceId) == null) {
      loge(TAG, "Secure channel established on unknown device " + deviceId + ".");
      return;
    }
    ConnectedDevice connectedDevice = connectedDevices.get(deviceId);
    ConnectedDevice updatedConnectedDevice =
        new ConnectedDevice(
            connectedDevice.getDeviceId(),
            getConnectedDeviceName(deviceId),
            connectedDevice.isAssociatedWithDriver(),
            /* hasSecureChannel= */ true);

    boolean notifyCallbacks = connectedDevices.get(deviceId) != null;

    // TODO (b/143088482) Implement interrupt
    // Ignore if central already holds the active device connection and interrupt the
    // connection.

    connectedDevices.put(deviceId, updatedConnectedDevice);
    logd(
        TAG,
        "Secure channel established to "
            + deviceId
            + " . Notifying callbacks: "
            + notifyCallbacks
            + ".");
    if (notifyCallbacks) {
      notifyAllDeviceCallbacks(
          deviceId, callback -> callback.onSecureChannelEstablished(updatedConnectedDevice));
    }
  }

  @VisibleForTesting
  void onMessageReceived(@NonNull String deviceId, @NonNull DeviceMessage message) {
    logd(
        TAG,
        "New message received from device "
            + deviceId
            + " intended for "
            + message.getRecipient()
            + " containing "
            + message.getMessage().length
            + " bytes.");

    ConnectedDevice connectedDevice = connectedDevices.get(deviceId);
    if (connectedDevice == null) {
      logw(
          TAG,
          "Received message from unknown device "
              + deviceId
              + "or to unknown "
              + "recipient "
              + message.getRecipient()
              + ".");
      return;
    }

    if (messageDeliveryDelegate != null
        && !messageDeliveryDelegate.shouldDeliverMessageForDevice(connectedDevice)) {
      logw(
          TAG,
          "The message delegate has rejected this message. It will not be "
              + "delivered to the intended recipient.");
      return;
    }

    UUID recipientId = message.getRecipient();
    Map<UUID, ThreadSafeCallbacks<DeviceCallback>> deviceCallbacks =
        this.deviceCallbacks.get(deviceId);
    if (deviceCallbacks == null) {
      saveMissedMessage(deviceId, message);
      return;
    }
    ThreadSafeCallbacks<DeviceCallback> recipientCallbacks = deviceCallbacks.get(recipientId);
    if (recipientCallbacks == null) {
      saveMissedMessage(deviceId, message);
      return;
    }

    recipientCallbacks.invoke(
        callback -> callback.onMessageReceived(connectedDevice, message));
  }

  @VisibleForTesting
  void deviceErrorOccurred(@NonNull String deviceId) {
    ConnectedDevice connectedDevice = connectedDevices.get(deviceId);
    if (connectedDevice == null) {
      logw(TAG, "Failed to establish secure channel on unknown device " + deviceId + ".");
      return;
    }

    notifyAllDeviceCallbacks(
        deviceId,
        callback ->
            callback.onDeviceError(connectedDevice, Errors.DEVICE_ERROR_INVALID_SECURITY_KEY));
  }

  @VisibleForTesting
  void onAssociationCompleted(@NonNull String deviceId) {
    ConnectedDevice connectedDevice = connectedDevices.get(deviceId);
    if (connectedDevice == null) {
      return;
    }

    // The previous device is now obsolete and should be replaced with a new one properly
    // reflecting the state of belonging to the active user and notify features.
    if (connectedDevice.isAssociatedWithDriver()) {
      // Device was already marked as belonging to active user. No need to reissue callbacks.
      return;
    }
    removeConnectedDevice(deviceId);
    addConnectedDevice(deviceId);
  }

  @Nullable
  private String getConnectedDeviceName(@NonNull String deviceId) {
    ConnectedDevice device = connectedDevices.get(deviceId);
    if (device == null) {
      return null;
    }
    String deviceName = device.getDeviceName();
    if (deviceName != null) {
      return deviceName;
    }
    AssociatedDevice associatedDevice = storage.getAssociatedDevice(deviceId);
    if (associatedDevice == null) {
      return null;
    }
    return associatedDevice.getDeviceName();
  }

  private void invokeConnectionCallbacks(
      boolean belongsToActiveUser, @NonNull SafeConsumer<ConnectionCallback> notification) {
    logd(
        TAG,
        "Notifying connection callbacks for device belonging to active user "
            + belongsToActiveUser
            + ".");
    if (belongsToActiveUser) {
      activeUserConnectionCallbacks.invoke(notification);
    }
    allUserConnectionCallbacks.invoke(notification);
  }

  private void notifyAllDeviceCallbacks(
      @NonNull String deviceId, @NonNull SafeConsumer<DeviceCallback> notification) {
    logd(TAG, "Notifying all device callbacks for device " + deviceId + ".");
    Map<UUID, ThreadSafeCallbacks<DeviceCallback>> deviceCallbacks =
        this.deviceCallbacks.get(deviceId);
    if (deviceCallbacks == null) {
      return;
    }

    for (ThreadSafeCallbacks<DeviceCallback> callbacks : deviceCallbacks.values()) {
      callbacks.invoke(notification);
    }
  }

  /**
   * Returns the name that should be used for the device during association.
   *
   * <p>The returned name will be a randomized byte array.
   */
  @NonNull
  private byte[] getNameForAssociation() {
    if (nameForAssociation == null) {
      nameForAssociation = ByteUtils.randomBytes(DEVICE_NAME_LENGTH);
    }
    return nameForAssociation;
  }

  @NonNull
  private CarBluetoothManager.Callback generateCarManagerCallback() {
    return new CarBluetoothManager.Callback() {
      @Override
      public void onDeviceConnected(String deviceId) {
        addConnectedDevice(deviceId);
      }

      @Override
      public void onDeviceDisconnected(String deviceId) {
        removeConnectedDevice(deviceId);
      }

      @Override
      public void onSecureChannelEstablished(String deviceId) {
        EventLog.onSecureChannelEstablished();
        ConnectedDeviceManager.this.onSecureChannelEstablished(deviceId);
      }

      @Override
      public void onMessageReceived(String deviceId, DeviceMessage message) {
        ConnectedDeviceManager.this.onMessageReceived(deviceId, message);
      }

      @Override
      public void onSecureChannelError(String deviceId) {
        deviceErrorOccurred(deviceId);
      }
    };
  }

  private final AssociationCallback internalAssociationCallback =
      new AssociationCallback() {
        @Override
        public void onAssociationStartSuccess(StartAssociationResponse response) {
          if (associationCallback != null) {
            associationCallback.onAssociationStartSuccess(response);
          }
        }

        @Override
        public void onAssociationStartFailure() {
          if (associationCallback != null) {
            associationCallback.onAssociationStartFailure();
          }
        }

        @Override
        public void onAssociationError(int error) {
          if (associationCallback != null) {
            associationCallback.onAssociationError(error);
          }
        }

        @Override
        public void onVerificationCodeAvailable(String code) {
          if (associationCallback != null) {
            associationCallback.onVerificationCodeAvailable(code);
          }
        }

        @Override
        public void onAssociationCompleted(String deviceId) {
          if (associationCallback != null) {
            associationCallback.onAssociationCompleted(deviceId);
          }
          ConnectedDeviceManager.this.onAssociationCompleted(deviceId);
        }
      };

  private final AssociatedDeviceCallback associatedDeviceCallback =
      new AssociatedDeviceCallback() {
        @Override
        public void onAssociatedDeviceAdded(AssociatedDevice device) {
          deviceAssociationCallbacks.invoke(callback -> callback.onAssociatedDeviceAdded(device));
        }

        @Override
        public void onAssociatedDeviceRemoved(AssociatedDevice device) {
          deviceAssociationCallbacks.invoke(callback -> callback.onAssociatedDeviceRemoved(device));
          logd(TAG, "Successfully removed associated device " + device + ".");
        }

        @Override
        public void onAssociatedDeviceUpdated(AssociatedDevice device) {
          deviceAssociationCallbacks.invoke(callback -> callback.onAssociatedDeviceUpdated(device));
        }
      };

  /** Callback for triggered connection events from {@link ConnectedDeviceManager}. */
  public interface ConnectionCallback {
    /** Triggered when a new device has connected. */
    void onDeviceConnected(@NonNull ConnectedDevice device);

    /** Triggered when a device has disconnected. */
    void onDeviceDisconnected(@NonNull ConnectedDevice device);
  }

  /** Triggered device events for a connected device from {@link ConnectedDeviceManager}. */
  public interface DeviceCallback {
    /**
     * Triggered when secure channel has been established on a device. Encrypted messaging now
     * available.
     */
    void onSecureChannelEstablished(@NonNull ConnectedDevice device);

    /** Triggered when a new message is received from a device. */
    void onMessageReceived(@NonNull ConnectedDevice device, @NonNull DeviceMessage message);

    /** Triggered when an error has occurred for a device. */
    void onDeviceError(@NonNull ConnectedDevice device, @Errors.DeviceError int error);
  }

  /** Callback for association device related events. */
  public interface DeviceAssociationCallback {

    /** Triggered when an associated device has been added. */
    void onAssociatedDeviceAdded(@NonNull AssociatedDevice device);

    /** Triggered when an associated device has been removed. */
    void onAssociatedDeviceRemoved(@NonNull AssociatedDevice device);

    /** Triggered when the name of an associated device has been updated. */
    void onAssociatedDeviceUpdated(@NonNull AssociatedDevice device);
  }

  /** Delegate for message delivery operations. */
  public interface MessageDeliveryDelegate {

    /** Indicate whether a message should be delivered for the specified device. */
    boolean shouldDeliverMessageForDevice(@NonNull ConnectedDevice device);
  }
}
