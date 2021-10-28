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
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelUuid;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.google.android.connecteddevice.model.AssociatedDeviceDetails;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.StartAssociationResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Implementation {@link ViewModel} for sharing associated devices data between the companion
 * platform and UI elements.
 */
public class AssociatedDeviceViewModel extends AndroidViewModel {

  private static final String TAG = "AssociatedDeviceViewModel";

  private static final Duration DISCOVERABLE_DURATION = Duration.ofMinutes(2);

  private static final String SCHEME = "https";

  private static final String AUTHORITY = "demo.companiondevice.com";

  private static final int QR_CODE_SIZE_IN_PIXEL = 200;

  /** States of association process. */
  public enum AssociationState {
    NONE,
    PENDING,
    STARTING,
    STARTED,
    COMPLETED,
    ERROR
  }

  private final List<AssociatedDevice> associatedDevices = new CopyOnWriteArrayList<>();
  private final List<ConnectedDevice> connectedDevices = new CopyOnWriteArrayList<>();

  private final MutableLiveData<AssociatedDeviceDetails> currentDeviceDetails =
      new MutableLiveData<>(null);
  private final MutableLiveData<String> advertisedCarName = new MutableLiveData<>(null);
  private final MutableLiveData<Bitmap> bitmap = new MutableLiveData<>(null);
  private final MutableLiveData<String> pairingCode = new MutableLiveData<>(null);
  private final MutableLiveData<Integer> bluetoothState =
      new MutableLiveData<>(BluetoothAdapter.STATE_OFF);
  private final MutableLiveData<AssociationState> associationState =
      new MutableLiveData<>(AssociationState.NONE);
  private final MutableLiveData<AssociatedDevice> removedDevice = new MutableLiveData<>(null);
  private final MutableLiveData<Boolean> isServiceConnected = new MutableLiveData<>(false);
  private final boolean isSppEnabled;
  private final String bleDeviceNamePrefix;
  private final BluetoothAdapter bluetoothAdapter;

  private final Connector connector;

  private ParcelUuid associationIdentifier;

  public AssociatedDeviceViewModel(
      @NonNull Application application, boolean isSppEnabled, String bleDeviceNamePrefix) {
    this(
        application,
        isSppEnabled,
        bleDeviceNamePrefix,
        new CompanionConnector(application, /* isForegroundProcess= */ true));
  }

  @VisibleForTesting
  @SuppressLint("UnprotectedReceiver") // Broadcasts are protected.
  AssociatedDeviceViewModel(
      @NonNull Application application,
      boolean isSppEnabled,
      String bleDeviceNamePrefix,
      Connector connector) {
    super(application);
    this.isSppEnabled = isSppEnabled;
    this.bleDeviceNamePrefix = bleDeviceNamePrefix;
    this.connector = connector;
    connector.setCallback(connectorCallback);
    IntentFilter filter = new IntentFilter();
    filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
    getApplication().registerReceiver(receiver, filter);
    bluetoothAdapter =
        application.getApplicationContext().getSystemService(BluetoothManager.class).getAdapter();
    bluetoothState.postValue(BluetoothAdapter.STATE_ON);
    connector.connect();
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
    connector.stopAssociation();
    associationState.postValue(AssociationState.NONE);
  }

  /** Retries association. */
  public void retryAssociation() {
    stopAssociation();
    startAssociationInternal();
  }

  /** Removes the current associated device. */
  public void removeCurrentDevice() {
    AssociatedDevice device = getAssociatedDevice();
    if (device == null) {
      return;
    }
    connector.removeAssociatedDevice(device.getDeviceId());
  }

  /** Toggles connection on the current associated device. */
  public void toggleConnectionStatusForCurrentDevice() {
    AssociatedDevice device = getAssociatedDevice();
    if (device == null) {
      return;
    }
    if (device.isConnectionEnabled()) {
      connector.disableAssociatedDeviceConnection(device.getDeviceId());
    } else {
      connector.enableAssociatedDeviceConnection(device.getDeviceId());
    }
  }

  /** Gets the associated device details. */
  public LiveData<AssociatedDeviceDetails> getCurrentDeviceDetails() {
    return currentDeviceDetails;
  }

