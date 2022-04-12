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

package com.google.android.connecteddevice.ui;

import static com.google.android.connecteddevice.api.RemoteFeature.ASSOCIATED_DEVICE_DATA_NAME_EXTRA;
import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.annotation.SuppressLint;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.android.connecteddevice.api.CompanionConnector;
import com.google.android.connecteddevice.api.Connector;
import com.google.android.connecteddevice.api.IAssociationCallback;
import com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.StartAssociationResponse;
import com.google.android.connecteddevice.ui.AssociatedDeviceDetails.ConnectionState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation {@link ViewModel} for sharing associated devices data between the companion
 * platform and UI elements.
 */
public class AssociatedDeviceViewModel extends AndroidViewModel {

  private static final String TAG = "AssociatedDeviceViewModel";

  private static final Duration DISCOVERABLE_DURATION = Duration.ofMinutes(2);

  private static final Duration CONNECT_TIME_OUT_DURATION = Duration.ofSeconds(10);

  /** States of association process. */
  public enum AssociationState {
    NONE,
    PENDING,
    STARTING,
    STARTED,
    COMPLETED,
    ERROR
  }

  private final Lock associatedDevicesLock = new ReentrantLock();

  @GuardedBy("associatedDevicesLock")
  private final List<AssociatedDevice> associatedDevices = new ArrayList<>();

  @GuardedBy("associatedDevicesLock")
  private final MutableLiveData<List<AssociatedDeviceDetails>> associatedDevicesDetails =
      new MutableLiveData<>(new ArrayList<>());

  private final MutableLiveData<String> advertisedCarName = new MutableLiveData<>(null);
  private final MutableLiveData<StartAssociationResponse> associationResponse =
      new MutableLiveData<>(null);
  private final MutableLiveData<String> pairingCode = new MutableLiveData<>(null);
  private final MutableLiveData<Integer> bluetoothState =
      new MutableLiveData<>(BluetoothAdapter.STATE_OFF);
  private final MutableLiveData<AssociationState> associationState =
      new MutableLiveData<>(AssociationState.NONE);
  private final MutableLiveData<AssociatedDevice> removedDevice = new MutableLiveData<>(null);
  private final MutableLiveData<Boolean> isServiceConnected = new MutableLiveData<>(false);
  private final boolean isSppEnabled;
  private final boolean isPassengerEnabled;
  private final String bleDeviceNamePrefix;
  private final BluetoothAdapter bluetoothAdapter;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable connectTimeoutRunnable = this::onConnectTimeout;

  private final Connector connector;

  private ParcelUuid associationIdentifier;

  public AssociatedDeviceViewModel(
      @NonNull Application application,
      boolean isSppEnabled,
      String bleDeviceNamePrefix,
      boolean isPassengerEnabled
  ) {
    this(
        application,
        isSppEnabled,
        bleDeviceNamePrefix,
        isPassengerEnabled,
        new CompanionConnector(
            application, /* isForegroundProcess= */ true, /* userType= */ Connector.USER_TYPE_ALL));
  }

  @VisibleForTesting
  @SuppressLint("UnprotectedReceiver") // Broadcasts are protected.
  AssociatedDeviceViewModel(
      @NonNull Application application,
      boolean isSppEnabled,
      String bleDeviceNamePrefix,
      boolean isPassengerEnabled,
      Connector connector) {
    super(application);
    this.isSppEnabled = isSppEnabled;
    this.bleDeviceNamePrefix = bleDeviceNamePrefix;
    this.connector = connector;
    this.isPassengerEnabled = isPassengerEnabled;

    connector.setCallback(connectorCallback);

    IntentFilter filter = new IntentFilter();
    filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
    getApplication().registerReceiver(receiver, filter);
    bluetoothAdapter =
        application.getApplicationContext().getSystemService(BluetoothManager.class).getAdapter();
    bluetoothState.postValue(BluetoothAdapter.STATE_ON);

    // Registers for device callbacks
    this.connector.setFeatureId(new ParcelUuid(UUID.randomUUID()));

    this.connector.setCallback(connectorCallback);
    connect();
  }

