package com.google.android.connecteddevice.api;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.RemoteException;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.util.Logger;
import java.time.Duration;
import java.util.List;

/**
 * Base class for a feature that must bind to {@code ConnectedDeviceService}. Callbacks are
 * registered automatically and events are forwarded to internal methods. Override these to add
 * custom logic for callback triggers.
 */
public abstract class RemoteFeature {

  private static final String TAG = "RemoteFeature";

  private static final String FULLY_QUALIFIED_SERVICE_NAME =
      "com.google.android.connecteddevice.service.ConnectedDeviceService";

  /**
   * When a client calls {@link Context#bindService(Intent, ServiceConnection, int)} to get the
   * IConnectedDeviceManager, this action is required in the param {@link Intent}.
   */
  public static final String ACTION_BIND_REMOTE_FEATURE =
      "com.google.android.connecteddevice.api.BIND_REMOTE_FEATURE";

  /** Intent action used to request a device be associated. */
  public static final String ACTION_ASSOCIATION_SETTING =
      "com.google.android.connecteddevice.api.ASSOCIATION_ACTIVITY";

  /** Data name for associated device. */
  public static final String ASSOCIATED_DEVICE_DATA_NAME_EXTRA =
      "com.google.android.connecteddevice.api.ASSOCIATED_DEVICE";

  private static final Duration BIND_RETRY_DURATION = Duration.ofSeconds(1);

  private static final int MAX_BIND_ATTEMPTS = 3;

  private final Context context;

  private final ParcelUuid featureId;

  private IConnectedDeviceManager connectedDeviceManager;

  private int bindAttempts;

  protected RemoteFeature(@NonNull Context context, @NonNull ParcelUuid featureId) {
    this.context = context;
    this.featureId = featureId;
  }

  /** Start setup process and begin binding to {@code ConnectedDeviceService}. */
  @CallSuper
  public void start() {
    bindAttempts = 0;
    bindToService();
  }

  /** Called when the hosting service is being destroyed. Cleans up internal feature logic. */
  @CallSuper
  public void stop() {
    if (connectedDeviceManager == null) {
      return;
    }
    try {
      connectedDeviceManager.unregisterConnectionCallback(connectionCallback);
      for (ConnectedDevice device : connectedDeviceManager.getActiveUserConnectedDevices()) {
        connectedDeviceManager.unregisterDeviceCallback(device, featureId, deviceCallback);
      }
      connectedDeviceManager.unregisterDeviceAssociationCallback(deviceAssociationCallback);
      connectedDeviceManager.unregisterOnLogRequestedListener(
          Logger.getLogger().getLoggerId(), onLogRequestedListener);
    } catch (RemoteException e) {
      loge(TAG, "Error while stopping remote feature.", e);
    }
    context.unbindService(serviceConnection);
  }

  /** Return the {@link Context} registered with the feature. */
  @NonNull
  public Context getContext() {
    return context;
  }

  /**
   * Return the {@link IConnectedDeviceManager} bound with the feature. Returns {@code null} if
   * binding has not completed yet.
   */
  @Nullable
  public IConnectedDeviceManager getConnectedDeviceManager() {
    return connectedDeviceManager;
  }

  /** Return the {@link ParcelUuid} feature id registered for the feature. */
  @NonNull
  public ParcelUuid getFeatureId() {
    return featureId;
  }

  /** Securely send message to a device. */
  public void sendMessageSecurely(@NonNull String deviceId, @NonNull byte[] message) {
    if (connectedDeviceManager == null) {
      loge(TAG, "Unable to send message, ConnectedDeviceManager not actively connected.");
      return;
    }
    ConnectedDevice device = getConnectedDeviceById(deviceId);
    if (device == null) {
      loge(
          TAG,
          "No matching device found with id "
              + deviceId
              + " when trying to send "
              + "secure message.");
      onMessageFailedToSend(deviceId, message, false);
      return;
    }

    sendMessageSecurely(device, message);
  }

