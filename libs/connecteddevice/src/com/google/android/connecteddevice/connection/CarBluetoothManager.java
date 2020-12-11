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

import static com.google.android.connecteddevice.model.Errors.DEVICE_ERROR_INVALID_HANDSHAKE;
import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.oob.OobChannel;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage;
import com.google.android.connecteddevice.util.ThreadSafeCallbacks;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;

/**
 * Generic manager for a car that keeps track of connected devices and their associated callbacks.
 */
public abstract class CarBluetoothManager {

  private static final String TAG = "CarBluetoothManager";

  protected final ConnectedDeviceStorage storage;

  protected final CopyOnWriteArraySet<ConnectedRemoteDevice> connectedDevices =
      new CopyOnWriteArraySet<>();

  protected final ThreadSafeCallbacks<Callback> callbacks = new ThreadSafeCallbacks<>();

  private final boolean isCompressionEnabled;

  private String clientDeviceName;

  private String clientDeviceAddress;

  protected CarBluetoothManager(
      @NonNull ConnectedDeviceStorage connectedDeviceStorage, boolean enableCompression) {
    storage = connectedDeviceStorage;
    isCompressionEnabled = enableCompression;
  }

  /** Attempt to connect to device with provided id. */
  public void connectToDevice(@NonNull UUID deviceId) {
    for (ConnectedRemoteDevice device : connectedDevices) {
      if (UUID.fromString(device.deviceId).equals(deviceId)) {
        logd(TAG, "Already connected to device " + deviceId + ".");
        // Already connected to this device. Ignore requests to connect again.
        return;
      }
    }
    // Clear any previous session before starting a new one.
    reset();
    initiateConnectionToDevice(deviceId);
  }

  /** Start to connect to associated devices */
  public abstract void initiateConnectionToDevice(@NonNull UUID deviceId);

  /** Start the association with a new device */
  public abstract void startAssociation(
      @NonNull String nameForAssociation, @NonNull AssociationCallback callback);

  /** Start the association with a new device using out of band verification code exchange */
  public abstract void startOutOfBandAssociation(
      @NonNull String nameForAssociation,
      @NonNull OobChannel oobChannel,
      @NonNull AssociationCallback callback);

  /** Disconnect the provided device from this manager. */
  public abstract void disconnectDevice(@NonNull String deviceId);

  /** Get current {@link AssociationCallback}. */
  @Nullable
  public abstract AssociationCallback getAssociationCallback();

  /** Set current {@link AssociationCallback}. */
  public abstract void setAssociationCallback(@Nullable AssociationCallback callback);

  /** Set the value of the client device name */
  public void setClientDeviceName(String deviceName) {
    clientDeviceName = deviceName;
  }

  /** Set the value of client device's mac address */
  public void setClientDeviceAddress(String macAddress) {
    clientDeviceAddress = macAddress;
  }

  /** Initialize and start the manager. */
  @CallSuper
  public void start() {}

  /** Stop the manager and clean up. */
  public void stop() {
    for (ConnectedRemoteDevice device : connectedDevices) {
      if (device.gatt != null) {
        device.gatt.close();
      }
    }
    connectedDevices.clear();
  }

  /** Stop the association process with any device. */
  public void stopAssociation() {
    if (!isAssociating()) {
      return;
    }
    reset();
  }

  /** Register a {@link Callback} to be notified on the {@link Executor}. */
  public void registerCallback(@NonNull Callback callback, @NonNull Executor executor) {
    callbacks.add(callback, executor);
  }

  /**
   * Unregister a callback.
   *
   * @param callback The {@link Callback} to unregister.
   */
  public void unregisterCallback(@NonNull Callback callback) {
    callbacks.remove(callback);
  }

  /**
   * Send a message to a connected device.
   *
   * @param deviceId Id of connected device.
   * @param message {@link DeviceMessage} to send.
   */
  public void sendMessage(@NonNull String deviceId, @NonNull DeviceMessage message) {
    ConnectedRemoteDevice device = getConnectedDevice(deviceId);
    if (device == null) {
      logw(TAG, "Attempted to send message to unknown device $deviceId. Ignored.");
      return;
    }

    sendMessage(device, message);
  }

  /**
   * Send a message to a connected device.
   *
   * @param device The connected {@link ConnectedRemoteDevice}.
   * @param message {@link DeviceMessage} to send.
   */
  public void sendMessage(@NonNull ConnectedRemoteDevice device, @NonNull DeviceMessage message) {
    String deviceId = device.deviceId;
    if (deviceId == null) {
      deviceId = "Unidentified device";
    }

    logd(TAG, "Writing " + message.getMessage().length + " bytes to " + deviceId + ".");
    device.secureChannel.sendClientMessage(message);
  }

