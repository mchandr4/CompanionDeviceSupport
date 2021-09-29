/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.LayoutRes;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.connecteddevice.model.TransportProtocols;
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModel;
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModelFactory;
import java.util.Arrays;
import java.util.List;

/** Fragment that provides association instructions. */
public class CompanionLandingFragment extends Fragment {
  private static final String IS_STARTED_FOR_SUW_KEY = "isStartedForSuw";
  private static final String TAG = "CompanionLandingFragment";

  /**
   * Creates a new instance of {@link CompanionLandingFragment}.
   *
   * @param isStartedForSUW If the fragment is created for car setup wizard.
   * @return {@link CompanionLandingFragment} instance.
   */
  static CompanionLandingFragment newInstance(boolean isStartedForSUW) {
    Bundle bundle = new Bundle();
    bundle.putBoolean(IS_STARTED_FOR_SUW_KEY, isStartedForSUW);
    CompanionLandingFragment fragment = new CompanionLandingFragment();
    fragment.setArguments(bundle);
    return fragment;
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    @LayoutRes
    int layout =
        getArguments().getBoolean(IS_STARTED_FOR_SUW_KEY)
            ? R.layout.suw_companion_landing_fragment
            : R.layout.companion_landing_fragment;
    return inflater.inflate(layout, container, false);
  }

  @Override
  public void onViewCreated(View view, Bundle bundle) {
    List<String> transportProtocols =
        Arrays.asList(getResources().getStringArray(R.array.transport_protocols));
    AssociatedDeviceViewModel model =
        new ViewModelProvider(
                requireActivity(),
                new AssociatedDeviceViewModelFactory(
                    requireActivity().getApplication(),
                    transportProtocols.contains(TransportProtocols.PROTOCOL_SPP),
                    getResources().getString(R.string.ble_device_name_prefix)))
            .get(AssociatedDeviceViewModel.class);
    TextView connectToCarTextView = view.findViewById(R.id.connect_to_car_text);
    model
        .getAdvertisedCarName()
        .observe(/* owner= */ this, carName -> setCarName(connectToCarTextView, carName));
    view.findViewById(R.id.add_associated_device_button)
        .setOnClickListener(l -> model.startAssociation());
  }

  private void setCarName(TextView textView, String carName) {
    if (textView == null) {
      loge(TAG, "No valid TextView to show device name.");
      return;
    }
    if (carName == null) {
      return;
    }
    if (!carName.isEmpty()) {
      // Embedded BLE name inside the parenthesis indicating it is just another representatives of
      // the device.
      carName = "(" + carName + ")";
    }
    String bluetoothName = BluetoothAdapter.getDefaultAdapter().getName();
    String connectToCarText =
        getString(R.string.connect_to_targe_car_instruction_text, bluetoothName, carName);
    Spanned styledConnectToCarText = Html.fromHtml(connectToCarText, Html.FROM_HTML_MODE_LEGACY);
    textView.setText(styledConnectToCarText);
  }
}
