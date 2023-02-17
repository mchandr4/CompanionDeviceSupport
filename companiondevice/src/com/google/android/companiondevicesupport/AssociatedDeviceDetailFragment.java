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

import static com.google.android.connecteddevice.util.SafeLog.logd;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.connecteddevice.model.TransportProtocols;
import com.google.android.connecteddevice.trust.TrustedDeviceConstants;
import com.google.android.connecteddevice.ui.AssociatedDeviceDetails;
import com.google.android.connecteddevice.ui.AssociatedDeviceDetails.ConnectionState;
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModel;
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModelFactory;
import java.util.Arrays;
import java.util.List;

/** Fragment that shows the details of an associated device. */
public class AssociatedDeviceDetailFragment extends Fragment {
  private static final String TAG = "AssociatedDeviceDetailFragment";
  private static final String REMOVE_DEVICE_DIALOG_TAG = "RemoveDeviceDialog";
  private static final String ASSOCIATED_DEVICE_DETAILS_KEY = "AssociatedDeviceDetailsKey";

  private AssociatedDeviceDetails deviceDetails;
  private TextView deviceName;
  private TextView connectionStatusText;
  private ImageView connectionStatusIndicator;
  private TextView connectionText;
  private ImageView connectionIcon;
  private LinearLayout connectionButton;
  private AssociatedDeviceViewModel model;
  private TextView claimText;
  private ImageView claimIcon;
  private Context context;

  /**
   * Returns an instance of the {@link AssociatedDeviceDetailFragment} that will display the details
   * of the given {@link AssociatedDeviceDetails}.
   */
  @NonNull
  public static AssociatedDeviceDetailFragment newInstance(AssociatedDeviceDetails deviceDetails) {
    Bundle arguments = new Bundle();
    arguments.putParcelable(ASSOCIATED_DEVICE_DETAILS_KEY, deviceDetails);

    AssociatedDeviceDetailFragment fragment = new AssociatedDeviceDetailFragment();
    fragment.setArguments(arguments);
    return fragment;
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.associated_device_detail_fragment, container, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    context = getContext();
    deviceName = view.findViewById(R.id.device_name);
    connectionIcon = view.findViewById(R.id.connection_button_icon);
    connectionText = view.findViewById(R.id.connection_button_text);
    connectionStatusText = view.findViewById(R.id.connection_status_text);
    connectionStatusIndicator = view.findViewById(R.id.connection_status_indicator);
    connectionButton = view.findViewById(R.id.connection_button);
    claimText = view.findViewById(R.id.claim_button_text);
    claimIcon = view.findViewById(R.id.claim_button_icon);
    List<String> transportProtocols =
        Arrays.asList(getResources().getStringArray(R.array.transport_protocols));
    model =
        new ViewModelProvider(
                requireActivity(),
                new AssociatedDeviceViewModelFactory(
                    requireActivity().getApplication(),
                    transportProtocols.contains(TransportProtocols.PROTOCOL_SPP),
                    getResources().getString(R.string.ble_device_name_prefix),
                    getResources().getBoolean(R.bool.enable_passenger)))
            .get(AssociatedDeviceViewModel.class);
    model.getAssociatedDevicesDetails().observe(this, this::setDeviceDetails);

    connectionButton.setOnClickListener(
        l -> model.toggleConnectionStatusForDevice(deviceDetails.getAssociatedDevice()));
    view.findViewById(R.id.forget_button).setOnClickListener(v -> showRemoveDeviceDialog());
    view.findViewById(R.id.trusted_device_feature_button).setOnClickListener(
        v ->
            model.startFeatureActivityForDevice(
                TrustedDeviceConstants.INTENT_ACTION_TRUSTED_DEVICE_SETTING,
                deviceDetails.getAssociatedDevice()));
    View claimButton = view.findViewById(R.id.claim_button);
    if (getResources().getBoolean(R.bool.enable_passenger)) {
      claimButton.setOnClickListener(v -> toggleClaim());
    } else {
      claimButton.setVisibility(View.GONE);
    }
  }

