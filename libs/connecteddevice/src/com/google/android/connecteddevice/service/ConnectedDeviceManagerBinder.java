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

package com.google.android.connecteddevice.service;

import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.ConnectedDeviceManager;
import com.google.android.connecteddevice.ConnectedDeviceManager.ConnectionCallback;
import com.google.android.connecteddevice.ConnectedDeviceManager.DeviceAssociationCallback;
import com.google.android.connecteddevice.ConnectedDeviceManager.DeviceCallback;
import com.google.android.connecteddevice.api.IConnectedDeviceManager;
import com.google.android.connecteddevice.api.IConnectionCallback;
import com.google.android.connecteddevice.api.IDeviceAssociationCallback;
import com.google.android.connecteddevice.api.IDeviceCallback;
import com.google.android.connecteddevice.api.IOnLogRequestedListener;
import com.google.android.connecteddevice.logging.LoggingManager;
import com.google.android.connecteddevice.logging.LoggingManager.OnLogRequestedListener;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.DeviceMessage;
import com.google.android.connecteddevice.util.RemoteCallbackBinder;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Binder for exposing ConnectedDeviceManager to features. */
public class ConnectedDeviceManagerBinder extends IConnectedDeviceManager.Stub {

  private static final String TAG = "ConnectedDeviceManagerBinder";

  private final ConnectedDeviceManager connectedDeviceManager;
  private final LoggingManager loggingManager;

  // aidl callback binder -> connection callback
  // Need to maintain a mapping in order to support unregistering callbacks.
  private final ConcurrentHashMap<IBinder, ConnectionCallback> connectionCallbacks =
      new ConcurrentHashMap<>();

  // aidl callback binder -> device association callback
  // Need to maintain a mapping in order to support unregistering callbacks.
  private final ConcurrentHashMap<IBinder, DeviceAssociationCallback> associationCallbacks =
      new ConcurrentHashMap<>();

  // aidl callback binder  -> device callback
  // Need to maintain a mapping in order to support unregistering callbacks.
  private final ConcurrentHashMap<IBinder, DeviceCallback> deviceCallbacks =
      new ConcurrentHashMap<>();

  // aidl listener binder -> log requested listener
  // Need to maintain a mapping in order to support unregistering callbacks.
  private final ConcurrentHashMap<IBinder, OnLogRequestedListener> logRequestedListeners =
      new ConcurrentHashMap<>();

  private final Executor callbackExecutor = Executors.newSingleThreadExecutor();
  private final Set<RemoteCallbackBinder> callbackBinders = new CopyOnWriteArraySet<>();

  public ConnectedDeviceManagerBinder(
      ConnectedDeviceManager connectedDeviceManager, LoggingManager loggingManager) {
    this.connectedDeviceManager = connectedDeviceManager;
    this.loggingManager = loggingManager;
  }

  @Override
  public List<ConnectedDevice> getActiveUserConnectedDevices() {
    return connectedDeviceManager.getActiveUserConnectedDevices();
  }

  @Override
  public void registerActiveUserConnectionCallback(@NonNull IConnectionCallback callback) {
    ConnectionCallback connectionCallback =
        new ConnectionCallback() {
          @Override
          public void onDeviceConnected(ConnectedDevice device) {
            try {
              callback.onDeviceConnected(device);
            } catch (RemoteException exception) {
              loge(TAG, "onDeviceConnected failed.", exception);
            }
          }

          @Override
          public void onDeviceDisconnected(ConnectedDevice device) {
            try {
              callback.onDeviceDisconnected(device);
            } catch (RemoteException exception) {
              loge(TAG, "onDeviceDisconnected failed.", exception);
            }
          }
        };
    connectedDeviceManager.registerActiveUserConnectionCallback(
        connectionCallback, callbackExecutor);
    RemoteCallbackBinder remoteBinder =
        new RemoteCallbackBinder(
            callback.asBinder(), iBinder -> unregisterConnectionCallback(callback));
    callbackBinders.add(remoteBinder);
    connectionCallbacks.put(callback.asBinder(), connectionCallback);
  }