  /** Securely send message to a device. */
  public void sendMessageSecurely(@NonNull ConnectedDevice device, @NonNull byte[] message) {
    if (connectedDeviceManager == null) {
      loge(TAG, "Unable to send message, ConnectedDeviceManager not actively connected.");
      return;
    }
    try {
      getConnectedDeviceManager().sendMessageSecurely(device, getFeatureId(), message);
    } catch (RemoteException e) {
      loge(TAG, "Error while sending secure message.", e);
      onMessageFailedToSend(device.getDeviceId(), message, true);
    }
  }

  /** Send a message to a device without encryption. */
  public void sendMessageUnsecurely(@NonNull String deviceId, @NonNull byte[] message) {
    if (connectedDeviceManager == null) {
      loge(TAG, "Unable to send message, ConnectedDeviceManager not actively connected.");
      return;
    }
    ConnectedDevice device = getConnectedDeviceById(deviceId);
    if (device == null) {
      loge(
          TAG,
          "No matching device found with id "
              + deviceId
              + " when trying to send "
              + "unsecure message.");
      onMessageFailedToSend(deviceId, message, false);
      return;
    }
  }

  /** Send a message to a device without encryption. */
  public void sendMessageUnsecurely(@NonNull ConnectedDevice device, @NonNull byte[] message) {
    if (connectedDeviceManager == null) {
      loge(TAG, "Unable to send message, ConnectedDeviceManager not actively connected.");
      return;
    }
    try {
      getConnectedDeviceManager().sendMessageUnsecurely(device, getFeatureId(), message);
    } catch (RemoteException e) {
      loge(TAG, "Error while sending unsecure message.", e);
      onMessageFailedToSend(device.getDeviceId(), message, true);
    }
  }

  /**
   * Return the {@link ConnectedDevice} with a matching device id for the currently active user.
   * Returns {@code null} if no match found.
   */
  @Nullable
  public ConnectedDevice getConnectedDeviceById(@NonNull String deviceId) {
    if (connectedDeviceManager == null) {
      loge(
          TAG,
          "Unable to get connected device. ConnectedDeviceManager not actively " + "connected.");
      return null;
    }
    List<ConnectedDevice> connectedDevices;
    try {
      connectedDevices = getConnectedDeviceManager().getActiveUserConnectedDevices();
    } catch (RemoteException e) {
      loge(TAG, "Exception while retrieving connected devices.", e);
      return null;
    }

    for (ConnectedDevice device : connectedDevices) {
      if (device.getDeviceId().equals(deviceId)) {
        return device;
      }
    }

    return null;
  }

  // These can be overridden to perform custom actions.

  /** Called when a new {@link ConnectedDevice} is connected. */
  protected void onDeviceConnected(@NonNull ConnectedDevice device) {}

  /** Called when a {@link ConnectedDevice} disconnects. */
  protected void onDeviceDisconnected(@NonNull ConnectedDevice device) {}

  /** Called when a secure channel has been established with a {@link ConnectedDevice}. */
  protected void onSecureChannelEstablished(@NonNull ConnectedDevice device) {}

  /**
   * Called when a message fails to send to a device.
   *
   * @param deviceId Id of the device the message failed to send to.
   * @param message Message to send.
   * @param isTransient {@code true} if cause of failure is transient and can be retried. {@code
   *     false} if failure is permanent.
   */
  protected void onMessageFailedToSend(
      @NonNull String deviceId, @NonNull byte[] message, boolean isTransient) {}

  /** Called when a new {@link byte[]} message is received for this feature. */
  protected void onMessageReceived(@NonNull ConnectedDevice device, @NonNull byte[] message) {}

  /** Called when an error has occurred with the connection. */
  protected void onDeviceError(@NonNull ConnectedDevice device, int error) {}

  /** Called when a new {@link AssociatedDevice} is added for the given user. */
  protected void onAssociatedDeviceAdded(@NonNull AssociatedDevice device) {}

  /** Called when an {@link AssociatedDevice} is removed for the given user. */
  protected void onAssociatedDeviceRemoved(@NonNull AssociatedDevice device) {}

  /** Called when an {@link AssociatedDevice} is updated for the given user. */
  protected void onAssociatedDeviceUpdated(@NonNull AssociatedDevice device) {}