  @Override
  protected void onCleared() {
    super.onCleared();
    connector.setCallback(null);
    connector.disconnect();
    getApplication().unregisterReceiver(receiver);
  }

  /** Confirms that the pairing code matches. */
  public void acceptVerification() {
    pairingCode.postValue(null);
    if (!connector.isConnected()) {
      loge(TAG, "Failed to accept verification, connector is not connected.");
      return;
    }
    connector.acceptVerification();
  }

  /** Stops association. */
  public void stopAssociation() {
    AssociationState state = associationState.getValue();
    if (state != AssociationState.STARTING && state != AssociationState.STARTED) {
      return;
    }
    advertisedCarName.postValue(null);
    pairingCode.postValue(null);
    if (!connector.isConnected()) {
      loge(TAG, "Failed to stop association, connector is not connected.");
      return;
    }
    connector.stopAssociation();
    associationState.postValue(AssociationState.NONE);
  }

  /** Retries association. */
  public void retryAssociation() {
    stopAssociation();
    startAssociationInternal();
  }

  /** Removes the association of the given device. */
  public void removeDevice(@NonNull AssociatedDevice device) {
    if (!connector.isConnected()) {
      loge(
          TAG,
          "Failed to remove device " + device.getDeviceId() + " , connector is not connected.");
      return;
    }
    connector.removeAssociatedDevice(device.getDeviceId());
  }

  /** Toggles connection of the given associated device. */
  public void toggleConnectionStatusForDevice(@NonNull AssociatedDevice device) {
    if (!connector.isConnected()) {
      loge(
          TAG,
          "Failed to change connection on device "
              + device.getDeviceId()
              + " , connector is not connected.");
      return;
    }
    if (device.isConnectionEnabled()) {
      connector.disableAssociatedDeviceConnection(device.getDeviceId());
    } else {
      connector.enableAssociatedDeviceConnection(device.getDeviceId());
    }
  }

  /** Mark the given device as belonging to the active driver. */
  public void claimDevice(@NonNull AssociatedDevice device) {
    if (!connector.isConnected()) {
      loge(
          TAG, "Failed to claim device " + device.getDeviceId() + " , connector is not connected.");
      return;
    }
    connector.claimAssociatedDevice(device.getDeviceId());
  }

  /** Mark the given device as unclaimed by any user. */
  public void removeClaimOnDevice(@NonNull AssociatedDevice device) {
    if (!connector.isConnected()) {
      loge(
          TAG,
          "Failed to remove claim on device "
              + device.getDeviceId()
              + " , connector is not connected.");
      return;
    }
    connector.removeAssociatedDeviceClaim(device.getDeviceId());
  }

  /**
   * Gets a list of details for all associated device.
   *
   * <p>The list will always be non-{@code null}, and will be empty if there are no associated
   * devices.
   */
  public LiveData<List<AssociatedDeviceDetails>> getAssociatedDevicesDetails() {
    associatedDevicesLock.lock();
    try {
      return associatedDevicesDetails;
    } finally {
      associatedDevicesLock.unlock();
    }
  }

  /** Starts feature activity for the given associated device. */
  public void startFeatureActivityForDevice(
      @NonNull String action, @NonNull AssociatedDevice device) {
    Intent intent = new Intent(action);
    intent.putExtra(ASSOCIATED_DEVICE_DATA_NAME_EXTRA, device);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    getApplication().startActivity(intent);
  }

  /** Resets the value of {@link #associationState} and {@link #associationResponse}. */
  public void resetAssociationState() {
    associationState.postValue(AssociationState.NONE);
    associationResponse.postValue(null);
  }

  /** Gets the name that is being advertised by the car. */
  public LiveData<String> getAdvertisedCarName() {
    return advertisedCarName;
  }

  /** Gets the response from a successful request to start association. */
  public LiveData<StartAssociationResponse> getAssociationResponse() {
    return associationResponse;
  }

