package com.google.android.connecteddevice.api;

import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.os.RemoteException;
import com.google.android.connecteddevice.ConnectedDeviceManager;
import com.google.android.connecteddevice.ConnectedDeviceManager.DeviceAssociationCallback;
import com.google.android.connecteddevice.connection.AssociationCallback;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.OobEligibleDevice;
import com.google.android.connecteddevice.util.RemoteCallbackBinder;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Binder for exposing connected device association actions to internal features. */
public class AssociationBinder extends IAssociatedDeviceManager.Stub {

  private static final String TAG = "AssociationBinder";

  private final ConnectedDeviceManager connectedDeviceManager;
  /**
   * {@link #remoteAssociationCallbackBinder} and {@link #iAssociationCallback} can only be
   * modified together through {@link #setAssociationCallback(IAssociationCallback)} or {@link
   * #clearAssociationCallback()} from the association thread.
   */
  private RemoteCallbackBinder remoteAssociationCallbackBinder;

  private IAssociationCallback iAssociationCallback;
  /**
   * {@link #remoteDeviceAssociationCallbackBinder} and {@link #deviceAssociationCallback} can
   * only be modified together through {@link
   * #setDeviceAssociationCallback(IDeviceAssociationCallback)} or {@link
   * #clearDeviceAssociationCallback()} from the association thread.
   */
  private RemoteCallbackBinder remoteDeviceAssociationCallbackBinder;

  private DeviceAssociationCallback deviceAssociationCallback;

  /**
   * {@link #remoteConnectionCallbackBinder} and {@link #connectionCallback} can only be modified
   * together through {@link #setConnectionCallback(IConnectionCallback)} or {@link
   * #clearConnectionCallback()} from the association thread.
   */
  private RemoteCallbackBinder remoteConnectionCallbackBinder;

  private ConnectedDeviceManager.ConnectionCallback connectionCallback;

  private final Executor executor = Executors.newSingleThreadExecutor();

  public AssociationBinder(ConnectedDeviceManager connectedDeviceManager) {
    this.connectedDeviceManager = connectedDeviceManager;
  }

  @Override
  public void setAssociationCallback(IAssociationCallback callback) {
    if (callback == null) {
      return;
    }
    remoteAssociationCallbackBinder =
        new RemoteCallbackBinder(callback.asBinder(), iBinder -> stopAssociation());
    iAssociationCallback = callback;
  }

  @Override
  public void clearAssociationCallback() {
    iAssociationCallback = null;
    if (remoteAssociationCallbackBinder == null) {
      return;
    }
    remoteAssociationCallbackBinder.cleanUp();
    remoteAssociationCallbackBinder = null;
  }

  @Override
  public void startAssociation() {
    connectedDeviceManager.startAssociation(associationCallback);
  }

  @Override
  public void startOobAssociation(OobEligibleDevice eligibleDevice) {
    if (eligibleDevice == null) {
      logw(TAG, "Attempted to start OOB association with null device. Ignoring.");
      return;
    }

    connectedDeviceManager.startOutOfBandAssociation(
        eligibleDevice, associationCallback);
  }

  @Override
  public void stopAssociation() {
    connectedDeviceManager.stopAssociation(associationCallback);
  }

  @Override
  public List<AssociatedDevice> getActiveUserAssociatedDevices() {
    return connectedDeviceManager.getActiveUserAssociatedDevices();
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
  public void setDeviceAssociationCallback(IDeviceAssociationCallback callback) {
    deviceAssociationCallback =
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
    remoteDeviceAssociationCallbackBinder =
        new RemoteCallbackBinder(callback.asBinder(), iBinder -> clearDeviceAssociationCallback());
    connectedDeviceManager.registerDeviceAssociationCallback(
        deviceAssociationCallback, executor);
  }

  @Override
  public void clearDeviceAssociationCallback() {
    if (deviceAssociationCallback == null) {
      return;
    }
    connectedDeviceManager.unregisterDeviceAssociationCallback(deviceAssociationCallback);
    deviceAssociationCallback = null;
    remoteDeviceAssociationCallbackBinder.cleanUp();
    remoteDeviceAssociationCallbackBinder = null;
  }

  @Override
  public List<ConnectedDevice> getActiveUserConnectedDevices() {
    return connectedDeviceManager.getActiveUserConnectedDevices();
  }

  @Override
  public void setConnectionCallback(IConnectionCallback callback) {
    connectionCallback =
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
    remoteConnectionCallbackBinder =
        new RemoteCallbackBinder(callback.asBinder(), iBinder -> clearConnectionCallback());
    connectedDeviceManager.registerActiveUserConnectionCallback(
        connectionCallback, executor);
  }

  @Override
  public void clearConnectionCallback() {
    if (connectionCallback == null) {
      return;
    }
    connectedDeviceManager.unregisterConnectionCallback(connectionCallback);
    remoteConnectionCallbackBinder.cleanUp();
    remoteConnectionCallbackBinder = null;
    connectionCallback = null;
  }

  @Override
  public void enableAssociatedDeviceConnection(String deviceId) {
    connectedDeviceManager.enableAssociatedDeviceConnection(deviceId);
  }

  @Override
  public void disableAssociatedDeviceConnection(String deviceId) {
    connectedDeviceManager.disableAssociatedDeviceConnection(deviceId);
  }

  private final AssociationCallback associationCallback =
      new AssociationCallback() {

        @Override
        public void onAssociationStartSuccess(String deviceName) {
          if (iAssociationCallback == null) {
            loge(
                TAG,
                "No IAssociationCallback has been set, ignoring " + "onAssociationStartSuccess.");
            return;
          }
          try {
            iAssociationCallback.onAssociationStartSuccess(deviceName);
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
        }
      };
}
