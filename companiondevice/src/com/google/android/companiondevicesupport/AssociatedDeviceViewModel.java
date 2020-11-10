package com.google.android.companiondevicesupport;

import static com.google.android.connecteddevice.api.RemoteFeature.ASSOCIATED_DEVICE_DATA_NAME_EXTRA;
import static com.google.android.connecteddevice.service.ConnectedDeviceService.ACTION_BIND_ASSOCIATION;
import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.app.ActivityManager;
import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.api.IAssociatedDeviceManager;
import com.google.android.connecteddevice.api.IAssociationCallback;
import com.google.android.connecteddevice.api.IConnectionCallback;
import com.google.android.connecteddevice.api.IDeviceAssociationCallback;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.service.ConnectedDeviceService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation {@link ViewModel} for sharing associated devices data between {@link
 * AssociatedDeviceDetailFragment} and {@link AssociationActivity}
 */
public class AssociatedDeviceViewModel extends AndroidViewModel {

  private static final String TAG = "AssociatedDeviceViewModel";

  private static final Duration DISCOVERABLE_DURATION = Duration.ofMinutes(2);

  /** States of association process. */
  public enum AssociationState {
    NONE,
    PENDING,
    STARTING,
    STARTED,
    COMPLETED,
    ERROR
  }

  private IAssociatedDeviceManager associatedDeviceManager;
  private List<AssociatedDevice> associatedDevices = new ArrayList<>();
  private List<ConnectedDevice> connectedDevices = new ArrayList<>();

  private final MutableLiveData<AssociatedDeviceDetails> deviceDetails =
      new MutableLiveData<>(null);
  private final MutableLiveData<String> advertisedCarName = new MutableLiveData<>(null);
  private final MutableLiveData<String> pairingCode = new MutableLiveData<>(null);
  private final MutableLiveData<AssociatedDevice> deviceToRemove = new MutableLiveData<>(null);
  private final MutableLiveData<Integer> bluetoothState =
      new MutableLiveData<>(BluetoothAdapter.STATE_OFF);
  private final MutableLiveData<AssociationState> associationState =
      new MutableLiveData<>(AssociationState.NONE);
  private final MutableLiveData<AssociatedDevice> removedDevice = new MutableLiveData<>(null);
  private final MutableLiveData<Boolean> isFinished = new MutableLiveData<>(false);

  public AssociatedDeviceViewModel(@NonNull Application application) {
    super(application);
    Intent intent = new Intent(getApplication(), ConnectedDeviceService.class);
    intent.setAction(ACTION_BIND_ASSOCIATION);
    getApplication().bindService(intent, connection, Context.BIND_AUTO_CREATE);
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    if (adapter != null) {
      bluetoothState.postValue(adapter.getState());
    }
  }

  @Override
  protected void onCleared() {
    super.onCleared();
    try {
      unregisterCallbacks();
    } catch (RemoteException e) {
      loge(TAG, "Error clearing registered callbacks. ", e);
    }
    getApplication().unbindService(connection);
    getApplication().unregisterReceiver(receiver);
    associatedDeviceManager = null;
  }

  /** Confirms that the pairing code matches. */
  public void acceptVerification() {
    pairingCode.postValue(null);
    try {
      associatedDeviceManager.acceptVerification();
    } catch (RemoteException e) {
      loge(TAG, "Error while accepting verification.", e);
    }
  }

  /** Stops association. */
  public void stopAssociation() {
    AssociationState state = associationState.getValue();
    if (state != AssociationState.STARTING && state != AssociationState.STARTED) {
      return;
    }
    advertisedCarName.postValue(null);
    pairingCode.postValue(null);
    try {
      associatedDeviceManager.stopAssociation();
    } catch (RemoteException e) {
      loge(TAG, "Error while stopping association process.", e);
    }
    associationState.postValue(AssociationState.NONE);
  }

  /** Retry association. */
  public void retryAssociation() {
    stopAssociation();
    startAssociation();
  }

  /** Select the current associated device as the device to remove. */
  public void selectCurrentDeviceToRemove() {
    deviceToRemove.postValue(getAssociatedDevice());
  }

  /** Remove the current associated device. */
  public void removeCurrentDevice() {
    AssociatedDevice device = getAssociatedDevice();
    if (device == null) {
      return;
    }
    try {
      associatedDeviceManager.removeAssociatedDevice(device.getDeviceId());
    } catch (RemoteException e) {
      loge(TAG, "Failed to remove associated device: " + device.getDeviceId(), e);
    }
  }

