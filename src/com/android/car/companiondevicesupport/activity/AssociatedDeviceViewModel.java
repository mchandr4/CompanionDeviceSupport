/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.companiondevicesupport.activity;

import android.annotation.NonNull;
import android.annotation.Nullable;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.android.car.companiondevicesupport.api.external.AssociatedDevice;
import com.android.car.companiondevicesupport.api.external.CompanionDevice;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation {@link ViewModel} for sharing associated devices data between
 * {@link AssociationFragment} and {@link AssociationActivity}
 */
public class AssociatedDeviceViewModel extends ViewModel {
    private final MutableLiveData<List<AssociatedDevice>> mAssociatedDevices =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<CompanionDevice>> mConnectedDevices =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> mIsSelected = new MutableLiveData<>(false);
    private final MutableLiveData<AssociatedDevice> mDeviceToRemove = new MutableLiveData<>(null);

    /**
     * Set the list of the devices that has been associated with the active user.
     *
     * @param associatedDevices Associated devices.
     */
    public void setAssociatedDevices(@NonNull List<AssociatedDevice> associatedDevices) {
        mAssociatedDevices.postValue(associatedDevices);
    }

    /**
     * Set the list of devices that are currently connected with the active user. The device in this
     * list should also be in {@link #mAssociatedDevices}.
     *
     * @param connectedDevices Connected devices.
     */
    public void setConnectedDevices(@NonNull List<CompanionDevice> connectedDevices) {
        mConnectedDevices.postValue(connectedDevices);
    }

    /**
     * Set the select status of the add associated device button.
     *
     * @param isSelected Select status.
     */
    public void setSelected(boolean isSelected) {
        mIsSelected.setValue(isSelected);
    }

    /**
     * Set the associated device to remove.
     *
     * @param device The device.
     */
    public void setDeviceToRemove(@Nullable AssociatedDevice device) {
        mDeviceToRemove.setValue(device);
    }

    /** The List within the live data can never be null. */
    @NonNull
    public LiveData<List<AssociatedDevice>> getAssociatedDevices() {
        return mAssociatedDevices;
    }

    /** The List within the live data can never be null. */
    @NonNull
    public MutableLiveData<List<CompanionDevice>> getConnectedDevices() {
        return mConnectedDevices;
    }

    public LiveData<Boolean> isSelected() {
        return mIsSelected;
    }

    /** Get the associated device to remove. The associated device could be null. */
    @NonNull
    public LiveData<AssociatedDevice> getDeviceToRemove() {
        return mDeviceToRemove;
    }
}
