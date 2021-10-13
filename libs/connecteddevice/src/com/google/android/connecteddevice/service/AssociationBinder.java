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
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.ConnectedDeviceManager;
import com.google.android.connecteddevice.ConnectedDeviceManager.ConnectionCallback;
import com.google.android.connecteddevice.ConnectedDeviceManager.DeviceAssociationCallback;
import com.google.android.connecteddevice.api.IAssociatedDeviceManager;
import com.google.android.connecteddevice.api.IAssociationCallback;
import com.google.android.connecteddevice.api.IConnectionCallback;
import com.google.android.connecteddevice.api.IDeviceAssociationCallback;
import com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener;
import com.google.android.connecteddevice.connection.AssociationCallback;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.StartAssociationResponse;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage.OnAssociatedDevicesRetrievedListener;
import com.google.android.connecteddevice.util.RemoteCallbackBinder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Binder for exposing associated device actions to internal features. */
public class AssociationBinder extends IAssociatedDeviceManager.Stub {

  private static final String TAG = "AssociationBinder";

  private final ConnectedDeviceManager connectedDeviceManager;

  // Registered association callback
  private IAssociationCallback iAssociationCallback;

  // aidl callback binder -> device association callback
  // Need to maintain a mapping in order to support unregistering callbacks.
  private final Map<IBinder, DeviceAssociationCallback> deviceAssociationCallbacks =
      new ConcurrentHashMap<>();

  // aidl callback binder -> connection callback
  // Need to maintain a mapping in order to support unregistering callbacks.
  private final Map<IBinder, ConnectionCallback> connectionCallbacks = new ConcurrentHashMap<>();

  private final Executor executor = Executors.newSingleThreadExecutor();
  private final Set<RemoteCallbackBinder> callbackBinders = new CopyOnWriteArraySet<>();

  public AssociationBinder(ConnectedDeviceManager connectedDeviceManager) {
    this.connectedDeviceManager = connectedDeviceManager;
  }

  @Override
  public void startAssociation(IAssociationCallback callback) {
    RemoteCallbackBinder remoteBinder =
        new RemoteCallbackBinder(callback.asBinder(), iBinder -> stopAssociation());
    callbackBinders.add(remoteBinder);
    iAssociationCallback = callback;
    connectedDeviceManager.startAssociation(associationCallback);
  }

  @Override
  public void stopAssociation() {
    if (iAssociationCallback == null) {
      return;
    }
    RemoteCallbackBinder remoteBinder = findRemoteCallbackBinder(iAssociationCallback.asBinder());
    if (remoteBinder == null) {
      return;
    }
    connectedDeviceManager.stopAssociation(associationCallback);
    resetAssociationCallback();
  }

  @Override
  public void retrievedActiveUserAssociatedDevices(
      IOnAssociatedDevicesRetrievedListener remoteListener) {
    OnAssociatedDevicesRetrievedListener listener =
        devices -> {
          try {
            remoteListener.onAssociatedDevicesRetrieved(devices);
          } catch (RemoteException exception) {
            loge(TAG, "onAssociatedDevicesRetrieved failed.", exception);
          }
        };
    connectedDeviceManager.retrieveActiveUserAssociatedDevices(listener);
  }

  @Override
  public void acceptVerification() {
    connectedDeviceManager.notifyOutOfBandAccepted();
  }

  @Override
  public void removeAssociatedDevice(String deviceId) {
    connectedDeviceManager.removeActiveUserAssociatedDevice(deviceId);
  }

  @Override
  public void registerDeviceAssociationCallback(IDeviceAssociationCallback callback) {
    DeviceAssociationCallback deviceAssociationCallback =
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
    connectedDeviceManager.registerDeviceAssociationCallback(deviceAssociationCallback, executor);
    RemoteCallbackBinder remoteBinder =
        new RemoteCallbackBinder(
            callback.asBinder(), iBinder -> unregisterDeviceAssociationCallback(callback));
    callbackBinders.add(remoteBinder);
    deviceAssociationCallbacks.put(callback.asBinder(), deviceAssociationCallback);
  }

  @Override
  public void unregisterDeviceAssociationCallback(IDeviceAssociationCallback callback) {
    RemoteCallbackBinder remoteBinder = findRemoteCallbackBinder(callback.asBinder());
    if (remoteBinder == null) {
      loge(
          TAG,
          "Failed to unregister DeviceAssociationCallback "
              + callback
              + ", it has not been registered.");
      return;
    }
    DeviceAssociationCallback deviceAssociationCallback =
        deviceAssociationCallbacks.remove(callback.asBinder());
    if (deviceAssociationCallback == null) {
      loge(
          TAG,
          "Failed to unregister DeviceAssociationCallback "
              + callback
              + ", it has not been registered.");
      return;
    }
    connectedDeviceManager.unregisterDeviceAssociationCallback(deviceAssociationCallback);
    remoteBinder.cleanUp();
    callbackBinders.remove(remoteBinder);
  }

  @Override
  public List<ConnectedDevice> getActiveUserConnectedDevices() {
    return connectedDeviceManager.getActiveUserConnectedDevices();
  }

