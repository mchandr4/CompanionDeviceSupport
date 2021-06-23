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

import android.app.AlertDialog;
import android.app.Dialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.google.android.connecteddevice.model.AssociatedDeviceDetails;
import com.google.android.connecteddevice.trust.TrustedDeviceConstants;
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModel;
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModelFactory;

/** Fragment that shows the details of an associated device. */
public class AssociatedDeviceDetailFragment extends Fragment {
  private static final String TAG = "AssociatedDeviceDetailFragment";
  private static final String REMOVE_DEVICE_DIALOG_TAG = "RemoveDeviceDialog";
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
    connectionIcon = view.findViewById(R.id.connection_button_icon);
    connectionText = view.findViewById(R.id.connection_button_text);
    connectionStatusText = view.findViewById(R.id.connection_status_text);
    connectionStatusIndicator = view.findViewById(R.id.connection_status_indicator);

    model =
        new ViewModelProvider(
                (ViewModelStoreOwner) requireActivity(),
                new AssociatedDeviceViewModelFactory(
                    requireActivity().getApplication(),
                    getResources().getBoolean(R.bool.enable_spp),
                    getResources().getString(R.string.ble_device_name_prefix)))
            .get(AssociatedDeviceViewModel.class);
    model.getCurrentDeviceDetails().observe(this, this::setDeviceDetails);

    view.findViewById(R.id.connection_button)
        .setOnClickListener(
            l -> {
              model.toggleConnectionStatusForCurrentDevice();
            });
    view.findViewById(R.id.forget_button).setOnClickListener(v -> showRemoveDeviceDialog());
    view.findViewById(R.id.trusted_device_feature_button)
        .setOnClickListener(
            v ->
                model.startFeatureActivityForCurrentDevice(
                    TrustedDeviceConstants.INTENT_ACTION_TRUSTED_DEVICE_SETTING));
  }

  @Override
  public void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);
    if (bundle != null) {
      resumeRemoveDeviceDialog();
    }
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
          ContextCompat.getDrawable(getContext(), R.drawable.ic_phonelink_ring_24dp),
          getString(R.string.connect));
    } else if (deviceDetails.isConnected()) {
      setConnectionStatus(
          ContextCompat.getColor(getContext(), R.color.connection_color_connected),
          getString(R.string.connected),
          ContextCompat.getDrawable(getContext(), R.drawable.ic_phonelink_erase_24dp),
          getString(R.string.disconnect));
    } else {
      setConnectionStatus(
          ContextCompat.getColor(getContext(), R.color.connection_color_not_detected),
          getString(R.string.notDetected),
          ContextCompat.getDrawable(getContext(), R.drawable.ic_phonelink_erase_24dp),
          getString(R.string.disconnect));
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

  private void showRemoveDeviceDialog() {
    String deviceName = model.getCurrentDeviceDetails().getValue().getDeviceName();
    RemoveDeviceDialogFragment dialogFragment =
        RemoveDeviceDialogFragment.newInstance(deviceName, (d, w) -> model.removeCurrentDevice());
    dialogFragment.show(getParentFragmentManager(), REMOVE_DEVICE_DIALOG_TAG);
  }

  private void resumeRemoveDeviceDialog() {
    RemoveDeviceDialogFragment removeDeviceDialogFragment =
        (RemoveDeviceDialogFragment)
            getParentFragmentManager().findFragmentByTag(REMOVE_DEVICE_DIALOG_TAG);
    if (removeDeviceDialogFragment != null) {
      removeDeviceDialogFragment.setOnConfirmListener((d, w) -> model.removeCurrentDevice());
    }
  }

  /** Dialog fragment to confirm removing an associated device. */
  public static class RemoveDeviceDialogFragment extends DialogFragment {
    private static final String DEVICE_NAME_KEY = "device_name";

    private DialogInterface.OnClickListener onConfirmListener;

    static RemoveDeviceDialogFragment newInstance(
        @NonNull String deviceName, @NonNull DialogInterface.OnClickListener listener) {
      Bundle bundle = new Bundle();
      bundle.putString(DEVICE_NAME_KEY, deviceName);
      RemoveDeviceDialogFragment fragment = new RemoveDeviceDialogFragment();
      fragment.setArguments(bundle);
      fragment.setOnConfirmListener(listener);
      return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      Bundle bundle = getArguments();
      String deviceName = bundle.getString(DEVICE_NAME_KEY);
      String title = getString(R.string.remove_associated_device_title, deviceName);
      Spanned styledTitle = Html.fromHtml(title, Html.FROM_HTML_MODE_LEGACY);
      return new AlertDialog.Builder(getActivity())
          .setTitle(styledTitle)
          .setMessage(getString(R.string.remove_associated_device_message))
          .setNegativeButton(getString(R.string.cancel), null)
          .setPositiveButton(getString(R.string.forget), onConfirmListener)
          .setCancelable(true)
          .create();
    }

    void setOnConfirmListener(@NonNull DialogInterface.OnClickListener onConfirmListener) {
      this.onConfirmListener = onConfirmListener;
    }
  }
}