  @Override
  public void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);

    Bundle arguments = getArguments();
    arguments.setClassLoader(AssociatedDeviceDetails.class.getClassLoader());
    deviceDetails = arguments.getParcelable(ASSOCIATED_DEVICE_DETAILS_KEY);

    if (bundle != null) {
      resumeRemoveDeviceDialog();
    }
  }

  private void setDeviceDetails(List<AssociatedDeviceDetails> associatedDevicesDetails) {
    int index = associatedDevicesDetails.indexOf(deviceDetails);
    if (index == -1) {
      logd(TAG, "Device details updated for non-matching device. Ignoring.");
      return;
    }
    deviceDetails = associatedDevicesDetails.get(index);
    if (context == null) {
      return;
    }
    deviceName.setText(deviceDetails.getDeviceName());

    if (deviceDetails.isConnectionEnabled()) {
      setEnabledConnectionStatus(deviceDetails);
    } else {
      setDisabledConnectionStatus(deviceDetails);
    }

    if (deviceDetails.belongsToDriver()) {
      claimText.setText(getString(R.string.claimed_device));
      claimIcon.setImageDrawable(
          ContextCompat.getDrawable(context, R.drawable.ic_baseline_star_24));
    } else {
      claimText.setText(getString(R.string.unclaimed_device));
      claimIcon.setImageDrawable(
          ContextCompat.getDrawable(context, R.drawable.ic_baseline_star_border_24));
    }
  }

  private void setDisabledConnectionStatus(AssociatedDeviceDetails deviceDetails) {
    if (deviceDetails.getConnectionState() == ConnectionState.CONNECTED) {
      // The connection status will remain connected during the disconnecting process until the
      // device get disconnected, which means when the connection state is
      // {@code ConnectionState.DISCONNECTED}.
      setConnectionStatus(
          ContextCompat.getColor(context, R.color.connection_color_connected),
          getString(R.string.connected),
          ContextCompat.getDrawable(context, R.drawable.ic_phonelink_erase_24dp),
          getString(R.string.disconnecting));
      // Disable the button to avoid user action interrupting the process. The button will be
      // re-enabled when the connection state is updated.
      this.connectionButton.setEnabled(false);
    } else {
      setConnectionStatus(
          ContextCompat.getColor(context, R.color.connection_color_disconnected),
          getString(R.string.disconnected),
          ContextCompat.getDrawable(context, R.drawable.ic_phonelink_ring_24dp),
          getString(R.string.connect));
    }
  }

  private void setEnabledConnectionStatus(AssociatedDeviceDetails deviceDetails) {
    if (deviceDetails.getConnectionState() == ConnectionState.CONNECTED) {
      setConnectionStatus(
          ContextCompat.getColor(context, R.color.connection_color_connected),
          getString(R.string.connected),
          ContextCompat.getDrawable(context, R.drawable.ic_phonelink_erase_24dp),
          getString(R.string.disconnect));
    } else if (deviceDetails.getConnectionState() == ConnectionState.DETECTED) {
      setConnectionStatus(
          ContextCompat.getColor(context, R.color.connection_color_detected),
          getString(R.string.detected),
          ContextCompat.getDrawable(context, R.drawable.ic_phonelink_erase_24dp),
          getString(R.string.disconnect));
    } else {
      setConnectionStatus(
          ContextCompat.getColor(context, R.color.connection_color_not_detected),
          getString(R.string.notDetected),
          ContextCompat.getDrawable(context, R.drawable.ic_phonelink_erase_24dp),
          getString(R.string.disconnect));
    }
  }

  private void setConnectionStatus(
      int connectionStatusColor,
      String connectionStatusText,
      Drawable connectionIcon,
      String connectionText) {
    this.connectionButton.setEnabled(true);
    this.connectionStatusText.setText(connectionStatusText);
    connectionStatusIndicator.setColorFilter(connectionStatusColor);
    this.connectionText.setText(connectionText);
    this.connectionIcon.setImageDrawable(connectionIcon);
  }

  private void showRemoveDeviceDialog() {
    String deviceName = deviceDetails.getDeviceName();
    RemoveDeviceDialogFragment dialogFragment =
        RemoveDeviceDialogFragment.newInstance(
          deviceName, (d, w) -> model.removeDevice(deviceDetails.getAssociatedDevice()));
    dialogFragment.show(getParentFragmentManager(), REMOVE_DEVICE_DIALOG_TAG);
  }

  private void resumeRemoveDeviceDialog() {
    RemoveDeviceDialogFragment removeDeviceDialogFragment =
        (RemoveDeviceDialogFragment)
            getParentFragmentManager().findFragmentByTag(REMOVE_DEVICE_DIALOG_TAG);
    if (removeDeviceDialogFragment != null) {
      removeDeviceDialogFragment.setOnConfirmListener(
        (d, w) -> model.removeDevice(deviceDetails.getAssociatedDevice()));
    }
  }

  private void toggleClaim() {
    if (deviceDetails.belongsToDriver()) {
      model.removeClaimOnDevice(deviceDetails.getAssociatedDevice());
    } else {
      model.claimDevice(deviceDetails.getAssociatedDevice());
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
