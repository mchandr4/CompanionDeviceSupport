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

package com.google.android.connecteddevice.trust;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.app.Application;
import android.app.KeyguardManager;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.api.IDeviceAssociationCallback;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.trust.api.IOnTrustedDevicesRetrievedListener;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceManager;
import com.google.android.connecteddevice.trust.api.TrustedDevice;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** ViewModel that powers the Trusted Device views. */
public class TrustedDeviceViewModel extends AndroidViewModel {
  private static final String TAG = "TrustedDeviceViewModel";

  /** State of trusted device enrollment. */
  public enum EnrollmentState {
    NONE,
    WAITING_FOR_PASSWORD_SETUP,
    IN_PROGRESS,
    CREDENTIAL_PENDING,
    FINISHED,
    NO_CONNECTION
  }

  private final AtomicBoolean hasPendingCredential = new AtomicBoolean(false);
  private final AtomicBoolean hasEnrollmentIntent = new AtomicBoolean(false);
  private final MutableLiveData<List<TrustedDevice>> trustedDevices = new MutableLiveData<>();
  private final MutableLiveData<AssociatedDevice> associatedDevice = new MutableLiveData<>(null);
  private final MutableLiveData<TrustedDevice> deviceDisabled = new MutableLiveData<>(null);
  private final MutableLiveData<TrustedDevice> deviceEnabled = new MutableLiveData<>(null);
  private final MutableLiveData<Integer> enrollmentError = new MutableLiveData<>(null);
  private final MutableLiveData<EnrollmentState> enrollmentState =
      new MutableLiveData<>(EnrollmentState.NONE);
  private final MutableLiveData<Boolean> isCurrentAssociatedDeviceRemoved =
      new MutableLiveData<>(false);

  private ITrustedDeviceManager trustedDeviceManager;
  private KeyguardManager keyguardManager;

  public TrustedDeviceViewModel(@NonNull Application application) {
    super(application);
    Intent intent = new Intent(application, TrustedDeviceManagerService.class);
    getApplication().bindService(intent, serviceConnection, /* flags= */ 0);
  }

  @VisibleForTesting
  TrustedDeviceViewModel(
      @NonNull Application application, @NonNull ITrustedDeviceManager trustedDeviceManager) {
    super(application);
    this.trustedDeviceManager = trustedDeviceManager;
    try {
      registerCallbacks();
      trustedDeviceManager.retrieveTrustedDevicesForActiveUser(onTrustedDevicesRetrievedListener);
    } catch (RemoteException e) {
      loge(TAG, "Initial set up failed.", e);
    }
  }

  /**
   * Set trusted devices.
   *
   * @param devices Trusted devices.
   */
  public void setTrustedDevices(@NonNull List<TrustedDevice> devices) {
    trustedDevices.postValue(devices);
  }

  /**
   * Set current associated device.
   *
   * @param device Associated device.
   */
  public void setAssociatedDevice(@NonNull AssociatedDevice device) {
    associatedDevice.postValue(device);
  }

  /** Set the disabled trusted device. */
  public void setDisabledDevice(TrustedDevice device) {
    deviceDisabled.postValue(device);
  }

  /** Set the enabled trusted device. */
  public void setEnabledDevice(TrustedDevice device) {
    deviceEnabled.postValue(device);
  }

  /** Get trusted device list. It will return an empty list if there's no trusted device. */
  public LiveData<List<TrustedDevice>> getTrustedDevices() {
    return trustedDevices;
  }

  /** Get current associated device. */
  public LiveData<AssociatedDevice> getAssociatedDevice() {
    return associatedDevice;
  }

  /** Get the disabled trusted device. */
  public LiveData<TrustedDevice> getDisabledDevice() {
    return deviceDisabled;
  }

  /** Get the enabled trusted device. */
  public LiveData<TrustedDevice> getEnabledDevice() {
    return deviceEnabled;
  }