  @Override
  public void unregisterConnectionCallback(@NonNull IConnectionCallback callback) {
    IBinder binder = callback.asBinder();
    RemoteCallbackBinder remoteBinder = findRemoteCallbackBinder(binder);
    if (remoteBinder == null) {
      loge(
          TAG,
          "RemoteCallbackBinder is null, IConnectionCallback was not " + "previously registered.");
      return;
    }
    ConnectionCallback connectionCallback = connectionCallbacks.get(binder);
    if (connectionCallback == null) {
      loge(TAG, "ConnectionCallback is null.");
      return;
    }
    connectedDeviceManager.unregisterConnectionCallback(connectionCallback);
    remoteBinder.cleanUp();
    callbackBinders.remove(remoteBinder);
    connectionCallbacks.remove(binder);
  }

  @Override
  public void registerDeviceCallback(
      @NonNull ConnectedDevice connectedDevice,
      @NonNull ParcelUuid recipientId,
      @NonNull IDeviceCallback callback) {
    DeviceCallback deviceCallback =
        new DeviceCallback() {
          @Override
          public void onSecureChannelEstablished(ConnectedDevice device) {
            try {
              callback.onSecureChannelEstablished(device);
            } catch (RemoteException exception) {
              loge(TAG, "onSecureChannelEstablished failed.", exception);
            }
          }

          @Override
          public void onMessageReceived(ConnectedDevice device, DeviceMessage message) {
            try {
              callback.onMessageReceived(device, message);
            } catch (RemoteException exception) {
              loge(TAG, "onMessageReceived failed.", exception);
            }
          }

          @Override
          public void onDeviceError(ConnectedDevice device, int error) {
            try {
              callback.onDeviceError(device, error);
            } catch (RemoteException exception) {
              loge(TAG, "onDeviceError failed.", exception);
            }
          }
        };
    connectedDeviceManager.registerDeviceCallback(
        connectedDevice,
        recipientId.getUuid(),
        deviceCallback,
        callbackExecutor);
    deviceCallbacks.put(callback.asBinder(), deviceCallback);
    RemoteCallbackBinder remoteBinder =
        new RemoteCallbackBinder(
            callback.asBinder(),
            iBinder -> unregisterDeviceCallback(connectedDevice, recipientId, callback));
    callbackBinders.add(remoteBinder);
  }

  @Override
  public void unregisterDeviceCallback(
      @NonNull ConnectedDevice connectedDevice,
      @NonNull ParcelUuid recipientId,
      @NonNull IDeviceCallback callback) {
    IBinder binder = callback.asBinder();
    RemoteCallbackBinder remoteBinder = findRemoteCallbackBinder(binder);
    if (remoteBinder == null) {
      loge(
          TAG,
          "RemoteCallbackBinder is null, IDeviceCallback was not previously "
              + "registered. Ignoring call to unregister.");
      return;
    }
    DeviceCallback deviceCallback = deviceCallbacks.remove(binder);
    if (deviceCallback == null) {
      loge(TAG, "No DeviceCallback associated with given callback. " + "Cannot unregister.");
      return;
    }
    connectedDeviceManager.unregisterDeviceCallback(
        connectedDevice, recipientId.getUuid(), deviceCallback);
    remoteBinder.cleanUp();
    callbackBinders.remove(remoteBinder);
  }

  @Override
  public boolean sendMessage(
      @NonNull ConnectedDevice connectedDevice,
      @NonNull DeviceMessage message) {
    try {
      connectedDeviceManager.sendMessage(connectedDevice, message);
      return true;
    } catch (IllegalStateException exception) {
      loge(TAG, "Attempted to send message prior to secure channel established.", exception);
    }
    return false;
  }