  /** Gets the generated pairing code. */
  public LiveData<String> getPairingCode() {
    return pairingCode;
  }

  /** Returns the value of a device whose association has been removed. */
  public LiveData<AssociatedDevice> getRemovedDevice() {
    return removedDevice;
  }

  /** Gets the current {@link AssociationState}. */
  public LiveData<AssociationState> getAssociationState() {
    return associationState;
  }

  /** Gets the current Bluetooth state. */
  public LiveData<Integer> getBluetoothState() {
    return bluetoothState;
  }

  /** Value is {@code true} if the service connection is alive. */
  public LiveData<Boolean> isServiceConnected() {
    return isServiceConnected;
  }

  /** Starts adding associated device with the given identifier. */
  public void startAssociation(@NonNull ParcelUuid identifier) {
    associationIdentifier = identifier;
    startAssociationInternal();
  }

  /** Starts adding associated device. */
  public void startAssociation() {
    associationIdentifier = null;
    startAssociationInternal();
  }

  private void startAssociationInternal() {
    associationState.postValue(AssociationState.PENDING);
    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
      return;
    }

    if (isSppEnabled
        && bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
      Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
      discoverableIntent.putExtra(
          BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, (int) DISCOVERABLE_DURATION.getSeconds());
      discoverableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      getApplication().startActivity(discoverableIntent);
    }

    if (!connector.isConnected()) {
      loge(TAG, "Failed to start association, connector is not connected.");
      return;
    }
    if (associationIdentifier != null) {
      connector.startAssociation(associationIdentifier, associationCallback);
    } else {
      connector.startAssociation(associationCallback);
    }

