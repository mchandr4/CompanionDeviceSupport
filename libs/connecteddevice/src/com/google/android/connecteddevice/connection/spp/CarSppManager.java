package com.google.android.connecteddevice.connection.spp;

import static com.google.android.connecteddevice.model.Errors.DEVICE_ERROR_UNEXPECTED_DISCONNECTION;
import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.ParcelUuid;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.connection.AssociationCallback;
import com.google.android.connecteddevice.connection.AssociationSecureChannel;
import com.google.android.connecteddevice.connection.CarBluetoothManager;
import com.google.android.connecteddevice.connection.DeviceMessageStream;
import com.google.android.connecteddevice.connection.ReconnectSecureChannel;
import com.google.android.connecteddevice.connection.SecureChannel;
import com.google.android.connecteddevice.oob.OobChannel;
import com.google.android.connecteddevice.oob.OobConnectionManager;
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

  private OobConnectionManager oobConnectionManager;

  private AssociationCallback associationCallback;

  /**
   * Initialize a new instance of manager.
   *
   * @param sppBinder {@link ConnectedDeviceSppDelegateBinder} for establishing connection.
   * @param connectedDeviceStorage Shared {@link ConnectedDeviceStorage} for companion features.
   * @param packetMaxBytes Maximum size in bytes to write in one packet.
   * @param enableCompression Enable compression on outgoing messages.
   */
  public CarSppManager(
      @NonNull ConnectedDeviceSppDelegateBinder sppBinder,
      @NonNull ConnectedDeviceStorage connectedDeviceStorage,
      @NonNull UUID associationServiceUuid,
      int packetMaxBytes,
      boolean enableCompression) {
    super(connectedDeviceStorage, enableCompression);
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
  public AssociationCallback getAssociationCallback() {
    return associationCallback;
  }

  @Override
  public void setAssociationCallback(AssociationCallback callback) {
    associationCallback = callback;
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
    associationCallback = null;
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
      @NonNull String nameForAssociation, @NonNull AssociationCallback callback) {
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    if (adapter == null) {
      loge(TAG, "Bluetooth is unavailable on this device. Unable to start associating.");
      return;
    }
    if (reconnectDeviceId != null) {
      sppServiceBinder.unregisterConnectionCallback(UUID.fromString(reconnectDeviceId));
    }
    reset();
    associationCallback = callback;
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
        callback.onAssociationStartSuccess(/* deviceName= */ null);
      }
    } catch (RemoteException e) {
      callback.onAssociationStartFailure();
      loge(TAG, "Error when try to start associate with remote device.", e);
    }
  }

  /** Start the association with a new device using out of band verification code exchange */
  @Override
  public void startOutOfBandAssociation(
      @NonNull String nameForAssociation,
      @NonNull OobChannel oobChannel,
      @NonNull AssociationCallback callback) {

    logd(TAG, "Starting out of band association.");
    startAssociation(
        nameForAssociation,
        new AssociationCallback() {
          @Override
          public void onAssociationStartSuccess(String deviceName) {
            associationCallback = callback;
            boolean success = oobConnectionManager.startOobExchange(oobChannel);
            if (!success) {
              callback.onAssociationStartFailure();
              return;
            }
            callback.onAssociationStartSuccess(deviceName);
          }

          @Override
          public void onAssociationStartFailure() {
            callback.onAssociationStartFailure();
          }
        });
    oobConnectionManager = new OobConnectionManager();
  }

  private void onDeviceConnected(Connection connection, boolean isReconnect) {
    onDeviceConnected(connection, isReconnect, /* isOob= */ false);
  }

  private void onDeviceConnected(Connection connection, boolean isReconnect, boolean isOob) {
    currentConnection = connection;
    currentPendingConnection = null;
    EventLog.onDeviceConnected();
    BluetoothDevice device = connection.getRemoteDevice();
    setClientDeviceAddress(device.getAddress());
    setClientDeviceName(connection.getRemoteDeviceName());
    DeviceMessageStream secureStream =
        new SppDeviceMessageStream(sppServiceBinder, connection, packetMaxBytes);
    secureStream.setMessageReceivedErrorListener(
        exception -> {
          disconnectWithError("Error occurred in stream: " + exception.getMessage(), exception);
        });
    SecureChannel secureChannel;
    if (isReconnect) {
      secureChannel =
          new ReconnectSecureChannel(
              secureStream, storage, reconnectDeviceId, /* expectedChallengeResponse= */ null);
    } else if (isOob) {
      // TODO(b/160901821): Integrate Oob with Spp channel
      loge(TAG, "Oob verification is currently not available for Spp");
      return;
    } else {
      secureChannel = new AssociationSecureChannel(secureStream, storage);
    }
    secureChannel.registerCallback(secureChannelCallback);
    ConnectedRemoteDevice connectedDevice = new ConnectedRemoteDevice(device, /* gatt= */ null);
    connectedDevice.secureChannel = secureChannel;
    addConnectedDevice(connectedDevice);
    if (isReconnect) {
      setDeviceIdAndNotifyCallbacks(reconnectDeviceId);
      reconnectDeviceId = null;
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
          ConnectedRemoteDevice connectedDevice = getConnectedDevice(connection.getRemoteDevice());
          // Reset before invoking callbacks to avoid a race condition with reconnect
          // logic.
          reset();
          String deviceId = connectedDevice == null ? reconnectDeviceId : connectedDevice.deviceId;
          if (deviceId != null) {
            logd(TAG, "Connected device " + deviceId + " disconnected.");
            callbacks.invoke(callback -> callback.onDeviceDisconnected(deviceId));
          }
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
                    if (associationCallback == null) {
                      loge(TAG, "No valid callback for association.");
                      return;
                    }
                    associationCallback.onVerificationCodeAvailable(code);
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
            associationCallback.onAssociationError(DEVICE_ERROR_UNEXPECTED_DISCONNECTION);
          }
          ConnectedRemoteDevice connectedDevice = getConnectedDevice(connection.getRemoteDevice());
          // Reset before invoking callbacks to avoid a race condition with reconnect
          // logic.
          reset();
          if (connectedDevice != null && connectedDevice.deviceId != null) {
            callbacks.invoke(callback -> callback.onDeviceDisconnected(connectedDevice.deviceId));
          }
        }
      };
}