  @Override
  public void registerDeviceAssociationCallback(@NonNull IDeviceAssociationCallback callback) {
    DeviceAssociationCallback associationCallback =
        new DeviceAssociationCallback() {
          @Override
          public void onAssociatedDeviceAdded(AssociatedDevice device) {
            try {
              callback.onAssociatedDeviceAdded(device);
            } catch (RemoteException exception) {
              loge(TAG, "onAssociatedDeviceAdded failed.", exception);
            }
          }

          @Override
          public void onAssociatedDeviceRemoved(AssociatedDevice device) {
            try {
              callback.onAssociatedDeviceRemoved(device);
            } catch (RemoteException exception) {
              loge(TAG, "onAssociatedDeviceRemoved failed.", exception);
            }
          }

          @Override
          public void onAssociatedDeviceUpdated(AssociatedDevice device) {
            try {
              callback.onAssociatedDeviceUpdated(device);
            } catch (RemoteException exception) {
              loge(TAG, "onAssociatedDeviceUpdated failed.", exception);
            }
          }
        };

    connectedDeviceManager.registerDeviceAssociationCallback(
        associationCallback, callbackExecutor);
    RemoteCallbackBinder remoteBinder =
        new RemoteCallbackBinder(
            callback.asBinder(), iBinder -> unregisterDeviceAssociationCallback(callback));
    callbackBinders.add(remoteBinder);
    associationCallbacks.put(callback.asBinder(), associationCallback);
  }

  @Override
  public void unregisterDeviceAssociationCallback(@NonNull IDeviceAssociationCallback callback) {
    IBinder binder = callback.asBinder();
    RemoteCallbackBinder remoteBinder = findRemoteCallbackBinder(binder);
    if (remoteBinder == null) {
      loge(
          TAG,
          "RemoteCallbackBinder is null, IDeviceAssociationCallback was "
              + "not previously registered.");
      return;
    }
    DeviceAssociationCallback associationCallback = associationCallbacks.remove(binder);
    if (associationCallback == null) {
      loge(TAG, "DeviceAssociationCallback is null.");
      return;
    }
    connectedDeviceManager.unregisterDeviceAssociationCallback(associationCallback);
    remoteBinder.cleanUp();
    callbackBinders.remove(remoteBinder);
  }

  @Override
  public void registerOnLogRequestedListener(
      int loggerId, @NonNull IOnLogRequestedListener listener) {
    OnLogRequestedListener onLogRequestedListener =
        () -> {
          try {
            listener.onLogRecordsRequested();
          } catch (RemoteException exception) {
            loge(TAG, "Failed to notify log records requested.", exception);
          }
        };

    loggingManager.registerLogRequestedListener(loggerId, onLogRequestedListener, callbackExecutor);
    IBinder listenerBinder = listener.asBinder();
    RemoteCallbackBinder remoteBinder =
        new RemoteCallbackBinder(
            listenerBinder, iBinder -> unregisterOnLogRequestedListener(loggerId, listener));
    callbackBinders.add(remoteBinder);
    logRequestedListeners.put(listenerBinder, onLogRequestedListener);
  }

  @Override
  public void unregisterOnLogRequestedListener(
      int loggerId, @NonNull IOnLogRequestedListener listener) {
    IBinder listenerBinder = listener.asBinder();
    RemoteCallbackBinder remoteBinder = findRemoteCallbackBinder(listenerBinder);
    if (remoteBinder == null) {
      loge(
          TAG,
          "RemoteCallbackBinder is null, IOnLogRequestedListener "
              + listener
              + " is not previously registered.");
      return;
    }
    OnLogRequestedListener onLogRequestedListener = logRequestedListeners.remove(listenerBinder);
    if (onLogRequestedListener == null) {
      loge(
          TAG,
          "OnLogRequestedListener is null, IOnLogRequestedListener "
              + listener
              + " is not previously registered.");
      return;
    }
    loggingManager.unregisterLogRequestedListener(loggerId, onLogRequestedListener);
    remoteBinder.cleanUp();
    callbackBinders.remove(listenerBinder);
  }

  @Override
  public void processLogRecords(int loggerId, @NonNull byte[] logRecords) {
    loggingManager.prepareLocalLogRecords(loggerId, logRecords);
  }

  @Nullable
  private RemoteCallbackBinder findRemoteCallbackBinder(@NonNull IBinder binder) {
    for (RemoteCallbackBinder remoteBinder : callbackBinders) {
      if (remoteBinder.getCallbackBinder().equals(binder)) {
        return remoteBinder;
      }
    }
    return null;
  }
}
