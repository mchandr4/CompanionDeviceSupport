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

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.connecteddevice.model.StartAssociationResponse;
import com.google.android.connecteddevice.model.TransportProtocols;
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModel;
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModelFactory;
import com.google.android.connecteddevice.ui.CompanionUriBuilder;
import com.google.android.connecteddevice.ui.QrCodeGenerator;
import java.util.Arrays;
import java.util.List;

/** Fragment that provides association instructions. */
public class CompanionQrCodeLandingFragment extends Fragment {
  private static final String IS_STARTED_FOR_SUW_KEY = "isStartedForSuw";
  private static final String IS_STARTED_FOR_SETUP_PROFILE_KEY = "isSetupProfile";
  private static final String TAG = "CompanionQrCodeLandingFragment";
  private boolean isStartedForSetupProfile = false;
  @Nullable private View instructionsView = null;
  @Nullable private View addButtonView = null;

  /**
   * Creates a new instance of {@link CompanionQrCodeLandingFragment}.
   *
   * @param isStartedForSUW If the fragment is created for car setup wizard.
   * @param isStartedForSetupProfile If the fragment is created for car setup wizard and car profile
   *     setup. This value will take effect only if the {@code isStartedForSUW} is {@code true}.
   * @return {@link CompanionQrCodeLandingFragment} instance.
   */
  static CompanionQrCodeLandingFragment newInstance(
      boolean isStartedForSUW, boolean isStartedForSetupProfile) {
    Bundle bundle = new Bundle();
    bundle.putBoolean(IS_STARTED_FOR_SUW_KEY, isStartedForSUW);
    bundle.putBoolean(IS_STARTED_FOR_SETUP_PROFILE_KEY, isStartedForSetupProfile);
    CompanionQrCodeLandingFragment fragment = new CompanionQrCodeLandingFragment();
    fragment.setArguments(bundle);
    return fragment;
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    boolean isStartedForSuw = getArguments().getBoolean(IS_STARTED_FOR_SUW_KEY);
    isStartedForSetupProfile = getArguments().getBoolean(IS_STARTED_FOR_SETUP_PROFILE_KEY);
    @LayoutRes int layout;
    if (isStartedForSuw) {
      if (isStartedForSetupProfile) {
        layout = R.layout.suw_companion_setup_profile_fragment;
      } else {
        layout = R.layout.suw_companion_qr_code_landing_fragment;
      }
    } else {
      layout = R.layout.companion_qr_code_landing_fragment;
    }
    return inflater.inflate(layout, container, false);
  }

  @Override
  public void onViewCreated(View view, Bundle bundle) {
    instructionsView = view.findViewById(R.id.association_qr_code_instructions);
    addButtonView = view.findViewById(R.id.add_button_and_divider);
    List<String> transportProtocols =
        Arrays.asList(getResources().getStringArray(R.array.transport_protocols));
    AssociatedDeviceViewModel model =
        new ViewModelProvider(
                requireActivity(),
                new AssociatedDeviceViewModelFactory(
                    requireActivity().getApplication(),
                    transportProtocols.contains(TransportProtocols.PROTOCOL_SPP),
                    getResources().getString(R.string.ble_device_name_prefix),
                    getResources().getBoolean(R.bool.enable_passenger)))
            .get(AssociatedDeviceViewModel.class);

    TextView connectToCarTextView = view.findViewById(R.id.connect_to_car_instruction);
    model
        .getAdvertisedCarName()
        .observe(/* owner= */ this, carName -> setCarName(connectToCarTextView, carName));
    model.getAssociationResponse().observe(/* owner= */ this, this::processAssociationResponse);
    AssociationBaseActivity activity = (AssociationBaseActivity) requireActivity();
    if (isStartedForSetupProfile) {
      // Directly start advertisement in SUW setup profile association flow.
      activity.startAssociation();
      return;
    }
    view.findViewById(R.id.add_associated_device_button)
        .setOnClickListener(l -> activity.startAssociation());
  }

  private void setCarName(TextView textView, String carName) {
    if (textView == null) {
      loge(TAG, "No valid TextView to show device name.");
      return;
    }
    if (carName == null) {
      return;
    }
    int textId =
        isStartedForSetupProfile ? R.string.suw_qr_instruction_text : R.string.qr_instruction_text;
    String connectToCarText = getString(textId, carName);
    Spanned styledConnectToCarText = Html.fromHtml(connectToCarText, Html.FROM_HTML_MODE_LEGACY);
    textView.setText(styledConnectToCarText);
  }

  /**
   * Set the QR code image view when the association response is available. Hide the instruction and
   * add button if they are not null.
   *
   * @param response the association started successfully response. Will be null when association
   *     has not been started.
   */
  private void processAssociationResponse(@Nullable StartAssociationResponse response) {
    if (response == null) {
      logd(
          TAG,
          "Association response is null during QR code generation when association "
              + "started successfully, ignore.");
      return;
    }

    ImageView qrImage = getView().findViewById(R.id.qr_code);
    if (qrImage == null) {
      loge(TAG, "No valid ImageView to show QR code.");
      return;
    }

    Uri uri =
        new CompanionUriBuilder()
            .scheme(getResources().getString(R.string.uri_scheme))
            .authority(getResources().getString(R.string.uri_authority))
            .appendPath(getResources().getString(R.string.uri_path))
            .oobData(response.getOobData())
            .deviceId(response.getDeviceIdentifier())
            .appendQueryParameter(
                IS_STARTED_FOR_SETUP_PROFILE_KEY, String.valueOf(isStartedForSetupProfile))
            .build();
    Bitmap bitmap =
        QrCodeGenerator.createQrCode(
            /* content= */ uri.toString(),
            /* sizeInPixels= */ getResources().getDimensionPixelSize(R.dimen.qr_code_size),
            /* foregroundColor= */ ContextCompat.getColor(
                getActivity(), R.color.settings_color_primary));
    if (bitmap == null) {
      loge(TAG, "QR code could not be generated, ignore.");
      return;
    }

    qrImage.setImageBitmap(bitmap);
    qrImage.setVisibility(View.VISIBLE);
    if (instructionsView != null) {
      instructionsView.setVisibility(View.GONE);
    }
    if (addButtonView != null) {
      addButtonView.setVisibility(View.GONE);
    }
  }
}