  /** Get enrollment state. */
  public LiveData<EnrollmentState> getEnrollmentState() {
    return enrollmentState;
  }

  /** Get enrollment error. */
  public LiveData<Integer> getEnrollmentError() {
    return enrollmentError;
  }

  /** Value is {@code true} if IHU has no associated device. */
  public LiveData<Boolean> isCurrentAssociatedDeviceRemoved() {
    return isCurrentAssociatedDeviceRemoved;
  }

  /** Resets enrollment state to {@link EnrollmentState#NONE}. */
  public void resetEnrollmentState() {
    enrollmentState.postValue(EnrollmentState.NONE);
  }

  /** Process trusted device enrollment. */
  public void processEnrollment() {
    hasEnrollmentIntent.set(true);
    processEnrollmentInternal();
  }

  /**
   * Enroll the given associated device as trusted device.
   *
   * @param device The associated device to be enrolled.
   */
  public void enrollTrustedDevice(AssociatedDevice device) {
    attemptInitiatingEnrollment(device);
  }

  /**
   * Disable the given trusted device.
   *
   * @param device The trusted device to disable.
   */
  public void disableTrustedDevice(TrustedDevice device) {
    if (trustedDeviceManager == null) {
      loge(TAG, "Failed to remove trusted device. service not connected.");
      return;
    }
    try {
      trustedDeviceManager.removeTrustedDevice(device);
    } catch (RemoteException e) {
      loge(TAG, "Failed to remove trusted device.", e);
    }
  }

  /** Marks enrollment as finished. */
  public void finishEnrollment() {
    hasPendingCredential.set(false);
    hasEnrollmentIntent.set(false);
    enrollmentState.postValue(EnrollmentState.FINISHED);
  }

  @Override
  protected void onCleared() {
    try {
      unregisterCallbacks();
      hasPendingCredential.set(false);
    } catch (RemoteException e) {
      loge(TAG, "Error clearing registered callbacks.", e);
    }
    getApplication().unbindService(serviceConnection);
    trustedDeviceManager = null;
  }

  private void processEnrollmentInternal() {
    if (!isDeviceSecure()) {
      enrollmentState.postValue(EnrollmentState.WAITING_FOR_PASSWORD_SETUP);
      return;
    }
    if (hasEnrollmentIntent.get() && hasPendingCredential.getAndSet(false)) {
      enrollmentState.postValue(EnrollmentState.CREDENTIAL_PENDING);
      return;
    }
    enrollmentState.postValue(EnrollmentState.IN_PROGRESS);
  }

  private void attemptInitiatingEnrollment(AssociatedDevice device) {
    if (!isCompanionDeviceConnected(device.getDeviceId())) {
      enrollmentState.postValue(EnrollmentState.NO_CONNECTION);
      return;
    }
    try {
      trustedDeviceManager.initiateEnrollment(device.getDeviceId());
    } catch (RemoteException e) {
      loge(TAG, "Failed to initiate enrollment. ", e);
    }
  }

  private boolean isCompanionDeviceConnected(String deviceId) {
    if (trustedDeviceManager == null) {
      loge(
          TAG,
          "Failed to check connection status for device: " + deviceId + ". Service not connected.");
      return false;
    }
    List<ConnectedDevice> devices;
    try {
      devices = trustedDeviceManager.getActiveUserConnectedDevices();
    } catch (RemoteException e) {
      loge(TAG, "Failed to check connection status for device: " + deviceId, e);
      return false;
    }
    if (devices == null || devices.isEmpty()) {
      return false;
    }
    for (ConnectedDevice device : devices) {
      if (device.getDeviceId().equals(deviceId)) {
        return true;
      }
    }
    return false;
  }

  private boolean isDeviceSecure() {
    KeyguardManager keyguardManager = getKeyguardManager();
    if (keyguardManager == null) {
      return false;
    }
    return keyguardManager.isDeviceSecure();
  }