  private void bindToService() {
    String packageName = context.getApplicationContext().getPackageName();
    Intent intent = new Intent();
    intent.setComponent(new ComponentName(packageName, FULLY_QUALIFIED_SERVICE_NAME));
    intent.setAction(ACTION_BIND_REMOTE_FEATURE);
    boolean success = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    if (!success) {
      bindAttempts++;
      if (bindAttempts > MAX_BIND_ATTEMPTS) {
        loge(
            TAG,
            "Failed to bind to ConnectedDeviceService after "
                + bindAttempts
                + " attempts. Aborting.");
        return;
      }
      logw(TAG, "Unable to bind to ConnectedDeviceService. Trying again.");
      new Handler(Looper.getMainLooper())
          .postDelayed(this::bindToService, BIND_RETRY_DURATION.toMillis());
    }
  }

  private final ServiceConnection serviceConnection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
          connectedDeviceManager = IConnectedDeviceManager.Stub.asInterface(service);
          try {
            connectedDeviceManager.registerActiveUserConnectionCallback(connectionCallback);
            connectedDeviceManager.registerDeviceAssociationCallback(deviceAssociationCallback);
            connectedDeviceManager.registerOnLogRequestedListener(
                Logger.getLogger().getLoggerId(), onLogRequestedListener);
            logd(TAG, "Successfully bound to ConnectedDeviceManager.");
            List<ConnectedDevice> activeUserConnectedDevices =
                connectedDeviceManager.getActiveUserConnectedDevices();
            for (ConnectedDevice device : activeUserConnectedDevices) {
              if (device.hasSecureChannel()) {
                onSecureChannelEstablished(device);
              }
              connectedDeviceManager.registerDeviceCallback(device, featureId, deviceCallback);
            }
          } catch (RemoteException e) {
            loge(TAG, "Error while inspecting connected devices.", e);
          }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
          logd(TAG, "Disconnected from ConnectedDeviceManager.");
          connectedDeviceManager = null;
          stop();
        }
      };

  private final IConnectionCallback connectionCallback =
      new IConnectionCallback.Stub() {
        @Override
        public void onDeviceConnected(ConnectedDevice connectedDevice) throws RemoteException {
          connectedDeviceManager.registerDeviceCallback(
              connectedDevice, featureId, deviceCallback);
          RemoteFeature.this.onDeviceConnected(connectedDevice);
        }

        @Override
        public void onDeviceDisconnected(ConnectedDevice connectedDevice) throws RemoteException {
          connectedDeviceManager.unregisterDeviceCallback(
              connectedDevice, featureId, deviceCallback);
          RemoteFeature.this.onDeviceDisconnected(connectedDevice);
        }
      };

  private final IDeviceCallback deviceCallback =
      new IDeviceCallback.Stub() {
        @Override
        public void onSecureChannelEstablished(ConnectedDevice connectedDevice) {
          RemoteFeature.this.onSecureChannelEstablished(connectedDevice);
        }

        @Override
        public void onMessageReceived(ConnectedDevice connectedDevice, byte[] message) {
          RemoteFeature.this.onMessageReceived(connectedDevice, message);
        }

        @Override
        public void onDeviceError(ConnectedDevice connectedDevice, int error) {
          RemoteFeature.this.onDeviceError(connectedDevice, error);
        }
      };

  private final IDeviceAssociationCallback deviceAssociationCallback =
      new IDeviceAssociationCallback.Stub() {
        @Override
        public void onAssociatedDeviceAdded(AssociatedDevice device) {
          RemoteFeature.this.onAssociatedDeviceAdded(device);
        }

        @Override
        public void onAssociatedDeviceRemoved(AssociatedDevice device) {
          RemoteFeature.this.onAssociatedDeviceRemoved(device);
        }

        @Override
        public void onAssociatedDeviceUpdated(AssociatedDevice device) {
          RemoteFeature.this.onAssociatedDeviceUpdated(device);
        }
      };

  private final IOnLogRequestedListener onLogRequestedListener =
      new IOnLogRequestedListener.Stub() {
        @Override
        public void onLogRecordsRequested() {
          Logger logger = Logger.getLogger();
          try {
            connectedDeviceManager.processLogRecords(logger.getLoggerId(), logger.toByteArray());
          } catch (RemoteException exception) {
            loge(TAG, "Failed to send log records for logger" + logger + ".", exception);
          }
        }
      };
}