  /** Clean manager status and callbacks. */
  @CallSuper
  public void reset() {
    clientDeviceAddress = null;
    clientDeviceName = null;
    connectedDevices.clear();
  }

  /** Notify that the user has accepted a pairing code or other out-of-band confirmation. */
  public void notifyOutOfBandAccepted() {
    if (getConnectedDevice() == null) {
      disconnectWithError(
          "Null connected device found when out-of-band confirmation " + "received.");
      return;
    }

    AssociationSecureChannel secureChannel =
        (AssociationSecureChannel) getConnectedDevice().secureChannel;
    if (secureChannel == null) {
      disconnectWithError(
          "Null SecureBleChannel found for the current connected device "
              + "when out-of-band confirmation received.");
      return;
    }

    secureChannel.notifyOutOfBandAccepted();
  }

  /** Returns the secure channel of current connected device. */
  @Nullable
  public SecureChannel getConnectedDeviceChannel() {
    ConnectedRemoteDevice connectedDevice = getConnectedDevice();
    if (connectedDevice == null) {
      return null;
    }

    return connectedDevice.secureChannel;
  }

  /** Return the current connected device. */
  @Nullable
  protected final ConnectedRemoteDevice getConnectedDevice() {
    if (connectedDevices.isEmpty()) {
      return null;
    }
    // Directly return the next because there will only be one device connected at one time.
    return connectedDevices.iterator().next();
  }

  /**
   * Get the {@link ConnectedRemoteDevice} with matching {@link BluetoothGatt} if available. Returns
   * {@code null} if no matches are found.
   */
  @Nullable
  protected final ConnectedRemoteDevice getConnectedDevice(@NonNull BluetoothGatt gatt) {
    for (ConnectedRemoteDevice device : connectedDevices) {
      if (device.gatt == gatt) {
        return device;
      }
    }

    return null;
  }

  /**
   * Get the {@link ConnectedRemoteDevice} with matching {@link BluetoothDevice} if available.
   * Returns {@code null} if no matches are found.
   */
  @Nullable
  protected final ConnectedRemoteDevice getConnectedDevice(@NonNull BluetoothDevice device) {
    for (ConnectedRemoteDevice connectedDevice : connectedDevices) {
      if (device.equals(connectedDevice.device)) {
        return connectedDevice;
      }
    }

    return null;
  }

  /**
   * Get the {@link ConnectedRemoteDevice} with matching device id if available. Returns {@code
   * null} if no matches are found.
   */
  @Nullable
  protected final ConnectedRemoteDevice getConnectedDevice(@NonNull String deviceId) {
    for (ConnectedRemoteDevice device : connectedDevices) {
      if (deviceId.equals(device.deviceId)) {
        return device;
      }
    }

    return null;
  }

  /** Add the {@link ConnectedRemoteDevice} that has connected. */
  protected final void addConnectedDevice(@NonNull ConnectedRemoteDevice device) {
    device.secureChannel.setCompressionEnabled(isCompressionEnabled);
    connectedDevices.add(device);
  }

  /** Return the number of devices currently connected. */
  protected final int getConnectedDevicesCount() {
    return connectedDevices.size();
  }

  /** Remove [@link BleDevice} that has been disconnected. */
  protected final void removeConnectedDevice(@NonNull ConnectedRemoteDevice device) {
    connectedDevices.remove(device);
  }

  /** Return [@code true} if the manager is currently in an association process. */
  protected final boolean isAssociating() {
    return getAssociationCallback() != null;
  }

  /**
   * Set the device id of {@link ConnectedRemoteDevice} and then notify device connected callback.
   *
   * @param deviceId The device id received from remote device.
   */
  protected final void setDeviceIdAndNotifyCallbacks(@NonNull String deviceId) {
    logd(TAG, "Setting device id: " + deviceId);
    ConnectedRemoteDevice connectedDevice = getConnectedDevice();
    if (connectedDevice == null) {
      disconnectWithError("Null connected device found when device id received.");
      return;
    }

    connectedDevice.deviceId = deviceId;
    callbacks.invoke(callback -> callback.onDeviceConnected(deviceId));
  }

  /** Log error which causes the disconnect with {@link Exception} and notify callbacks. */
  protected final void disconnectWithError(@NonNull String errorMessage, @Nullable Exception e) {
    loge(TAG, errorMessage, e);
    if (isAssociating()) {
      getAssociationCallback().onAssociationError(DEVICE_ERROR_INVALID_HANDSHAKE);
    }
    reset();
  }

  /** Log error which cause the disconnection and notify callbacks. */
  protected final void disconnectWithError(@NonNull String errorMessage) {
    disconnectWithError(errorMessage, null);
  }