  @Override
  public void registerConnectionCallback(IConnectionCallback callback) {
    ConnectionCallback connectionCallback =
        new ConnectedDeviceManager.ConnectionCallback() {
          @Override
          public void onDeviceConnected(ConnectedDevice device) {
            if (callback == null) {
              loge(TAG, "No IConnectionCallback has been set, ignoring " + "onDeviceConnected.");
              return;
            }
            try {
              callback.onDeviceConnected(device);
            } catch (RemoteException exception) {
              loge(TAG, "onDeviceConnected failed.", exception);
            }
          }

          @Override
          public void onDeviceDisconnected(ConnectedDevice device) {
            if (callback == null) {
              loge(TAG, "No IConnectionCallback has been set, ignoring " + "onDeviceConnected.");
              return;
            }
            try {
              callback.onDeviceDisconnected(device);
            } catch (RemoteException exception) {
              loge(TAG, "onDeviceDisconnected failed.", exception);
            }
          }
        };
    connectedDeviceManager.registerActiveUserConnectionCallback(connectionCallback, executor);
    RemoteCallbackBinder remoteBinder =
        new RemoteCallbackBinder(
            callback.asBinder(), iBinder -> unregisterConnectionCallback(callback));
    callbackBinders.add(remoteBinder);
    connectionCallbacks.put(callback.asBinder(), connectionCallback);
  }

  @Override
  public void unregisterConnectionCallback(@NonNull IConnectionCallback callback) {
    RemoteCallbackBinder remoteBinder = findRemoteCallbackBinder(callback.asBinder());
    if (remoteBinder == null) {
      loge(
          TAG,
          "Failed to unregister ConnectionCallback "
              + callback.asBinder()
              + ", it was not been registered.");
      return;
    }
    ConnectionCallback connectionCallback = connectionCallbacks.remove(callback.asBinder());
    if (connectionCallback == null) {
      loge(
          TAG,
          "Failed to unregister ConnectionCallback "
              + callback.asBinder()
              + ", it was not been registered.");
      return;
    }
    connectedDeviceManager.unregisterConnectionCallback(connectionCallback);
    remoteBinder.cleanUp();
    callbackBinders.remove(remoteBinder);
  }

  @Override
  public void enableAssociatedDeviceConnection(String deviceId) {
    connectedDeviceManager.enableAssociatedDeviceConnection(deviceId);
  }

  @Override
  public void disableAssociatedDeviceConnection(String deviceId) {
    connectedDeviceManager.disableAssociatedDeviceConnection(deviceId);
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

  private void resetAssociationCallback() {
    RemoteCallbackBinder remoteBinder = findRemoteCallbackBinder(iAssociationCallback.asBinder());
    if (remoteBinder != null) {
      remoteBinder.cleanUp();
      callbackBinders.remove(remoteBinder);
    }
    iAssociationCallback = null;
  }

  private final AssociationCallback associationCallback =
      new AssociationCallback() {

        @Override
        public void onAssociationStartSuccess(StartAssociationResponse response) {
          if (iAssociationCallback == null) {
            loge(
                TAG,
                "No IAssociationCallback has been set, ignoring " + "onAssociationStartSuccess.");
            return;
          }
          try {
            iAssociationCallback.onAssociationStartSuccess(response);
          } catch (RemoteException exception) {
            loge(TAG, "onAssociationStartSuccess failed.", exception);
          }
        }

        @Override
        public void onAssociationStartFailure() {
          if (iAssociationCallback == null) {
            loge(
                TAG,
                "No IAssociationCallback has been set, ignoring " + "onAssociationStartFailure.");
            return;
          }
          try {
            iAssociationCallback.onAssociationStartFailure();
          } catch (RemoteException exception) {
            loge(TAG, "onAssociationStartFailure failed.", exception);
          }
          resetAssociationCallback();
        }

        @Override
        public void onAssociationError(int error) {
          if (iAssociationCallback == null) {
            loge(
                TAG,
                "No IAssociationCallback has been set, ignoring "
                    + "onAssociationError: "
                    + error
                    + ".");
            return;
          }
          try {
            iAssociationCallback.onAssociationError(error);
          } catch (RemoteException exception) {
            loge(TAG, "onAssociationError failed. Error: " + error + "", exception);
          }
          resetAssociationCallback();
        }

        @Override
        public void onVerificationCodeAvailable(String code) {
          if (iAssociationCallback == null) {
            loge(
                TAG,
                "No IAssociationCallback has been set, ignoring "
                    + "onVerificationCodeAvailable, code: "
                    + code);
            return;
          }
          try {
            iAssociationCallback.onVerificationCodeAvailable(code);
          } catch (RemoteException exception) {
            loge(TAG, "onVerificationCodeAvailable failed. Code: " + code + "", exception);
          }
        }

        @Override
        public void onAssociationCompleted(String deviceId) {
          if (iAssociationCallback == null) {
            loge(
                TAG,
                "No IAssociationCallback has been set, ignoring "
                    + "onAssociationCompleted, deviceId: "
                    + deviceId);
            return;
          }
          try {
            iAssociationCallback.onAssociationCompleted();
          } catch (RemoteException exception) {
            loge(TAG, "onAssociationCompleted failed.", exception);
          }
          resetAssociationCallback();
        }
      };
}
