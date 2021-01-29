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

package com.google.android.companiondevicesupport;

import androidx.lifecycle.ViewModelProviders;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.connecteddevice.trust.TrustedDeviceConstants;

/** Fragment that shows the details of an associated device. */
public class AssociatedDeviceDetailFragment extends Fragment {
  private static final String TAG = "AssociatedDeviceDetailFragment";
  private TextView deviceName;
  private TextView connectionStatusText;
  private ImageView connectionStatusIndicator;
  private TextView connectionText;
  private ImageView connectionIcon;
  private AssociatedDeviceViewModel model;

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.associated_device_detail_fragment, container, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    deviceName = view.findViewById(R.id.device_name);
    connectionIcon = view.findViewById(R.id.connection_icon);
    connectionText = view.findViewById(R.id.connection_text);
    connectionStatusText = view.findViewById(R.id.connection_status_text);
    connectionStatusIndicator = view.findViewById(R.id.connection_status_indicator);

    model = ViewModelProviders.of(getActivity()).get(AssociatedDeviceViewModel.class);
    model.getDeviceDetails().observe(this, this::setDeviceDetails);

    view.findViewById(R.id.connection_button)
        .setOnClickListener(
            l -> {
              model.toggleConnectionStatusForCurrentDevice();
            });
    view.findViewById(R.id.remove_button)
        .setOnClickListener(v -> model.selectCurrentDeviceToRemove());
    view.findViewById(R.id.trusted_device_feature_button)
        .setOnClickListener(
            v ->
                model.startFeatureActivityForCurrentDevice(
                    TrustedDeviceConstants.INTENT_ACTION_TRUSTED_DEVICE_SETTING));
  }

  private void setDeviceDetails(AssociatedDeviceDetails deviceDetails) {
    if (deviceDetails == null) {
      return;
    }
    deviceName.setText(deviceDetails.getDeviceName());

    if (!deviceDetails.isConnectionEnabled()) {
      setConnectionStatus(
          ContextCompat.getColor(getContext(), R.color.connection_color_disconnected),
          getString(R.string.disconnected),
          ContextCompat.getDrawable(getContext(), R.drawable.ic_phonelink_ring),
          getString(R.string.enable_device_connection_text));
    } else if (deviceDetails.isConnected()) {
      setConnectionStatus(
          ContextCompat.getColor(getContext(), R.color.connection_color_connected),
          getString(R.string.connected),
          ContextCompat.getDrawable(getContext(), R.drawable.ic_phonelink_erase),
          getString(R.string.disable_device_connection_text));
    } else {
      setConnectionStatus(
          ContextCompat.getColor(getContext(), R.color.connection_color_not_detected),
          getString(R.string.notDetected),
          ContextCompat.getDrawable(getContext(), R.drawable.ic_phonelink_erase),
          getString(R.string.disable_device_connection_text));
    }
  }

  private void setConnectionStatus(
      int connectionStatusColor,
      String connectionStatusText,
      Drawable connectionIcon,
      String connectionText) {
    this.connectionStatusText.setText(connectionStatusText);
    connectionStatusIndicator.setColorFilter(connectionStatusColor);
    this.connectionText.setText(connectionText);
    this.connectionIcon.setImageDrawable(connectionIcon);
  }
}