  /** Toggle connection on the current associated device. */
  public void toggleConnectionStatusForCurrentDevice() {
    AssociatedDevice device = getAssociatedDevice();
    if (device == null) {
      return;
    }
    try {
      if (device.isConnectionEnabled()) {
        associatedDeviceManager.disableAssociatedDeviceConnection(device.getDeviceId());
      } else {
        associatedDeviceManager.enableAssociatedDeviceConnection(device.getDeviceId());
      }
    } catch (RemoteException e) {
      loge(TAG, "Failed to toggle connection status for device: " + device.getDeviceId() + ".", e);
    }
  }

  /** Get the associated device details. */
  public MutableLiveData<AssociatedDeviceDetails> getDeviceDetails() {
    return deviceDetails;
  }

  /** Start feature activity for the current associated device. */
  public void startFeatureActivityForCurrentDevice(@NonNull String action) {
    AssociatedDevice device = getAssociatedDevice();
    if (device == null || action == null) {
      return;
    }
    Intent intent = new Intent(action);
    intent.putExtra(ASSOCIATED_DEVICE_DATA_NAME_EXTRA, device);
    getApplication().startActivityAsUser(intent, UserHandle.of(ActivityManager.getCurrentUser()));
  }

  /** Reset the value of {@link #associationState} to {@link AssociationState.NONE}. */
  public void resetAssociationState() {
    associationState.postValue(AssociationState.NONE);
  }

  /** Get the associated device to remove. The associated device could be null. */
  public LiveData<AssociatedDevice> getDeviceToRemove() {
    return deviceToRemove;
  }

  /** Get the name that is being advertised by the car. */
  public MutableLiveData<String> getAdvertisedCarName() {
    return advertisedCarName;
  }

  /** Get the generated pairing code. */
  public MutableLiveData<String> getPairingCode() {
    return pairingCode;
  }

  /** Value is {@code true} if the current associated device has been removed. */
  public MutableLiveData<AssociatedDevice> getRemovedDevice() {
    return removedDevice;
  }

  /** Get the current {@link AssociationState}. */
  public MutableLiveData<AssociationState> getAssociationState() {
    return associationState;
  }

  /** Get the current Bluetooth state. */
  public MutableLiveData<Integer> getBluetoothState() {
    return bluetoothState;
  }

  /** Value is {@code true} if IHU is not in association and has no associated device. */
  public MutableLiveData<Boolean> isFinished() {
    return isFinished;
  }