  /** Starts feature activity for the current associated device. */
  public void startFeatureActivityForCurrentDevice(@NonNull String action) {
    AssociatedDevice device = getAssociatedDevice();
    if (device == null || action == null) {
      return;
    }
    Intent intent = new Intent(action);
    intent.putExtra(ASSOCIATED_DEVICE_DATA_NAME_EXTRA, device);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    getApplication().startActivity(intent);
  }

  /** Resets the value of {@link #associationState}. */
  public void resetAssociationState() {
    associationState.postValue(AssociationState.NONE);
  }

  /** Gets the name that is being advertised by the car. */
  public LiveData<String> getAdvertisedCarName() {
    return advertisedCarName;
  }

  /** Gets the Qr code bitmap. */
  public LiveData<Bitmap> getQrCode() {
    return bitmap;
  }

  /** Gets the generated pairing code. */
  public LiveData<String> getPairingCode() {
    return pairingCode;
  }

  /** Value is {@code true} if the current associated device has been removed. */
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

    if (associationIdentifier != null) {
      connector.startAssociation(associationIdentifier, associationCallback);
    } else {
      connector.startAssociation(associationCallback);
    }

    associationState.postValue(AssociationState.STARTING);
  }

  private void updateDeviceDetails() {
    AssociatedDevice device = getAssociatedDevice();
    if (device == null) {
      return;
    }
    currentDeviceDetails.postValue(
        new AssociatedDeviceDetails(getAssociatedDevice(), isConnected()));
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
    this.associatedDevices.clear();
    this.associatedDevices.addAll(associatedDevices);
    updateDeviceDetails();
  }

  private void setConnectedDevices(@NonNull List<ConnectedDevice> connectedDevices) {
    this.connectedDevices.clear();
    this.connectedDevices.addAll(connectedDevices);
    updateDeviceDetails();
  }

  private void addOrUpdateAssociatedDevice(@NonNull AssociatedDevice device) {
    associatedDevices.remove(device);
    associatedDevices.add(device);
    updateDeviceDetails();
  }

  private void removeAssociatedDevice(AssociatedDevice device) {
    if (associatedDevices.remove(device)) {
      removedDevice.postValue(device);
      currentDeviceDetails.postValue(null);
    }
  }

  private final Connector.Callback connectorCallback =
      new Connector.Callback() {
        @Override
        public void onConnected() {
          logd(TAG, "Connected to platform.");
          isServiceConnected.postValue(true);
          setConnectedDevices(connector.getConnectedDevices());
          connector.retrieveAssociatedDevicesForDriver(associatedDevicesRetrievedListener);
        }

        @Override
        public void onDisconnected() {
          logd(TAG, "Disconnected from the platform.");
          isServiceConnected.postValue(false);
          connector.connect();
        }

        @Override
        public void onAssociatedDeviceAdded(@NonNull AssociatedDevice device) {
          addOrUpdateAssociatedDevice(device);
        }

        @Override
        public void onAssociatedDeviceRemoved(@NonNull AssociatedDevice device) {
          removeAssociatedDevice(device);
        }

        @Override
        public void onAssociatedDeviceUpdated(AssociatedDevice device) {
          addOrUpdateAssociatedDevice(device);
        }

        @Override
        public void onDeviceConnected(ConnectedDevice connectedDevice) {
          connectedDevices.add(connectedDevice);
          updateDeviceDetails();
        }

        @Override
        public void onDeviceDisconnected(ConnectedDevice connectedDevice) {
          connectedDevices.remove(connectedDevice);
          updateDeviceDetails();
        }
      };

  private final IAssociationCallback associationCallback =
      new IAssociationCallback.Stub() {
        @Override
        public void onAssociationStartSuccess(StartAssociationResponse response) {
          Uri uri =
              new CompanionUriBuilder()
                  .scheme(SCHEME)
                  .authority(AUTHORITY)
                  .oobData(response.getOobData())
                  .deviceId(response.getDeviceIdentifier())
                  .build();

          Bitmap code = QrCodeGenerator.createQrCode(uri.toString(), QR_CODE_SIZE_IN_PIXEL);
          if (code == null) {
            loge(TAG, "QR code is null, ignore.");
            return;
          }
          bitmap.postValue(code);

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
