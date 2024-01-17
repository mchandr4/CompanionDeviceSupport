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

import static com.google.android.connecteddevice.trust.TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_NO_CONNECTION;
import static com.google.android.connecteddevice.trust.TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_UNEXPECTED_STATE;
import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.app.Application;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.android.connecteddevice.api.IDeviceAssociationCallback;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.trust.api.IOnTrustedDevicesRetrievedListener;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceManager;
import com.google.android.connecteddevice.trust.api.TrustedDevice;
import java.util.List;

/** ViewModel that powers the Trusted Device views. */
public class TrustedDeviceViewModel extends AndroidViewModel {
  private static final String TAG = "TrustedDeviceViewModel";

  /** State of trusted device enrollment. */
  public enum EnrollmentState {
    NONE,
    WAITING_FOR_PASSWORD_SETUP,
    IN_PROGRESS,
    CREDENTIAL_PENDING,
    FINISHED
  }

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
    registerCallbacks();
    updateTrustedDevicesFromServer();
  }

  private void updateTrustedDevicesFromServer() {
    if (trustedDeviceManager == null) {
      loge(TAG, "Server not connected when attempting to retrieve trusted devices.");
      return;
    }
    IOnTrustedDevicesRetrievedListener onTrustedDevicesRetrievedListener =
        new IOnTrustedDevicesRetrievedListener.Stub() {
          @Override
          public void onTrustedDevicesRetrieved(List<TrustedDevice> devices) {
            trustedDevices.postValue(devices);
            logd(TAG, "on trusted devices retrieved.");
          }
        };
    try {
      trustedDeviceManager.retrieveTrustedDevicesForActiveUser(onTrustedDevicesRetrievedListener);
    } catch (RemoteException e) {
      loge(TAG, "Failed to retrieve trusted devices.", e);
    }
  }

  /**
   * Set trusted devices.
   *
   * @param devices Trusted devices.
   * @deprecated Set trusted device from outside of view model is not supported anymore.
   */
  @Deprecated
  public void setTrustedDevices(@NonNull List<TrustedDevice> devices) {
    trustedDevices.postValue(devices);
  }

  /**
   * Set current associated device.
   *
   * @param device Associated device.
   * @deprecated Set associated device from outside of view model is not supported anymore.
   */
  @Deprecated
  public void setAssociatedDevice(@NonNull AssociatedDevice device) {
    associatedDevice.postValue(device);
  }

  /**
   * Set the disabled trusted device.
   *
   * @deprecated Set disabled trusted device from outside of view model is not supported anymore.
   */
  @Deprecated
  public void setDisabledDevice(TrustedDevice device) {
    deviceDisabled.postValue(device);
  }

  /**
   * Set the enabled trusted device.
   *
   * @deprecated Set enabled trusted device from outside of view model is not supported anymore.
   */
  @Deprecated
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

  /**
   * Get the disabled trusted device
   *
   * @deprecated Ensure to use {@code getTrustedDevices} as the only source of truth to update
   *     trusted device information.
   */
  @Deprecated
  public LiveData<TrustedDevice> getDisabledDevice() {
    return deviceDisabled;
  }

  /**
   * Get the enabled trusted device.
   *
   * @deprecated Ensure to use {@code getTrustedDevices} as the only source of truth to update
   *     trusted device information.
   */
  @Deprecated
  public LiveData<TrustedDevice> getEnabledDevice() {
    return deviceEnabled;
  }

  /** Get enrollment state. */
  public LiveData<EnrollmentState> getEnrollmentState() {
    return enrollmentState;
  }

  /**
   * Get enrollment error. Enrollment error will always result in enrollment aborted and enrollment
   * state reset. Caller only needs to do error handling in UI.
   */
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
    switch (enrollmentState.getValue()) {
      case NONE:
        logd(TAG, "Processing new enrollment.");
        processEnrollmentInternal();
        break;
      case WAITING_FOR_PASSWORD_SETUP:
        if (!isDeviceSecure()) {
          loge(TAG, "Device not secure, failed to process enrollment on secure device.");
          abortEnrollment();
          return;
        }
        logd(TAG, "Continue processing enrollment on secure device.");
        processEnrollmentInternal();
        break;
      default:
        loge(
            TAG,
            "Attempted to process enrollment with unexpected state: "
                + enrollmentState.getValue()
                + ", aborting enrollment.");
        abortEnrollment();
        enrollmentError.postValue(TRUSTED_DEVICE_ERROR_UNEXPECTED_STATE);
    }
  }

  /**
   * Must be called when the user has successfully confirmed their credential via lock screen
   * launched by [createConfirmDeviceCredentialIntent]. Otherwise, the enrollment will not proceed.
   */
  public void onCredentialVerified() {
    if (trustedDeviceManager == null) {
      loge(
          TAG,
          "Failed to send credential verification confirmation to TrustedDeviceManager. "
              + "Service not connected.");
      return;
    }
    try {
      trustedDeviceManager.onCredentialVerified();
    } catch (RemoteException e) {
      loge(TAG, "Failed to confirm credential verification.", e);
    }
  }

  /** Aborts enrollment. */
  public void abortEnrollment() {
    if (trustedDeviceManager == null) {
      loge(TAG, "Failed to abort enrollment. service not connected.");
      return;
    }
    try {
      trustedDeviceManager.abortEnrollment();
    } catch (RemoteException e) {
      loge(TAG, "Failed to abort enrollment.", e);
    }
    updateTrustedDevicesFromServer();
    resetEnrollmentState();
  }

  /**
   * Enroll the given associated device as trusted device.
   *
   * @param device The associated device to be enrolled.
   */
  public void enrollTrustedDevice(AssociatedDevice device) {
    updateTrustedDevicesFromServer();
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
    enrollmentState.postValue(EnrollmentState.FINISHED);
  }

  @Override
  protected void onCleared() {
    unregisterCallbacks();
    getApplication().unbindService(serviceConnection);
    trustedDeviceManager = null;
  }

  private void processEnrollmentInternal() {
    if (trustedDeviceManager == null) {
      loge(TAG, "Failed to process enrollment. TrustedDeviceManager not connected.");
      return;
    }
    enrollmentState.postValue(EnrollmentState.IN_PROGRESS);
    try {
      trustedDeviceManager.processEnrollment(isDeviceSecure());
    } catch (RemoteException e) {
      loge(TAG, "Failed to process enrollment. ", e);
    }
  }

  private void attemptInitiatingEnrollment(AssociatedDevice device) {
    if (!isCompanionDeviceConnected(device.getDeviceId())) {
      enrollmentError.postValue(TRUSTED_DEVICE_ERROR_NO_CONNECTION);
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
    KeyguardManager keyguardManager = getApplication().getSystemService(KeyguardManager.class);
    if (keyguardManager == null) {
      loge(TAG, "Unable to get KeyguardManager.");
      return false;
    }
    return keyguardManager.isDeviceSecure();
  }

  private void registerCallbacks() {
    if (trustedDeviceManager == null) {
      loge(TAG, "Server not connected when attempting to register callbacks.");
      return;
    }
    try {
      trustedDeviceManager.registerTrustedDeviceEnrollmentCallback(trustedDeviceEnrollmentCallback);
      trustedDeviceManager.registerTrustedDeviceCallback(trustedDeviceCallback);
      trustedDeviceManager.registerAssociatedDeviceCallback(deviceAssociationCallback);
    } catch (RemoteException e) {
      loge(TAG, "Error registering callbacks.", e);
    }
  }

  private void unregisterCallbacks() {
    if (trustedDeviceManager == null) {
      loge(TAG, "Server not connected when attempting to unregister callbacks.");
      return;
    }
    try {
      trustedDeviceManager.unregisterTrustedDeviceEnrollmentCallback(
          trustedDeviceEnrollmentCallback);
      trustedDeviceManager.unregisterTrustedDeviceCallback(trustedDeviceCallback);
      trustedDeviceManager.unregisterAssociatedDeviceCallback(deviceAssociationCallback);
    } catch (RemoteException e) {
      loge(TAG, "Error unregistering callbacks.", e);
    }
  }

  private final ServiceConnection serviceConnection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
          logd(TAG, "Successfully connected to TrustedDeviceManager.");
          trustedDeviceManager = ITrustedDeviceManager.Stub.asInterface(service);
          registerCallbacks();
          updateTrustedDevicesFromServer();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
          resetEnrollmentState();
        }
      };

  private final ITrustedDeviceCallback trustedDeviceCallback =
      new ITrustedDeviceCallback.Stub() {
        @Override
        public void onTrustedDeviceAdded(TrustedDevice device) {
          logd(TAG, "Added trusted device: " + device.getDeviceId() + ", update UI elements.");
          updateTrustedDevicesFromServer();
          finishEnrollment();
        }

        @Override
        public void onTrustedDeviceRemoved(TrustedDevice device) {
          logd(TAG, "Removed trusted device: " + device.getDeviceId() + ", update UI elements.");
          updateTrustedDevicesFromServer();
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

        // Escrow token is the first message of enrollment. Resetting the state so the manager
        // always accepts new enrollment.
        @Override
        public void onEscrowTokenReceived() {
          resetEnrollmentState();
        }

        @Override
        public void onValidateCredentialsRequest() {
          enrollmentState.postValue(EnrollmentState.CREDENTIAL_PENDING);
        }

        @Override
        public void onTrustedDeviceEnrollmentError(int error) {
          loge(TAG, "Failed to enroll trusted device, encountered error: " + error + ".");
          abortEnrollment();
          enrollmentError.postValue(error);
        }

        @Override
        public void onSecureDeviceRequest() {
          enrollmentState.postValue(EnrollmentState.WAITING_FOR_PASSWORD_SETUP);
        }
      };
}
