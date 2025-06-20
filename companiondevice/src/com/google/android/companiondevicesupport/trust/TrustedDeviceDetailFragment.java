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

package com.google.android.companiondevicesupport.trust;

import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.companiondevicesupport.R;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.trust.TrustedDeviceViewModel;
import com.google.android.connecteddevice.trust.api.TrustedDevice;
import java.util.List;

/** Fragment that shows the details of an trusted device. */
public class TrustedDeviceDetailFragment extends Fragment {
  private static final String TAG = "TrustedDeviceDetailFragment";
  private static final String ASSOCIATED_DEVICE_KEY = "AssociatedDeviceKey";

  private AssociatedDevice associatedDevice;
  private TrustedDeviceViewModel model;
  private TrustedDevice trustedDevice;
  private Switch trustedDeviceSwitch;
  private TextView trustedDeviceTitle;

  static TrustedDeviceDetailFragment newInstance(@NonNull AssociatedDevice device) {
    Bundle bundle = new Bundle();
    bundle.putParcelable(ASSOCIATED_DEVICE_KEY, device);
    TrustedDeviceDetailFragment fragment = new TrustedDeviceDetailFragment();
    fragment.setArguments(bundle);
    return fragment;
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.trusted_device_detail_fragment, container, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    Bundle bundle = getArguments();
    if (bundle == null) {
      loge(TAG, "No valid arguments for TrustedDeviceDetailFragment.");
      return;
    }
    associatedDevice = bundle.getParcelable(ASSOCIATED_DEVICE_KEY);
    trustedDeviceTitle = view.findViewById(R.id.trusted_device_item_title);
    setTrustedDeviceTitle();
    model = new ViewModelProvider(requireActivity()).get(TrustedDeviceViewModel.class);
    trustedDeviceSwitch = view.findViewById(R.id.trusted_device_switch);
    trustedDeviceSwitch.setOnCheckedChangeListener(
        (buttonView, isChecked) -> {
          if (isChecked && trustedDevice == null) {
            // When the current device has not been enrolled as trusted device, turning on the
            // switch is for enrolling the current device.
            model.enrollTrustedDevice(associatedDevice);
          } else if (!isChecked && trustedDevice != null) {
            // When the current device has been enrolled as trusted device, turning off the
            // switch is for disable trusted device feature for the current device.
            model.disableTrustedDevice(trustedDevice);
          }
          // Ignore other conditions as {@link Switch#setChecked(boolean)} will always trigger
          // this listener.
        });
    observeViewModel();
  }

  private void setTrustedDeviceTitle() {
    String deviceName = associatedDevice.getName();
    if (deviceName == null) {
      deviceName = getString(R.string.unknown);
    }
    String deviceTitle = getString(R.string.trusted_device_item_title, deviceName);
    Spanned styledDeviceTitle = Html.fromHtml(deviceTitle, Html.FROM_HTML_MODE_LEGACY);
    trustedDeviceTitle.setText(styledDeviceTitle);
  }

  private void setTrustedDevices(List<TrustedDevice> devices) {
    if (devices == null || devices.isEmpty()) {
      trustedDeviceSwitch.setChecked(false);
      trustedDevice = null;
      return;
    }
    if (devices.size() > 1) {
      loge(TAG, "More than one devices have been associated.");
      return;
    }
    // Currently, we only support single trusted device.
    TrustedDevice device = devices.get(0);
    if (!device.getDeviceId().equals(associatedDevice.getId())) {
      loge(TAG, "Trusted device id doesn't match associated device id.");
      return;
    }
    trustedDevice = device;
    trustedDeviceSwitch.setChecked(true);
  }

  private void observeViewModel() {
    model
        .getAssociatedDevice()
        .observe(
            this,
            device -> {
              if (device == null) {
                return;
              }
              if (device.getId().equals(associatedDevice.getId())) {
                associatedDevice = device;
                setTrustedDeviceTitle();
              }
            });

    model.getTrustedDevices().observe(this, this::setTrustedDevices);
  }
}
