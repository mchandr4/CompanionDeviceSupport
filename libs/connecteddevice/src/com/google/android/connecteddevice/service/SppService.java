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

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.RemoteException;
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder;
import com.google.android.connecteddevice.transport.spp.Connection;
import com.google.android.connecteddevice.transport.spp.IConnectedDeviceSppDelegate;
import com.google.android.connecteddevice.transport.spp.ISppCallback;
import com.google.android.connecteddevice.transport.spp.PendingConnection;
import com.google.android.connecteddevice.transport.spp.PendingSentMessage;
import com.google.android.connecteddevice.transport.spp.SppManager;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Early start service that will hold a {@link IConnectedDeviceSppDelegate} reference to pass
 * bluetooth socket connection related events to {@link ConnectedDeviceService}.
 */
public class SppService extends Service {
  private static final String TAG = "SppService";
  private static final int MAX_BIND_ATTEMPTS = 3;

  private static final Duration BIND_RETRY_DURATION = Duration.ofSeconds(1);

  private final ConcurrentHashMap<Connection, SppManager> activeConnections =
      new ConcurrentHashMap<>();
  // Map from a PendingConnection's unique identifier to its associated sppManager.
  private final ConcurrentHashMap<Integer, SppManager> pendingConnections =
      new ConcurrentHashMap<>();

  private int bindAttempts;
  private IConnectedDeviceSppDelegate delegate;

  private final Executor callbackExecutor = Executors.newSingleThreadExecutor();
  private final ISppCallback sppCallback =
      new ISppCallback.Stub() {
        @Override
        public boolean onStartConnectionAsServerRequested(
            ParcelUuid serviceUuid, boolean isSecure) {
          return startConnectionAsServer(serviceUuid, isSecure);
        }

        @Override
        public void onStartConnectionAsClientRequested(
            ParcelUuid serviceUuid, BluetoothDevice remoteDevice, boolean isSecure) {
          startConnectionAsClient(serviceUuid, remoteDevice, isSecure);
        }

        @Override
        public PendingSentMessage onSendMessageRequested(Connection connection, byte[] message) {
          PendingSentMessage pendingSentMessage = new PendingSentMessage();
          sendMessage(connection, message, pendingSentMessage);
          return pendingSentMessage;
        }

        @Override
        public void onDisconnectRequested(Connection connection) {
          SppManager manager = activeConnections.get(connection);
          if (manager == null) {
            loge(TAG, "Can not find suitable manager to disconnect the remote device. Ignored.");
            return;
          }
          manager.cleanup();
        }

        @Override
        public void onCancelConnectionAttemptRequested(int pendingConnectionId) {
          SppManager manager = pendingConnections.get(pendingConnectionId);
          if (manager == null) {
            loge(
                TAG,
                "Can not find suitable manager related to pending connection with id "
                    + pendingConnectionId
                    + "to cancel connection attempt. Ignored.");
            return;
          }
          manager.cleanup();
          pendingConnections.remove(pendingConnectionId);
        }
      };