  protected void startAssociation() {
    if (associatedDeviceManager == null) {
      return;
    }
    associationState.postValue(AssociationState.PENDING);
    if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
      return;
    }
    try {
      boolean isSppEnabled =
          getApplication()
              .getApplicationContext()
              .getResources()
              .getBoolean(R.bool.enable_spp_support);
      if (isSppEnabled
          && BluetoothAdapter.getDefaultAdapter().getScanMode()
              != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(
            BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, (int) DISCOVERABLE_DURATION.getSeconds());
        getApplication()
            .startActivityAsUser(
                discoverableIntent, UserHandle.of(ActivityManager.getCurrentUser()));
      }

      associatedDeviceManager.startAssociation();

    } catch (RemoteException e) {
      loge(TAG, "Failed to start association .", e);
      associationState.postValue(AssociationState.ERROR);
    }
    associationState.postValue(AssociationState.STARTING);
  }

  protected IAssociatedDeviceManager getAssociatedDeviceManager() {
    return associatedDeviceManager;
  }

  private void updateDeviceDetails() {
    AssociatedDevice device = getAssociatedDevice();
    if (device == null) {
      return;
    }
    deviceDetails.postValue(new AssociatedDeviceDetails(getAssociatedDevice(), isConnected()));
  }

  @Nullable
  private AssociatedDevice getAssociatedDevice() {
    if (associatedDevices.isEmpty()) {
      return null;
    }
    return associatedDevices.get(0);
  }

  private boolean isConnected() {
    if (associatedDevices.isEmpty() || connectedDevices.isEmpty()) {
      return false;
    }
    String associatedDeviceId = associatedDevices.get(0).getDeviceId();
    String connectedDeviceId = connectedDevices.get(0).getDeviceId();
    return associatedDeviceId.equals(connectedDeviceId);
  }

  private void setAssociatedDevices(@NonNull List<AssociatedDevice> associatedDevices) {
    this.associatedDevices = associatedDevices;
    updateDeviceDetails();
  }

  private void setConnectedDevices(@NonNull List<ConnectedDevice> connectedDevices) {
    this.connectedDevices = connectedDevices;
    updateDeviceDetails();
  }

  private void addOrUpdateAssociatedDevice(@NonNull AssociatedDevice device) {
    associatedDevices.removeIf(d -> d.getDeviceId().equals(device.getDeviceId()));
    associatedDevices.add(device);
    updateDeviceDetails();
  }

  private void removeAssociatedDevice(AssociatedDevice device) {
    if (associatedDevices.removeIf(d -> d.getDeviceId().equals(device.getDeviceId()))) {
      removedDevice.postValue(device);
      deviceDetails.postValue(null);
    }
  }

  private void registerCallbacks() throws RemoteException {
    associatedDeviceManager.setAssociationCallback(associationCallback);
    associatedDeviceManager.setDeviceAssociationCallback(deviceAssociationCallback);
    associatedDeviceManager.setConnectionCallback(connectionCallback);
  }

  private void unregisterCallbacks() throws RemoteException {
    associatedDeviceManager.clearDeviceAssociationCallback();
    associatedDeviceManager.clearAssociationCallback();
    associatedDeviceManager.clearConnectionCallback();
  }

  private final ServiceConnection connection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
          associatedDeviceManager = IAssociatedDeviceManager.Stub.asInterface(service);
          try {
            registerCallbacks();
            setConnectedDevices(associatedDeviceManager.getActiveUserConnectedDevices());
            setAssociatedDevices(associatedDeviceManager.getActiveUserAssociatedDevices());
          } catch (RemoteException e) {
            loge(TAG, "Initial set failed onServiceConnected", e);
          }
          AssociationState state = associationState.getValue();
          if (associatedDevices.isEmpty()
              && state != AssociationState.STARTING
              && state != AssociationState.STARTED) {
            startAssociation();
          }
          logd(TAG, "Service connected:" + name.getClassName());
          IntentFilter filter = new IntentFilter();
          filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
          getApplication().registerReceiver(receiver, filter);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
          associatedDeviceManager = null;
          logd(TAG, "Service disconnected: " + name.getClassName());
          isFinished.postValue(true);
        }
      };

  private final IAssociationCallback associationCallback =
      new IAssociationCallback.Stub() {
        @Override
        public void onAssociationStartSuccess(String deviceName) {
          associationState.postValue(AssociationState.STARTED);
          advertisedCarName.postValue(deviceName);
        }

        @Override
        public void onAssociationStartFailure() {
          associationState.postValue(AssociationState.ERROR);
          loge(TAG, "Failed to start association.");
        }

        @Override
        public void onAssociationError(int error) {
          associationState.postValue(AssociationState.ERROR);
          loge(TAG, "Error during association: " + error + ".");
        }

        @Override
        public void onVerificationCodeAvailable(String code) {
          advertisedCarName.postValue(null);
          pairingCode.postValue(code);
        }

        @Override
        public void onAssociationCompleted() {
          associationState.postValue(AssociationState.COMPLETED);
        }
      };

  private final IDeviceAssociationCallback deviceAssociationCallback =
      new IDeviceAssociationCallback.Stub() {
        @Override
        public void onAssociatedDeviceAdded(AssociatedDevice device) {
          addOrUpdateAssociatedDevice(device);
        }

        @Override
        public void onAssociatedDeviceRemoved(AssociatedDevice device) {
          removeAssociatedDevice(device);
        }

        @Override
        public void onAssociatedDeviceUpdated(AssociatedDevice device) {
          addOrUpdateAssociatedDevice(device);
        }
      };

  private final IConnectionCallback connectionCallback =
      new IConnectionCallback.Stub() {
        @Override
        public void onDeviceConnected(ConnectedDevice connectedDevice) {
          connectedDevices.add(connectedDevice);
          updateDeviceDetails();
        }

        @Override
        public void onDeviceDisconnected(ConnectedDevice companionDevice) {
          connectedDevices.removeIf(d -> d.getDeviceId().equals(companionDevice.getDeviceId()));
          updateDeviceDetails();
        }
      };

  private final BroadcastReceiver receiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          String action = intent.getAction();
          if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
            return;
          }
          int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
          if (state != BluetoothAdapter.STATE_ON
              && state != BluetoothAdapter.STATE_OFF
              && state != BluetoothAdapter.ERROR) {
            // No need to convey any other state.
            return;
          }
          bluetoothState.postValue(state);
          if (state == BluetoothAdapter.STATE_ON
              && associationState.getValue() == AssociationState.PENDING
              && associatedDeviceManager != null) {
            startAssociation();
          }
        }
      };
}
