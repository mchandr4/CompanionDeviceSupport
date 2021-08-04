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

package com.google.android.connecteddevice.transport.spp;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.bluetooth.BluetoothDevice;
import android.os.ParcelUuid;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.util.RemoteCallbackBinder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Binder used to delegate calls to {@link android.bluetooth.BluetoothSocket} via a service running
 * in the foreground user.
 */
public class ConnectedDeviceSppDelegateBinder extends IConnectedDeviceSppDelegate.Stub {
  public static final String ACTION_BIND_SPP = "com.google.android.connecteddevice.BIND_SPP";

  private static final String TAG = "ConnectedDeviceSppDelegateBinder";

  private ISppCallback remoteCallback;
  private final Map<Connection, OnMessageReceivedListener> onMessageReceivedListeners =
      new HashMap<>();
  private final Map<UUID, OnErrorListener> connectionErrorListeners = new HashMap<>();
  private final Set<PendingConnection> pendingConnections = new HashSet<>();

  @VisibleForTesting RemoteCallbackBinder callbackBinder;

  private final OnRemoteCallbackSetListener onRemoteCallbackSetListener;

  public ConnectedDeviceSppDelegateBinder(OnRemoteCallbackSetListener listener) {
    onRemoteCallbackSetListener = listener;
  }

  @Override
  public void setCallback(@NonNull ISppCallback callback) {
    logd(TAG, "Set callback:" + callback);
    callbackBinder =
        new RemoteCallbackBinder(callback.asBinder(), iBinder -> clearCallback(callback));
    this.remoteCallback = callback;
    if (onRemoteCallbackSetListener != null) {
      onRemoteCallbackSetListener.onRemoteCallbackSet(true);
    }
  }

  @Override
  public void clearCallback(@NonNull ISppCallback callback) {
    if (remoteCallback == null || remoteCallback.asBinder() != callback.asBinder()) {
      logw(TAG, "ISppCallback(" + callback + ") has not been set before.");
      return;
    }

    logd(TAG, "Clear callback:" + callback);
    remoteCallback = null;
    if (onRemoteCallbackSetListener != null) {
      onRemoteCallbackSetListener.onRemoteCallbackSet(false);
    }
    if (callbackBinder == null) {
      logw(TAG, "Remote callback binder is null when trying to clear callback");
      return;
    }
    callbackBinder.cleanUp();
    callbackBinder = null;
  }

  /**
   * Notify the system user that an SPP connection has been established
   *
   * @param pendingConnectionId ID associated with the initial connection request
   * @param remoteDevice device on the other end of the connection
   * @param deviceName name of the device on the other end of the connection
   */
  @Override
  public void notifyConnected(
      int pendingConnectionId, @NonNull BluetoothDevice remoteDevice, @Nullable String deviceName) {
    for (PendingConnection pendingConnection : pendingConnections) {
      if (pendingConnection.getId() == pendingConnectionId) {
        pendingConnections.remove(pendingConnection);
        pendingConnection.notifyConnected(remoteDevice, deviceName);
        return;
      }
    }
    logw(
        TAG,
        "No pendingConnection is associated with ID "
            + pendingConnectionId
            + ". Skipping notifyConnected.");
  }

  /**
   * Notify the system user that a message was received from the connected device.
   *
   * @param connection The active connection that the message was received on
   * @param message Raw content of the message that was received
   */
  @Override
  public void notifyMessageReceived(@NonNull Connection connection, @NonNull byte[] message) {
    logd(TAG, "Notifying listeners of a new message.");
    OnMessageReceivedListener listener = onMessageReceivedListeners.get(connection);
    if (listener == null) {
      loge(TAG, "There are no listeners registered for connection " + connection.getServiceUuid()
          + ". This message is being dropped on the floor!");
      return;
    }
    listener.onMessageReceived(message);
  }

  /** Notify the system user that an error occurred on an active connection. */
  @Override
  public void notifyError(@NonNull Connection connection) {
    OnErrorListener onErrorListener =
        connectionErrorListeners.get(connection.getServiceUuid().getUuid());
    if (onErrorListener != null) {
      onErrorListener.onError(connection);
    }
  }

  /**
   * Notify the system user that a requested connection attempt failed.
   *
   * @param pendingConnectionId ID associated with the initial connection request
   */
  @Override
  public void notifyConnectAttemptFailed(int pendingConnectionId) {
    for (PendingConnection pendingConnection : pendingConnections) {
      if (pendingConnection.getId() == pendingConnectionId) {
        pendingConnections.remove(pendingConnection);
        pendingConnection.notifyConnectionError();
        return;
      }
    }
    logw(
        TAG,
        "No pendingConnection is associated with id "
            + pendingConnectionId
            + ". Skipping notifyConnectionError.");
  }