    associationState.postValue(AssociationState.STARTING);
  }

  /**
   * Refreshes the {@link #associatedDevicesDetails} based on the current state of
   * {@link #associatedDevices}.
   */
  private void updateDeviceDetails() {
    associatedDevicesLock.lock();
    try {
      updateDeviceDetailsLocked();
    } finally {
      associatedDevicesLock.unlock();
    }
  }

  /**
   * A version of {@link #updateDeviceDetails()} that should be called if the caller already
   * holds the {@link #associatedDevicesLock}.
   */
  @GuardedBy("associatedDevicesLock")
  private void updateDeviceDetailsLocked() {
    logd(TAG, "Updating device details. Current number of associated devices: "
        + associatedDevices.size());

    List<AssociatedDeviceDetails> associatedDevicesDetails = new ArrayList<>();
    for (AssociatedDevice device : associatedDevices) {
      associatedDevicesDetails.add(new AssociatedDeviceDetails(device, getConnectionState(device)));
    }
    this.associatedDevicesDetails.postValue(associatedDevicesDetails);
  }

  private ConnectionState getConnectionState(@NonNull AssociatedDevice device) {
    logd(TAG, "Getting connection state for device " + device.getDeviceId() + ".");
    ConnectedDevice connectedDevice = connector.getConnectedDeviceById(device.getDeviceId());
    if (connectedDevice == null) {
      logd(TAG, "Device is not detected.");
      return ConnectionState.NOT_DETECTED;
    }
    if (connectedDevice.hasSecureChannel()) {
      logd(TAG, "Device is connected.");
      return ConnectionState.CONNECTED;
    }
    logd(TAG, "Device is detected.");
    return ConnectionState.DETECTED;
  }

  private void setAssociatedDevices(@NonNull List<AssociatedDevice> associatedDevices) {
    associatedDevicesLock.lock();

    try {
      this.associatedDevices.clear();
      this.associatedDevices.addAll(associatedDevices);
      updateDeviceDetailsLocked();
    } finally {
      associatedDevicesLock.unlock();
    }
  }

  private void addOrUpdateAssociatedDevice(@NonNull AssociatedDevice device) {
    associatedDevicesLock.lock();

    try {
      associatedDevices.remove(device);
      associatedDevices.add(device);
      updateDeviceDetailsLocked();
    } finally {
      associatedDevicesLock.unlock();
    }
  }

  private void removeAssociatedDevice(AssociatedDevice device) {
    associatedDevicesLock.lock();

    try {
      if (associatedDevices.remove(device)) {
        logd(TAG, device.getDeviceId() + " removed as an associated device. "
            + "Current number of associated devices: " + associatedDevices.size());

        removedDevice.postValue(device);
        updateDeviceDetailsLocked();
      }
    } finally {
      associatedDevicesLock.unlock();
    }
  }

  private void connect() {
    handler.postDelayed(connectTimeoutRunnable, CONNECT_TIME_OUT_DURATION.toMillis());
    connector.connect();
  }

  private void onConnectTimeout() {
    logd(TAG, "Connector failed to connect in " + CONNECT_TIME_OUT_DURATION);
    connector.disconnect();
  }

  private final Connector.Callback connectorCallback =
      new Connector.Callback() {
        @Override
        public void onConnected() {
          logd(TAG, "Connected to platform.");
          handler.removeCallbacks(connectTimeoutRunnable);
          isServiceConnected.postValue(true);

          if (isPassengerEnabled) {
            connector.retrieveAssociatedDevices(associatedDevicesRetrievedListener);
          } else {
            // If passenger is disabled, then there should only be one device and that will belong
            // to the driver.
            connector.retrieveAssociatedDevicesForDriver(associatedDevicesRetrievedListener);
          }
        }

        @Override
        public void onDisconnected() {
          logd(TAG, "Disconnected from the platform.");
          isServiceConnected.postValue(false);
          connect();
        }

        @Override
        public void onAssociatedDeviceAdded(@NonNull AssociatedDevice device) {
          logd(TAG, "Associated device has been added: " + device.getDeviceId());
          resetAssociationState();
          addOrUpdateAssociatedDevice(device);
        }

        @Override
        public void onAssociatedDeviceRemoved(@NonNull AssociatedDevice device) {
          logd(TAG, "Associated device " + device.getDeviceId() + " was removed");
          removeAssociatedDevice(device);
        }

        @Override
        public void onAssociatedDeviceUpdated(AssociatedDevice device) {
          logd(TAG, "Associated device has been updated: " + device.getDeviceId());
          addOrUpdateAssociatedDevice(device);
        }

        @Override
        public void onDeviceConnected(ConnectedDevice connectedDevice) {
          logd(TAG, "Device " + connectedDevice.getDeviceId() + " has connected.");
          updateDeviceDetails();
        }

        @Override
        public void onDeviceDisconnected(ConnectedDevice connectedDevice) {
          logd(TAG, "Device " + connectedDevice.getDeviceId() + " has disconnected.");
          updateDeviceDetails();
        }

        @Override
        public void onSecureChannelEstablished(@NonNull ConnectedDevice device) {
          logd(TAG, "Device " + device.getDeviceId() + " has established a secure channel.");
          updateDeviceDetails();
        }

        @Override
        public void onFailedToConnect() {
          loge(TAG, "Connector failed to connect.");
        }
      };

  private final IAssociationCallback associationCallback =
      new IAssociationCallback.Stub() {
        @Override
        public void onAssociationStartSuccess(StartAssociationResponse response) {
          associationResponse.postValue(response);
          associationState.postValue(AssociationState.STARTED);
          String deviceName = response.getDeviceName();
          if (!deviceName.isEmpty()) {
            // Name prefix is only needed under BLE mode.
            deviceName = bleDeviceNamePrefix + deviceName;
          }
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
          associationIdentifier = null;
        }
      };

  private final IOnAssociatedDevicesRetrievedListener associatedDevicesRetrievedListener =
      new IOnAssociatedDevicesRetrievedListener.Stub() {
        @Override
        public void onAssociatedDevicesRetrieved(List<AssociatedDevice> devices) {
          setAssociatedDevices(devices);
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
              && connector.isConnected()) {
            startAssociationInternal();
          }
        }
      };
}