  @Nullable
  private KeyguardManager getKeyguardManager() {
    if (keyguardManager == null) {
      keyguardManager = getApplication().getSystemService(KeyguardManager.class);
    }
    if (keyguardManager == null) {
      loge(TAG, "Unable to get KeyguardManager.");
    }
    return keyguardManager;
  }

  private void registerCallbacks() throws RemoteException {
    if (trustedDeviceManager == null) {
      loge(TAG, "Server not connected when attempting to register callbacks.");
      return;
    }
    trustedDeviceManager.registerTrustedDeviceEnrollmentCallback(trustedDeviceEnrollmentCallback);
    trustedDeviceManager.registerTrustedDeviceCallback(trustedDeviceCallback);
    trustedDeviceManager.registerAssociatedDeviceCallback(deviceAssociationCallback);
  }

  private void unregisterCallbacks() throws RemoteException {
    if (trustedDeviceManager == null) {
      loge(TAG, "Server not connected when attempting to unregister callbacks.");
      return;
    }
    trustedDeviceManager.unregisterTrustedDeviceEnrollmentCallback(trustedDeviceEnrollmentCallback);
    trustedDeviceManager.unregisterTrustedDeviceCallback(trustedDeviceCallback);
    trustedDeviceManager.unregisterAssociatedDeviceCallback(deviceAssociationCallback);
  }

  private final ServiceConnection serviceConnection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
          trustedDeviceManager = ITrustedDeviceManager.Stub.asInterface(service);
          try {
            registerCallbacks();
            trustedDeviceManager.retrieveTrustedDevicesForActiveUser(
                onTrustedDevicesRetrievedListener);
          } catch (RemoteException e) {
            loge(TAG, "Error while connecting to service.");
            return;
          }
          logd(TAG, "Successfully connected to TrustedDeviceManager.");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}
      };

  private final ITrustedDeviceCallback trustedDeviceCallback =
      new ITrustedDeviceCallback.Stub() {
        @Override
        public void onTrustedDeviceAdded(TrustedDevice device) {
          logd(TAG, "Added trusted device: " + device.getDeviceId() + ".");
          setEnabledDevice(device);
          finishEnrollment();
        }

        @Override
        public void onTrustedDeviceRemoved(TrustedDevice device) {
          logd(TAG, "Removed trusted device: " + device.getDeviceId() + ".");
          setDisabledDevice(device);
        }
      };

  private final IDeviceAssociationCallback deviceAssociationCallback =
      new IDeviceAssociationCallback.Stub() {
        @Override
        public void onAssociatedDeviceAdded(AssociatedDevice device) {}

        @Override
        public void onAssociatedDeviceRemoved(AssociatedDevice device) {
          AssociatedDevice currentDevice = getAssociatedDevice().getValue();
          if (device.equals(currentDevice)) {
            isCurrentAssociatedDeviceRemoved.postValue(true);
          }
        }

        @Override
        public void onAssociatedDeviceUpdated(AssociatedDevice device) {
          if (device != null) {
            setAssociatedDevice(device);
          }
        }
      };

  private final ITrustedDeviceEnrollmentCallback trustedDeviceEnrollmentCallback =
      new ITrustedDeviceEnrollmentCallback.Stub() {

        @Override
        public void onValidateCredentialsRequest() {
          hasPendingCredential.set(true);
          processEnrollmentInternal();
        }

        @Override
        public void onTrustedDeviceEnrollmentError(int error) {
          loge(TAG, "Failed to enroll trusted device, encountered error: " + error + ".");
          enrollmentError.postValue(error);
        }
      };

  private final IOnTrustedDevicesRetrievedListener onTrustedDevicesRetrievedListener =
      new IOnTrustedDevicesRetrievedListener.Stub() {

        @Override
        public void onTrustedDevicesRetrieved(List<TrustedDevice> devices) {
          setTrustedDevices(devices);
          logd(TAG, "on trusted devices retrieved.");
        }
      };
}
