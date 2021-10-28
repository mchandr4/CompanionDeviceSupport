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

package com.google.android.connecteddevice.connection.spp;

import static com.google.android.connecteddevice.model.Errors.DEVICE_ERROR_UNEXPECTED_DISCONNECTION;
import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.bluetooth.BluetoothDevice;
import android.os.ParcelUuid;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.connection.AssociationCallback;
import com.google.android.connecteddevice.connection.AssociationSecureChannel;
import com.google.android.connecteddevice.connection.CarBluetoothManager;
import com.google.android.connecteddevice.connection.ConnectionResolver;
import com.google.android.connecteddevice.connection.DeviceMessageStream;
import com.google.android.connecteddevice.connection.ReconnectSecureChannel;
import com.google.android.connecteddevice.model.OobData;
import com.google.android.connecteddevice.model.StartAssociationResponse;
import com.google.android.connecteddevice.oob.BluetoothRfcommChannel;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage;
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder;
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder.OnErrorListener;
import com.google.android.connecteddevice.transport.spp.Connection;
import com.google.android.connecteddevice.transport.spp.PendingConnection;
import com.google.android.connecteddevice.transport.spp.PendingConnection.OnConnectedListener;
import com.google.android.connecteddevice.transport.spp.PendingConnection.OnConnectionErrorListener;
import com.google.android.connecteddevice.util.EventLog;
import com.google.common.base.Objects;
import java.util.UUID;

/**
 * Communication manager that allows for targeted connections to a specific device from the car
 * using {@link ConnectedDeviceSppDelegateBinder} .
 */
public class CarSppManager extends CarBluetoothManager {

  private static final String TAG = "CarSppManager";

  private final ConnectedDeviceSppDelegateBinder sppServiceBinder;

  private final UUID associationServiceUuid;

  private final int packetMaxBytes;

  private String reconnectDeviceId;

  @VisibleForTesting Connection currentConnection;

  private PendingConnection currentPendingConnection;

  /**
   * Initialize a new instance of manager.
   *
   * @param sppBinder {@link ConnectedDeviceSppDelegateBinder} for establishing connection.
   * @param connectedDeviceStorage Shared {@link ConnectedDeviceStorage} for companion features.
   * @param packetMaxBytes Maximum size in bytes to write in one packet.
   * @param enableCompression Enable compression on outgoing messages.
   * @param isCapabilitiesEligible Association should attempt a capabilities exchange.
   */
  public CarSppManager(
      @NonNull ConnectedDeviceSppDelegateBinder sppBinder,
      @NonNull ConnectedDeviceStorage connectedDeviceStorage,
      @NonNull UUID associationServiceUuid,
      int packetMaxBytes,
      boolean enableCompression,
      boolean isCapabilitiesEligible) {
    super(
        connectedDeviceStorage,
        enableCompression,
        new BluetoothRfcommChannel(sppBinder),
        isCapabilitiesEligible);
    this.sppServiceBinder = sppBinder;
    this.associationServiceUuid = associationServiceUuid;
    this.packetMaxBytes = packetMaxBytes;
  }

  @Override
  public void stop() {
    super.stop();
    reset();
  }

  @Override
  public void disconnectDevice(@NonNull String deviceId) {
    ConnectedRemoteDevice connectedDevice = getConnectedDevice();
    if (connectedDevice == null || !deviceId.equals(connectedDevice.deviceId)) {
      return;
    }
    reset();
  }

  @Override
  public void initiateConnectionToDevice(@NonNull UUID deviceId) {
    logd(TAG, "Start spp reconnection listening for device with id: " + deviceId);
    currentConnection = null;
    reconnectDeviceId = deviceId.toString();

    sppServiceBinder.unregisterConnectionCallback(associationServiceUuid);
    sppServiceBinder.registerConnectionCallback(deviceId, reconnectOnErrorListener);
    try {
      PendingConnection pendingConnection =
          sppServiceBinder.connectAsServer(deviceId, /* isSecure= */ true);

      if (pendingConnection == null) {
        return;
      }
      currentPendingConnection = pendingConnection;
      pendingConnection
          .setOnConnectedListener(reconnectOnConnectedListener)
          .setOnConnectionErrorListener(onConnectionErrorListener);
    } catch (RemoteException e) {
      loge(TAG, "Error when start connection with remote device as server.", e);
    }
  }

  @Override
  public void reset() {
    super.reset();
    reconnectDeviceId = null;
    if (currentConnection != null) {
      try {
        sppServiceBinder.disconnect(currentConnection);
      } catch (RemoteException e) {
        loge(TAG, "Error when try to disconnect with remote device.", e);
      }
      currentConnection = null;
    }

    if (currentPendingConnection != null) {
      try {
        sppServiceBinder.cancelConnectionAttempt(currentPendingConnection);
      } catch (RemoteException e) {
        loge(TAG, "Error when try to disconnect with remote device.", e);
      }
      currentPendingConnection = null;
    }
  }