  /**
   * Request to the foreground user to open a connection as the RFCOMM server.
   *
   * @param serviceUuid UUID to listen for client connection requests
   * @param isSecure true if resulting BT connection should be encrypted by the platform
   * @return An object representing the pending connection request
   */
  @Nullable
  public PendingConnection connectAsServer(@NonNull UUID serviceUuid, boolean isSecure)
      throws RemoteException {
    logd(TAG, "Connecting as server.");
    if (remoteCallback == null) {
      logw(TAG, "Remote callback is null when trying to establish connection as a server.");
      return null;
    }

    if (remoteCallback.onStartConnectionAsServerRequested(new ParcelUuid(serviceUuid), isSecure)) {
      PendingConnection pendingConnection = new PendingConnection(serviceUuid, isSecure);
      pendingConnections.add(pendingConnection);
      return pendingConnection;
    }

    return null;
  }

  /**
   * Request to the foreground user to connect to a remote device as an RFCOMM client.
   *
   * @param serviceUuid UUID of connection request
   * @param remoteDevice Device to connect to
   * @param isSecure true if resulting BT connection should be encrypted by the platform
   * @return An object representing the pending connection request
   */
  @Nullable
  public PendingConnection connectAsClient(
      @NonNull UUID serviceUuid, @NonNull BluetoothDevice remoteDevice, boolean isSecure)
      throws RemoteException {
    logd(TAG, "Connecting as client.");
    if (remoteCallback == null) {
      logw(TAG, "Remote callback is null when trying to establish connection as a client.");
      return null;
    }
    remoteCallback.onStartConnectionAsClientRequested(
        new ParcelUuid(serviceUuid), remoteDevice, isSecure);

    PendingConnection pendingConnection =
        new PendingConnection(serviceUuid, remoteDevice, isSecure);
    pendingConnections.add(pendingConnection);
    return pendingConnection;
  }

  /** Request to the foreground user to disconnect an active connection */
  public void disconnect(@NonNull Connection connection) throws RemoteException {
    if (remoteCallback != null) {
      remoteCallback.onDisconnectRequested(connection);
    }
  }

  /**
   * Request to the foreground user to cancel a connection request before an active connection is
   * established
   */
  public void cancelConnectionAttempt(@NonNull PendingConnection pendingConnection)
      throws RemoteException {
    pendingConnections.remove(pendingConnection);
    if (remoteCallback != null) {
      remoteCallback.onCancelConnectionAttemptRequested(pendingConnection.getId());
    }
  }

  /**
   * Request to the foreground user to send a {@code message} on an active {@code connection}
   *
   * @return An object representing the pending send message request
   */
  @Nullable
  public PendingSentMessage sendMessage(@NonNull Connection connection, @NonNull byte[] message)
      throws RemoteException {
    if (remoteCallback != null) {
      return remoteCallback.onSendMessageRequested(connection, message);
    }
    return null;
  }

  public void registerConnectionCallback(
      @NonNull UUID serviceUuid, @Nullable OnErrorListener onErrorListener) {
    connectionErrorListeners.put(serviceUuid, onErrorListener);
  }

  public void unregisterConnectionCallback(@NonNull UUID serviceUuid) {
    connectionErrorListeners.remove(serviceUuid);
  }

  public void setOnMessageReceivedListener(
      @NonNull Connection connection,
      @Nullable OnMessageReceivedListener onMessageReceivedListener) {
    logd(TAG, "Registering a new message listener for " + connection.getServiceUuid() + ".");
    onMessageReceivedListeners.put(connection, onMessageReceivedListener);
  }

  public void clearOnMessageReceivedListener(@NonNull Connection connection) {
    logd(TAG, "Removing message listener for " + connection.getServiceUuid() + ".");
    onMessageReceivedListeners.remove(connection);
  }

  /** Callbacks for notifying the relevant events with the SPP connection. */
  public interface OnErrorListener {
    void onError(Connection connection);
  }

  /** Listener for when a message has been received on an open connection. */
  public interface OnMessageReceivedListener {
    void onMessageReceived(@NonNull byte[] message);
  }

  /** Listener for when the remote callback is set by the remote service. */
  public interface OnRemoteCallbackSetListener {
    void onRemoteCallbackSet(boolean hasBeenSet);
  }
}
