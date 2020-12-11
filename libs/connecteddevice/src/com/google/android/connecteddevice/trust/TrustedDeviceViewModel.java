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

import static com.google.android.connecteddevice.util.SafeLog.loge;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceManager;
import com.google.android.connecteddevice.trust.api.TrustedDevice;
import java.util.List;
import java.util.concurrent.Executors;

/** ViewModel that powers the Trusted Device views. */
public class TrustedDeviceViewModel extends ViewModel {
  private static final String TAG = "TrustedDeviceViewModel";

  private final MutableLiveData<List<TrustedDevice>> trustedDevices = new MutableLiveData<>();
  private final MutableLiveData<AssociatedDevice> associatedDevice = new MutableLiveData<>(null);
  private final MutableLiveData<TrustedDevice> deviceToDisable = new MutableLiveData<>(null);
  private final MutableLiveData<AssociatedDevice> deviceToEnable = new MutableLiveData<>(null);
  private final MutableLiveData<TrustedDevice> deviceDisabled = new MutableLiveData<>(null);
  private final MutableLiveData<TrustedDevice> deviceEnabled = new MutableLiveData<>(null);

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
    associatedDevice.setValue(device);
  }

  /**
   * Set the trusted device to disable.
   *
   * @param device The trusted device to disable.
   */
  public void setDeviceToDisable(TrustedDevice device) {
    deviceToDisable.setValue(device);
  }

  /**
   * Set the associated device to enroll.
   *
   * @param device The associated device to enroll.
   */
  public void setDeviceToEnable(AssociatedDevice device) {
    deviceToEnable.setValue(device);
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
  public MutableLiveData<List<TrustedDevice>> getTrustedDevices() {
    return trustedDevices;
  }

  /** Get current associated device. */
  public MutableLiveData<AssociatedDevice> getAssociatedDevice() {
    return associatedDevice;
  }

  /** Get the trusted device to disable. */
  public MutableLiveData<TrustedDevice> getDeviceToDisable() {
    return deviceToDisable;
  }

  /** Get the associated device to enroll. */
  public MutableLiveData<AssociatedDevice> getDeviceToEnable() {
    return deviceToEnable;
  }

  /** Get the disabled trusted device. */
  public MutableLiveData<TrustedDevice> getDisabledDevice() {
    return deviceDisabled;
  }

  /** Get the enabled trusted device. */
  public MutableLiveData<TrustedDevice> getEnabledDevice() {
    return deviceEnabled;
  }

  public void updateTrustedDevices(ITrustedDeviceManager trustedDeviceManager) {
    Executors.newSingleThreadExecutor()
        .execute(
            () -> {
              List<TrustedDevice> trustedDevices;
              try {
                trustedDevices = trustedDeviceManager.getTrustedDevicesForActiveUser();
              } catch (RemoteException e) {
                loge(TAG, "Error while retrieving trusted devices.");
                return;
              }
              setTrustedDevices(trustedDevices);
            });
  }
}