  protected final SecureChannel.Callback secureChannelCallback =
      new SecureChannel.Callback() {
        @Override
        public void onSecureChannelEstablished() {
          ConnectedRemoteDevice connectedDevice = getConnectedDevice();
          if (connectedDevice == null || connectedDevice.deviceId == null) {
            disconnectWithError("Null device id found when secure channel " + "established.");
            return;
          }
          String deviceId = connectedDevice.deviceId;
          if (clientDeviceAddress == null) {
            disconnectWithError("Null device address found when secure channel " + "established.");
            return;
          }

          if (isAssociating()) {
            logd(
                TAG,
                "Secure channel established for un-associated device. Saving "
                    + "association of that device for current user.");
            storage.addAssociatedDeviceForActiveUser(
                new AssociatedDevice(
                    deviceId,
                    clientDeviceAddress,
                    clientDeviceName,
                    /* isConnectionEnabled= */ true));
            AssociationCallback callback = getAssociationCallback();
            if (callback != null) {
              callback.onAssociationCompleted(deviceId);
              setAssociationCallback(null);
            }
          }
          callbacks.invoke(callback -> callback.onSecureChannelEstablished(deviceId));
        }

        @Override
        public void onEstablishSecureChannelFailure(int error) {
          ConnectedRemoteDevice connectedDevice = getConnectedDevice();
          if (connectedDevice == null || connectedDevice.deviceId == null) {
            disconnectWithError(
                "Null device id found when secure channel " + "failed to establish.");
            return;
          }
          String deviceId = connectedDevice.deviceId;
          callbacks.invoke(callback -> callback.onSecureChannelError(deviceId));

          if (isAssociating()) {
            getAssociationCallback().onAssociationError(error);
          }

          disconnectWithError("Error while establishing secure connection.");
        }

        @Override
        public void onMessageReceived(DeviceMessage deviceMessage) {
          ConnectedRemoteDevice connectedDevice = getConnectedDevice();
          if (connectedDevice == null || connectedDevice.deviceId == null) {
            disconnectWithError("Null device id found when message received.");
            return;
          }

          logd(
              TAG,
              "Received new message from "
                  + connectedDevice.deviceId
                  + " with "
                  + deviceMessage.getMessage().length
                  + " bytes in its "
                  + "payload. Notifying "
                  + callbacks.size()
                  + " callbacks.");
          callbacks.invoke(
              callback -> callback.onMessageReceived(connectedDevice.deviceId, deviceMessage));
        }

        @Override
        public void onMessageReceivedError(Exception exception) {
          disconnectWithError("Error while receiving message.");
        }

        @Override
        public void onDeviceIdReceived(String deviceId) {
          setDeviceIdAndNotifyCallbacks(deviceId);
        }
      };

  /** State for a connected device. */
  public enum ConnectedDeviceState {
    CONNECTING,
    PENDING_VERIFICATION,
    CONNECTED,
    UNKNOWN
  }

  /** Container class to hold information about a connected device. */
  public static class ConnectedRemoteDevice {
    @NonNull public BluetoothDevice device;
    @Nullable public BluetoothGatt gatt;
    @NonNull public ConnectedDeviceState state;
    @Nullable public String deviceId;
    @Nullable public SecureChannel secureChannel;

    public ConnectedRemoteDevice(@NonNull BluetoothDevice device, @Nullable BluetoothGatt gatt) {
      this.device = device;
      this.gatt = gatt;
      state = ConnectedDeviceState.UNKNOWN;
    }
  }

  /** Callback for triggered events from {@link CarBluetoothManager}. */
  public interface Callback {
    /**
     * Triggered when device is connected and device id retrieved. Device is now ready to receive
     * messages.
     *
     * @param deviceId Id of device that has connected.
     */
    void onDeviceConnected(@NonNull String deviceId);

    /**
     * Triggered when device is disconnected.
     *
     * @param deviceId Id of device that has disconnected.
     */
    void onDeviceDisconnected(@NonNull String deviceId);

    /**
     * Triggered when device has established encryption for secure communication.
     *
     * @param deviceId Id of device that has established encryption.
     */
    void onSecureChannelEstablished(@NonNull String deviceId);

    /**
     * Triggered when a new message is received.
     *
     * @param deviceId Id of the device that sent the message.
     * @param message {@link DeviceMessage} received.
     */
    void onMessageReceived(@NonNull String deviceId, @NonNull DeviceMessage message);

    /**
     * Triggered when an error when establishing the secure channel.
     *
     * @param deviceId Id of the device that experienced the error.
     */
    void onSecureChannelError(@NonNull String deviceId);
  }
}