  /** Start the association by listening to incoming connect request. */
  @Override
  public void startAssociation(
      @NonNull byte[] nameForAssociation, @NonNull AssociationCallback callback) {
    if (reconnectDeviceId != null) {
      sppServiceBinder.unregisterConnectionCallback(UUID.fromString(reconnectDeviceId));
    }
    reset();
    setAssociationCallback(callback);
    sppServiceBinder.registerConnectionCallback(associationServiceUuid, associationOnErrorListener);
    try {
      PendingConnection pendingConnection =
          sppServiceBinder.connectAsServer(associationServiceUuid, /* isSecure= */ true);
      if (pendingConnection == null) {
        callback.onAssociationStartFailure();
      } else {
        currentPendingConnection = pendingConnection;
        pendingConnection
            .setOnConnectedListener(associationOnConnectedListener)
            .setOnConnectionErrorListener(onConnectionErrorListener);
        callback.onAssociationStartSuccess(
            new StartAssociationResponse(
                /* oobData= */ new OobData(new byte[0], new byte[0], new byte[0]),
                /* deviceIdentifier= */ new byte[0],
                /* deviceName= */ ""));
      }
    } catch (RemoteException e) {
      callback.onAssociationStartFailure();
      loge(TAG, "Error when try to start associate with remote device.", e);
    }
  }

  private void onDeviceConnected(Connection connection, boolean isReconnect) {
    currentConnection = connection;
    currentPendingConnection = null;
    EventLog.onDeviceConnected();
    BluetoothDevice device = connection.getRemoteDevice();
    setClientDeviceAddress(device.getAddress());
    setClientDeviceName(connection.getRemoteDeviceName());
    DeviceMessageStream secureStream =
        new SppDeviceMessageStream(sppServiceBinder, connection, packetMaxBytes);
    secureStream.setMessageReceivedErrorListener(
        exception ->
          disconnectWithError("Error occurred in stream: " + exception.getMessage(), exception));

    ConnectedRemoteDevice connectedDevice = new ConnectedRemoteDevice(device, /* gatt= */ null);

    if (isReconnect) {
      // Capabilities are only exchanged during association so isCapabilitiesEligible is always
      // false here.
      ConnectionResolver connectionResolver =
          new ConnectionResolver(secureStream, /* isCapabilitiesEligible= */ false);
      connectionResolver.resolveConnection(
          (resolvedConnection) -> {
            ReconnectSecureChannel secureChannel =
                new ReconnectSecureChannel(
                    secureStream,
                    storage,
                    reconnectDeviceId,
                    /* expectedChallengeResponse= */ null);
            secureChannel.registerCallback(secureChannelCallback);
            connectedDevice.secureChannel = secureChannel;
            addConnectedDevice(connectedDevice);
            setDeviceIdAndNotifyCallbacks(reconnectDeviceId);
            reconnectDeviceId = null;
          });
    } else {
      onDeviceConnectedForAssociation(secureStream, connectedDevice);
    }
  }

  private final OnConnectedListener reconnectOnConnectedListener =
      new OnConnectedListener() {
        @Override
        public void onConnected(
            UUID uuid, BluetoothDevice remoteDevice, boolean isSecure, String deviceName) {
          Connection connection =
              new Connection(new ParcelUuid(uuid), remoteDevice, isSecure, deviceName);
          onDeviceConnected(connection, /* isReconnect= */ true);
        }
      };

  @VisibleForTesting
  final OnErrorListener reconnectOnErrorListener =
      new OnErrorListener() {

        @Override
        public void onError(Connection connection) {
          if (!Objects.equal(currentConnection, connection)) {
            loge(TAG, "Receive connection error callback on a unrecognized connection, ignored.");
            return;
          }
          currentConnection = null;
          reset();
        }
      };

  private final OnConnectedListener associationOnConnectedListener =
      new OnConnectedListener() {
        @Override
        public void onConnected(
            UUID uuid, BluetoothDevice remoteDevice, boolean isSecure, String deviceName) {
          Connection connection =
              new Connection(new ParcelUuid(uuid), remoteDevice, isSecure, deviceName);
          onDeviceConnected(connection, /* isReconnect= */ false);
          ConnectedRemoteDevice connectedDevice = getConnectedDevice();
          if (connectedDevice == null || connectedDevice.secureChannel == null) {
            loge(TAG, "No connected device or secure channel found when trying to associate.");
            return;
          }
          ((AssociationSecureChannel) connectedDevice.secureChannel)
              .setShowVerificationCodeListener(
                  code -> {
                    if (!isAssociating()) {
                      loge(TAG, "No valid callback for association.");
                      return;
                    }
                    getAssociationCallback().onVerificationCodeAvailable(code);
                  });
        }
      };

  final OnConnectionErrorListener onConnectionErrorListener =
      new OnConnectionErrorListener() {
        @Override
        public void onConnectionError() {
          loge(TAG, "Connection attempt failed when performing a server role.");
        }
      };

  @VisibleForTesting
  final OnErrorListener associationOnErrorListener =
      new OnErrorListener() {

        @Override
        public void onError(Connection connection) {
          if (!Objects.equal(currentConnection, connection)) {
            loge(TAG, "Receive connection error callback on a unrecognized connection, ignored.");
            return;
          }
          currentConnection = null;
          if (isAssociating()) {
            getAssociationCallback().onAssociationError(DEVICE_ERROR_UNEXPECTED_DISCONNECTION);
          } else {
            loge(TAG, "Encounter association error with no association callback registered.");
          }
          reset();
        }
      };
}