  private final ServiceConnection serviceConnection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
          logd(TAG, "Successfully connected to remote service.");
          delegate = IConnectedDeviceSppDelegate.Stub.asInterface(service);
          try {
            delegate.setCallback(sppCallback);
          } catch (RemoteException e) {
            loge(TAG, "Error while set callback to delegate.", e);
          }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
          logd(TAG, "Disconnected to remote service.");
          // TODO(b/170164323) Need to handle the service disconnect situation.
          cleanUp();
        }
      };

  @Override
  public void onCreate() {
    super.onCreate();
    logd(TAG, "Service is created, start binding to ConnectedDeviceService.");
    bindAttempts = 0;
    bindToService();
  }

  @Override
  public void onDestroy() {
    logd(TAG, "Service was destroyed.");
    cleanUp();
    unbindService(serviceConnection);
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private void cleanUp() {
    logd(TAG, "Cleaning up service.");
    for (SppManager manager : activeConnections.values()) {
      manager.cleanup();
    }
    for (SppManager manager : pendingConnections.values()) {
      manager.cleanup();
    }
    activeConnections.clear();
    pendingConnections.clear();
    if (delegate == null) {
      return;
    }
    try {
      delegate.clearCallback(sppCallback);
    } catch (RemoteException e) {
      loge(TAG, "Error while clear callback of delegate.", e);
    }
    delegate = null;
  }

  private void bindToService() {
    Intent intent = new Intent(this, ConnectedDeviceService.class);
    intent.setAction(ConnectedDeviceSppDelegateBinder.ACTION_BIND_SPP);
    boolean success = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    if (success) {
      logd(TAG, "Successfully started bind attempt to ConnectedDeviceService.");
      return;
    }
    bindAttempts++;
    if (bindAttempts > MAX_BIND_ATTEMPTS) {
      loge(
          TAG,
          "Failed to bind to CompanionDeviceSupportService after "
              + bindAttempts
              + " attempts. Aborting.");
      return;
    }
    logw(
        TAG,
        "Unable to bind to CompanionDeviceSupportService. Trying again with a duration of "
            + BIND_RETRY_DURATION.toMillis()
            + " milliseconds.");
    new Handler(Looper.getMainLooper())
        .postDelayed(this::bindToService, BIND_RETRY_DURATION.toMillis());
  }

  private boolean startConnectionAsServer(ParcelUuid serviceUuid, boolean isSecure) {
    logd(TAG, "startConnectionAsServer for uuid: " + serviceUuid);

    SppManager manager = new SppManager(isSecure);
    PendingConnection pendingConnection = new PendingConnection(serviceUuid.getUuid(), isSecure);
    pendingConnections.put(pendingConnection.getId(), manager);
    manager.registerCallback(
        generateConnectionCallback(manager, serviceUuid, isSecure), callbackExecutor);
    return manager.startListening(serviceUuid.getUuid());
  }

  private void startConnectionAsClient(
      ParcelUuid serviceUuid, BluetoothDevice device, boolean isSecure) {
    SppManager manager = new SppManager(isSecure);
    PendingConnection pendingConnection =
        new PendingConnection(serviceUuid.getUuid(), device, isSecure);
    pendingConnections.put(pendingConnection.getId(), manager);

    manager.registerCallback(
        generateConnectionCallback(manager, serviceUuid, isSecure), callbackExecutor);
    manager.connect(device, serviceUuid.getUuid());
  }

  private boolean sendMessage(
      Connection connection, byte[] message, PendingSentMessage pendingSentMessage) {
    SppManager manager = activeConnections.get(connection);
    if (manager == null) {
      loge(TAG, "Can not find suitable manager for the connection to send message. Ignore.");
      return false;
    }
    return manager.write(message, pendingSentMessage);
  }

  private SppManager.ConnectionCallback generateConnectionCallback(
      SppManager manager, ParcelUuid uuid, boolean isSecure) {
    return new SppManager.ConnectionCallback() {
      @Override
      public void onRemoteDeviceConnected(BluetoothDevice device) {
        Connection connection = new Connection(uuid, device, isSecure, device.getName());
        activeConnections.put(connection, manager);
        manager.addOnMessageReceivedListener(
            generateMessageReceivedListener(connection), callbackExecutor);
        try {
          // TODO(b/170164323) Need to handle the service disconnect situation.
          for (Map.Entry<Integer, SppManager> entry : pendingConnections.entrySet()) {
            if (entry.getValue() == manager) {
              pendingConnections.remove(entry.getKey());
              if (delegate == null) {
                logw(TAG, "Remote device connected with no delegate set");
              } else {
                delegate.notifyConnected(entry.getKey(), device, device.getName());
              }
              break;
            }
          }
        } catch (RemoteException e) {
          loge(TAG, "Error while notify remote device connected.", e);
        }
      }

      @Override
      public void onRemoteDeviceDisconnected(BluetoothDevice device) {
        logd(TAG, "Remote device disconnected");
        if (delegate == null) {
          logw(TAG, "Remote device disconnected with no delegate set");
          activeConnections.values().removeIf(value -> value == manager);
          return;
        }
        IConnectedDeviceSppDelegate localDelegate = delegate;

        for (Connection connection : activeConnections.keySet()) {
          if (activeConnections.get(connection) != manager) {
            continue;
          }
          try {
            // TODO(b/170164323) Need to handle the service disconnect situation.
            localDelegate.notifyError(connection);
          } catch (RemoteException e) {
            loge(TAG, "Error while notify remote device disconnected.", e);
          }
          break;
        }
        activeConnections.values().removeIf(value -> value == manager);
      }
    };
  }

  private SppManager.OnMessageReceivedListener generateMessageReceivedListener(
      Connection connection) {
    return (device, message) -> {
      if (delegate == null) {
        logw(TAG, "Received message from remote device with no delegate set");
        return;
      }

      try {
        delegate.notifyMessageReceived(connection, message);
      } catch (RemoteException e) {
        loge(TAG, "Error while notify remote message received.", e);
      }
    };
  }
}
